(ns github-bot-app.handler
  (:require [clojure.edn :as edn]
            [clojure.java.io :as jio]
            [clojure.tools.logging :as log]
            [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [metrics.jvm.core :as mjc]
            [metrics.ring.instrument :as mri]
            [metrics.ring.expose :as mre]
            [github-bot-app.http :as hh]
            [github-bot-app.metrics :as metrics]
            [github-bot-app.pools :as pools]
            [github-bot-app.webhook :as webhk]
            [github-bot-app.middleware :refer [wrap-request-logger
                                               wrap-exception-handler]]))


(defn init []
  (log/info {:phase :init-begin})
  (mjc/instrument-jvm (metrics/lookup-registry))
  (def config
    (with-open [r (java.io.PushbackReader.
                   (jio/reader (jio/resource "config.edn")))]
      (edn/read r)))
  (log/info {:phase :init-end}))


(defn destroy []
  (log/info {:phase :destroy-begin})
  (metrics/remove-registry)
  (.shutdown pools/scheduled-pool)
  (.shutdown pools/dispatch-pool)
  (log/info {:phase :destroy-end}))


(defroutes app-routes
  (OPTIONS "/" []
    (hh/options [:options :head :get]))
  (HEAD "/" []
     "")
  (GET "/" []
     "<h1>GitHub-Bot-App</h1>")
  (ANY "/" []
    (hh/method-not-allowed [:options :head]))
  (GET "/metrics" request
    (mre/serve-metrics request (metrics/lookup-registry) {:pretty-print? true}))
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
      wrap-request-logger
      wrap-exception-handler
      (mri/instrument (metrics/lookup-registry))))


