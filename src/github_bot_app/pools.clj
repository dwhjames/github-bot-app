(ns github-bot-app.pools
  (:require [clojure.tools.logging :as log])
  (:import [java.util.concurrent Executors ExecutorService
                                 ScheduledExecutorService ThreadFactory
                                 TimeUnit]
           java.util.concurrent.atomic.AtomicInteger
           [com.codahale.metrics
            InstrumentedExecutorService
            InstrumentedScheduledExecutorService
            InstrumentedThreadFactory
            SharedMetricRegistries]))


(defn- create-thread-factory
  [^String str ^AtomicInteger counter & [is-daemon]]
  (let [thread-factory (Executors/defaultThreadFactory)]
    (reify ThreadFactory
      (^Thread newThread [this ^Runnable r]
        (doto (.newThread thread-factory r)
          (.setName (format str (.getAndIncrement counter)))
          (.setDaemon (or is-daemon false)))))))


(defn- ^Runnable wrap-runnable
  [^Runnable r]
  (reify Runnable
    (run [this]
      (try
        (.run r)
        (catch Exception ex
          (log/error ex "Uncaught exception in pool!")
          (throw ex))))))


(def dispatch-pool-thread-format
  "github-bot-app-dispatch-pool-%d")


(def ^AtomicInteger dispatch-pool-thread-counter
  (AtomicInteger. 0))


(def ^ExecutorService dispatch-pool
  (let [registry (SharedMetricRegistries/getOrCreate "github-bot-app")]
    (InstrumentedExecutorService.
     (Executors/newCachedThreadPool
     (InstrumentedThreadFactory.
      (create-thread-factory dispatch-pool-thread-format
                             dispatch-pool-thread-counter)
      registry
      "github-bot-app-dispatch-threads"))
     registry
     "github-bot-app-dispatch-pool")))


(defn dispatch
  [^Runnable r]
  (.execute dispatch-pool (wrap-runnable r)))


(def scheduled-pool-thread-format
  "github-bot-app-scheduled-pool-%d")


(def ^AtomicInteger scheduled-pool-thread-counter
  (AtomicInteger. 0))


(def ^ScheduledExecutorService scheduled-pool
  (let [registry (SharedMetricRegistries/getOrCreate "github-bot-app")]
    (InstrumentedScheduledExecutorService.
     (Executors/newSingleThreadScheduledExecutor
      (InstrumentedThreadFactory.
       (create-thread-factory scheduled-pool-thread-format
                              scheduled-pool-thread-counter
                              true)
       registry
       "github-bot-app-scheduled-threads"))
     registry
     "github-bot-app-scheduled-pool")))

(defn schedule
  [^Long delay ^TimeUnit time-unit ^Runnable r]
  (.schedule scheduled-pool
             (wrap-runnable r)
             (long delay) time-unit))
