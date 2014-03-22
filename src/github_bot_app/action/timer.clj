(ns github-bot-app.action.timer
  (:require [measure.core :refer [timer with-timer]])
  (:import com.codahale.metrics.SharedMetricRegistries))


(def github-api-timer
  (timer (SharedMetricRegistries/getOrCreate "github-bot-app")
         "github-bot-app.github-api.call-time"))


(defmacro github-api-time! [& body]
  `(with-timer github-api-timer ~@body))
