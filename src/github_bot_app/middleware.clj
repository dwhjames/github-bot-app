(ns github-bot-app.middleware
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ring.util.response :refer [response status]]
            [ring.util.request :refer [content-length]]))


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
