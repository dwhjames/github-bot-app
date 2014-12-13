(ns github-bot-app.action.composite-pr
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [tentacles.issues :as gh-issues]
            [tentacles.pulls :as gh-pulls]
            [github-bot-app.action.timer :refer [github-api-time!]]))


(def merge-commit-pat
  #"^Merge pull request (#\d+)")


(defn run-action [event payload config]
  (when (and (= event
                "pull_request")
             (= (:action payload)
                "opened"))
    (let [owner (get-in payload [:repository :owner :login])
          repo (get-in payload [:repository :name])
          pull-id (get-in payload [:pull_request :number])
          commits (github-api-time!
                    (gh-pulls/commits
                     owner repo pull-id
                     (:auth-options config)))]
      (log/info (pr-str {:api-call :pull-commits
                         :url (get-in payload [:pull_request :commits_url])
                         :count (count commits)
                         :shas (mapv #(-> % :sha (subs 0 7)) commits)}))
      (when-let [pr-strs (->> commits
                              (mapcat
                               #(when-let [matches (re-find merge-commit-pat
                                                            (get-in % [:commit :message]))]
                                  [(second matches)]))
                              seq)]
        (github-api-time!
         (gh-issues/create-comment
          owner repo pull-id
          (str "This pull request includes pull requests "
               (str/join ", " pr-strs))
          (:auth-options config)))
        (log/info (pr-str {:api-call :create-comment
                           :url (get-in payload [:pull_request :comments_url])
                           :info :composite-pr}))))))
