(ns github-bot-app.action.pr-police
  (:require [clojure.tools.logging :as log]
            [tentacles.issues :as gh-issues]
            [github-bot-app.action.timer :refer [github-api-time!]]))


(defn run-action [event payload config]
  (when (and (= event
                "pull_request")
             (= (:action payload)
                "opened")
             (empty? (get-in payload [:pull_request :body])))
    (let [owner (get-in payload [:repository :owner :login])
          repo (get-in payload [:repository :name])
          issue-id (get-in payload [:pull_request :number])
          user (get-in payload [:pull_request :user :login])]
      (github-api-time!
        (gh-issues/create-comment
         owner repo issue-id
         (str "Pull Request Police: @"
              user
              ", please add a description to your pull request.")
         (:auth-options config)))
      (log/info (pr-str
                 {:event :pull-request
                  :action :opened
                  :number issue-id
                  :police :no-description})))))
