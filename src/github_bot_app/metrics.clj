(ns github-bot-app.metrics
  (:require [cheshire.core :as json]
            [measure.core :as measure]
            [ring.util.response :refer [content-type response]])
  (:import [java.util.concurrent TimeUnit]
           [com.codahale.metrics MetricRegistry SharedMetricRegistries]
           [com.codahale.metrics.jvm GarbageCollectorMetricSet MemoryUsageGaugeSet ThreadStatesGaugeSet]))


(defn register-jvm-metrics []
  (doto (SharedMetricRegistries/getOrCreate "github-bot-app")
    (.registerAll (GarbageCollectorMetricSet.))
    (.registerAll (MemoryUsageGaugeSet.))))


(defn remove-metrics []
  (SharedMetricRegistries/remove "github-bot-app"))


(defn metrics-map []
  (let [registry (SharedMetricRegistries/getOrCreate "github-bot-app")]
    (-> (sorted-map)
        (into (let [gauges (into {} (.getGauges registry))]
                (zipmap (keys gauges)
                        (map (fn [g]
                               {:type "gauge"
                                :value (measure/value g)})
                             (vals gauges)))))
        (into (let [counters (into {} (.getCounters registry))]
                (zipmap (keys counters)
                        (map (fn [c]
                               {:type "counter"
                                :value (measure/value c)})
                             (vals counters)))))
        (into (let [histograms (into {} (.getHistograms registry))]
                (zipmap (keys histograms)
                        (map (fn [h]
                               (into (sorted-map :type "histogram"
                                                 :count (measure/value h))
                                     (measure/snapshot h)))
                             (vals histograms)))))
        (into (let [meters (into {} (.getMeters registry))]
                (zipmap (keys meters)
                        (map (fn [m]
                               (into (sorted-map :type "meter")
                                     (measure/rates m TimeUnit/SECONDS)))
                             (vals meters)))))
        (into (let [timers (into {} (.getTimers registry))]
                (zipmap (keys timers)
                        (map (fn [t]
                               (-> (sorted-map :type "timer"
                                               :count (measure/value t))
                                   (into (measure/rates t TimeUnit/SECONDS))
                                   (into (measure/snapshot t TimeUnit/MILLISECONDS))))
                             (vals timers))))))))


(defn handle-metrics []
  (-> (metrics-map)
      json/generate-string
      response
      (content-type "application/json")))

