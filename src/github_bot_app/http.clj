(ns github-bot-app.http
  (:require [clojure.string :as str]
            [ring.util.response :refer :all]))


;; Copied from https://github.com/pumbers/doitnow


(defn options
  "Generate a 200 HTTP response with an Allow header containing the provided
  HTTP method names - response for an HTTP OPTIONS request"
  ([] (options #{:options} nil))
  ([allowed] (options allowed nil))
  ([allowed body]
    (->
      (response body)
      (header "Allow"
              (str/join ", " (map (comp str/upper-case name) allowed))))))

(defn method-not-allowed
  "Generate a 405 response with an Allow header containing the provided HTTP
  method names"
  [allowed]
    (->
      (options allowed)
      (status 405)))
