(ns github-bot-app.middleware
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ring.util.response :refer :all]
            [measure.core :refer [counter increment decrement
                                  meter mark
                                  timer time!]])
  (:import com.codahale.metrics.SharedMetricRegistries))


(defn wrap-instrumentation
  "Instrument a ring handler."
  [handler]
  (let [registry (SharedMetricRegistries/getOrCreate "github-bot-app")
        active-request-counter (counter registry "ring.requests.active")
        request-meter (meter registry "ring.requests.rate")
        request-method-timers {:get     (timer registry "ring.handling-time.GET")
                               :put     (timer registry "ring.handling-time.PUT")
                               :post    (timer registry "ring.handling-time.POST")
                               :head    (timer registry "ring.handling-time.HEAD")
                               :delete  (timer registry "ring.handling-time.DELETE")
                               :options (timer registry "ring.handling-time.OPTIONS")
                               :trace   (timer registry "ring.handling-time.TRACE")
                               :other   (timer registry "ring.handling-time.OTHER")}
        response-status-meters {1 (meter registry "ring.responses.rate.1xx")
                                2 (meter registry "ring.responses.rate.2xx")
                                3 (meter registry "ring.responses.rate.3xx")
                                4 (meter registry "ring.responses.rate.4xx")
                                5 (meter registry "ring.responses.rate.5xx")}]
    (fn [request]
      (increment active-request-counter)
      (try
        (let [request-method (:request-method request)]
          (mark request-meter)
          (let [t (get request-method-timers
                              request-method
                              (:other request-method-timers))
                resp (time! t #(handler request))
                status-code (:status resp)]
            (mark (get response-status-meters (quot status-code 100)))
            resp))
        (finally (decrement active-request-counter))))))


(defn wrap-request-logger
  "Ring middleware function that uses clojure.tools.logging to write info message
  containing remote address, request method & URI of incoming request"
  [handler]
  (fn [req]
    (log/info (pr-str (select-keys req [:remote-addr :request-method :uri])))
    (handler req)))

(defn wrap-exception-handler
  "Ring middleware function to trap any uncaught exceptions and return an appropriate
  status code with the exception instance as the response body"
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch Exception e
        (->
          (response (str e))
          (status 500))))))
