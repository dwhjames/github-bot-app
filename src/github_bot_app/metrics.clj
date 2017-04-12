(ns github-bot-app.metrics
  (:import [com.codahale.metrics SharedMetricRegistries]))

(defn lookup-registry []
  (SharedMetricRegistries/getOrCreate "github-bot-app"))

(defn remove-registry []
  (SharedMetricRegistries/remove "github-bot-app"))
