(ns github-bot-app.handler
  (:require [clojure.edn :as edn]
            [clojure.java.io :as jio]
            [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [metrics.ring.expose :refer [expose-metrics-as-json]]
            [metrics.ring.instrument :refer (instrument)]
            [github-bot-app.http :as hh]
            [github-bot-app.webhook :as webhk]
            [github-bot-app.middleware :refer [wrap-request-logger
                                               wrap-exception-handler]]))


(def config
  (with-open [r (java.io.PushbackReader.
                 (jio/reader (jio/resource "config.edn")))]
    (edn/read r)))


(defroutes app-routes
  (OPTIONS "/" []
    (hh/options [:options :head :get]))
  (HEAD "/" []
     "")
  (GET "/" []
     "<h1>GitHub-Bot-App</h1>")
  (ANY "/" []
    (hh/method-not-allowed [:options :head]))
  (context "/webhook" []
    (POST "/" [:as req]
      (webhk/handle-webhook req config))
    (OPTIONS "/" []
      (hh/options [:options :post]))
    (ANY "/" []
      (hh/method-not-allowed [:options :post])))
  (route/not-found "Not Found"))


(def app
  (-> (handler/api app-routes)
      (wrap-request-logger)
      (wrap-exception-handler)
      (instrument)
      (expose-metrics-as-json)))
