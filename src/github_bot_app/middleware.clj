(ns github-bot-app.middleware
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ring.util.response :refer [response status]]
            [ring.util.request :refer [content-length]]
            [measure.core :as m])
  (:import com.codahale.metrics.SharedMetricRegistries))


(defn wrap-instrumentation
  "Instrument a ring handler."
  [handler]
  (let [registry (SharedMetricRegistries/getOrCreate "github-bot-app")
        active-request-counter (m/counter registry "ring.requests.active")
        request-meter (m/meter registry "ring.requests.rate")
        request-size-hist (m/histogram registry "ring.requests.size")
        request-method-timers {:get     (m/timer registry "ring.handling-time.GET")
                               :put     (m/timer registry "ring.handling-time.PUT")
                               :post    (m/timer registry "ring.handling-time.POST")
                               :head    (m/timer registry "ring.handling-time.HEAD")
                               :delete  (m/timer registry "ring.handling-time.DELETE")
                               :options (m/timer registry "ring.handling-time.OPTIONS")
                               :trace   (m/timer registry "ring.handling-time.TRACE")
                               :other   (m/timer registry "ring.handling-time.OTHER")}
        response-status-meters {1 (m/meter registry "ring.responses.rate.1xx")
                                2 (m/meter registry "ring.responses.rate.2xx")
                                3 (m/meter registry "ring.responses.rate.3xx")
                                4 (m/meter registry "ring.responses.rate.4xx")
                                5 (m/meter registry "ring.responses.rate.5xx")}]
    (fn [request]
      (m/increment active-request-counter)
      (try
        (let [request-method (:request-method request)]
          (m/mark request-meter)
          (when-let [n (content-length request)]
            (m/update request-size-hist n))
          (let [t (get request-method-timers
                              request-method
                              (:other request-method-timers))
                resp (m/time! t #(handler request))
                status-code (:status resp)]
            (m/mark (get response-status-meters (quot status-code 100)))
            resp))
        (finally (m/decrement active-request-counter))))))


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
        (log/error e (pr-str {:exception (str e) :request req}))
        (->
          (response (str e))
          (status 500))))))
