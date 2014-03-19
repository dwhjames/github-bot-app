(ns github-bot-app.action.hey-ping
  (:require [clojure.tools.logging :as log]
            [tentacles.issues :as gh-issues]
            [github-bot-app.action.timer :refer [github-api-time!]]))


(def hey-regex
  #"^hey\s+@(\S+)")


(defn run-action [event payload config]
  (when (= event "issue_comment")
    (let [owner (get-in payload [:repository :owner :login])
          repo (get-in payload [:repository :name])
          issue-id (get-in payload [:issue :number])
          comment-user (get-in payload [:comment :user :login])
          comment-body (get-in payload [:comment :body])
          matches (re-find hey-regex comment-body)]
      (when (and matches
                 (= (nth matches 1)
                    (:bot-name config)))
        (github-api-time!
          (gh-issues/create-comment
           owner repo issue-id
           (str "hey @" comment-user)
           (:auth-options config)))
        (log/info (pr-str
                   {:api-call :create-comment
                    :number issue-id
                    :info :hey-ping}))))))
