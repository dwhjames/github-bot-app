(ns github-bot-app.action.timer
  (:require [measure.core :refer [timer]])
  (:import [com.codahale.metrics
            SharedMetricRegistries
            Timer]))


(def ^Timer github-api-timer
  (timer (SharedMetricRegistries/getOrCreate "github-bot-app")
         "github-bot-app.github-api.call-time"))


(defmacro github-api-time! [& body]
  `(with-open [t# (.time github-api-timer)]
     ~@body))
