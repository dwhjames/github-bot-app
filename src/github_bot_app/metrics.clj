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
    (-> {}
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
                               (into {:type "histogram"
                                      :count (measure/value h)}
                                     (measure/snapshot h)))
                             (vals histograms)))))
        (into (let [meters (into {} (.getMeters registry))]
                (zipmap (keys meters)
                        (map (fn [m]
                               (into {:type "meter"
                                      :units "events / second"}
                                     (measure/rates m)))
                             (vals meters)))))
        (into (let [timers (into {} (.getTimers registry))
                    factor (/ 1.0 (.toNanos (TimeUnit/MILLISECONDS) 1))]
                (zipmap (keys timers)
                        (map (fn [t]
                               (let [s (measure/snapshot t)]
                                 (into {:type "timer"
                                        :rate-units "calls / second"
                                        :duration-units "milliseconds"
                                        :count (measure/value t)
                                        :min (* factor (:min s))
                                        :max (* factor (:max s))
                                        :mean (* factor (:mean s))
                                        :std-dev (* factor (:std-dev s))
                                        :median (* factor (:median s))
                                        :75th-percentile (* factor (:75th-percentile s))
                                        :95th-percentile (* factor (:95th-percentile s))
                                        :98th-percentile (* factor (:98th-percentile s))
                                        :99th-percentile (* factor (:99th-percentile s))
                                        :999th-percentile (* factor (:999th-percentile s))}
                                       (measure/rates t))))
                             (vals timers))))))))


(defn handle-metrics []
  (-> (metrics-map)
      json/generate-string
      response
      (content-type "application/json")))

