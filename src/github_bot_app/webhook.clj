(ns github-bot-app.webhook
  (:require [clojure.java.io :as jio]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [metrics.histograms :as hist]
            [ring.util.response :refer [response status]]
            [ring.util.request :refer [content-type content-length]]
            [github-bot-app.metrics :as metrics]
            [github-bot-app.pools :as pools]
            [github-bot-app.action.auto-label :as auto-label]
            [github-bot-app.action.composite-pr :as composite-pr]
            [github-bot-app.action.hey-ping :as hey-ping]
            [github-bot-app.action.merge-conflict :as merge-conflict]
            [github-bot-app.action.pr-flowdock-chat :as pr-flowdock-chat]
            [github-bot-app.action.pr-police :as pr-police])
  (:import com.fasterxml.jackson.core.JsonParseException
           com.codahale.metrics.SharedMetricRegistries))


(defn- is-json-content? [type]
  (not (empty? (re-find #"^application/(.+\+)?json" type))))


(defn- read-json [request]
  (if-let [body (:body request)]
    (try
      (with-open [r (jio/reader body)]
        (json/parse-stream r true))
      (catch JsonParseException e
        (log/warn e)))))


(defn- error-resp [code body]
  (-> (response body)
      (content-type "text/plain")
      (status code)))


(defn- run-ping-action [event payload config]
  (when (= event "ping")
    (log/info (pr-str
               {:event :ping
                :hook-id (:hook_id payload)}))))


(def payload-actions
  [["ping"             run-ping-action]
   ["auto-label"       auto-label/run-action]
   ["composite-pr"     composite-pr/run-action]
   ["hey-ping"         hey-ping/run-action]
   ["merge-conflict"   merge-conflict/run-action]
   ["pr-flowdock-chat" pr-flowdock-chat/run-action]
   ["pr-police"        pr-police/run-action]])


(defn- dispatch-payload-actions
  [event payload config]
  (doseq [[label act] payload-actions]
    (pools/dispatch (str "payload action " label) #(act event payload config))))


(def payload-size-histogram
  (hist/histogram (metrics/lookup-registry)
                  "github-bot-app.webhook.payload-size"))


(defn handle-webhook [req config]
  (if-let [event (get-in req [:headers "x-github-event"])]
    (if-let [type (content-type req)]
      (if (is-json-content? type)
        (if-let [payload (read-json req)]
          (do
            (log/info (pr-str
                       {:event event
                        :delivery (-> req
                                      (get-in [:headers "x-github-delivery"])
                                      java.util.UUID/fromString)}))
            (hist/update! payload-size-histogram (content-length req))
            (dispatch-payload-actions event payload config)
            (-> (response nil)
                (status 204)))
          (error-resp 400 "Bad Json"))
        (error-resp 415 "Expected Json Content-Type"))
      (error-resp 415 "Missing Content-Type header"))
    (error-resp 400 "Missing X-GitHub-Event header")))
