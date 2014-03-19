(ns github-bot-app.action.timer
  (:require [metrics.timers :as timers]))


(def github-api-timer
  (timers/timer ["github-bot-app" "github-api" "call-time"]))


(defmacro github-api-time! [& body]
  `(timers/time! github-api-timer ~@body))
