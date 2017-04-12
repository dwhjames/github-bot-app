(ns github-bot-app.action.timer
  (:require [metrics.timers :refer [timer]]
            [github-bot-app.metrics :as metrics])
  (:import [com.codahale.metrics
            SharedMetricRegistries
            Timer]))


(def ^Timer github-api-timer
  (timer (metrics/lookup-registry)
         "github-bot-app.github-api.call-time"))


(defmacro github-api-time! [& body]
  `(with-open [t# (.time github-api-timer)]
     ~@body))
