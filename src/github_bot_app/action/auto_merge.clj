(ns github-bot-app.action.auto-merge
  (:require [clojure.tools.logging :as log]
            [tentacles.core :as gh-api]
            [tentacles.pulls :as gh-pulls]
            [tentacles.repos :as gh-repos]
            [github-bot-app.action.timer :refer [github-api-time!]]))

(defn- statuses-for-ref
  [owner repo sha config]
  (github-api-time!
    (gh-repos/statuses
      owner repo sha
      (merge
        (:auth-options config)
        {:combined? false}))))

(defn- search-open-pulls
  [owner repo config]
  (github-api-time!
    (gh-pulls/pulls
     owner repo
     (merge
      (:auth-options config)
      {:state "open"}))))

(defn- search-open-pulls-by-branch
  [owner repo branch config]
  (github-api-time!
    (gh-pulls/pulls
     owner repo
     (merge
      (:auth-options config)
      {:state "open"
       :head (str repo ":" branch)}))))

(defn- specific-pull
  [owner repo pull-number config]
  (github-api-time!
    (gh-pulls/specific-pull
     owner repo pull-number
     (:auth-options config))))

(defn- pull-reviews
  [user repo pull-number config]
  (github-api-time!
    (gh-api/api-call :get "repos/%s/%s/pulls/%s/reviews" [user repo pull-number] (:auth-options config))))

(defn- merge-pull
  [owner repo pull-number config]
  (github-api-time!
    (gh-pulls/merge
      owner repo pull-number
      (:auth-options config))))

(defn- delete-branch
  [user repo branch config]
  (github-api-time!
    (gh-api/api-call :delete "repos/%s/%s/git/refs/heads/%s" [user repo branch] (:auth-options config))))

(defn run-action [event payload config]
  (cond
   (and (= event "status")
        (= (:state payload)
           "success"))
   (let [owner (get-in payload [:repository :owner :login])
         repo (get-in payload [:repository :name])
         status-sha (:sha payload)
         ;; branches that include the status'ed sha at the head
         branches (->> payload
                       :branches
                       (filter
                         #(= status-sha (get-in % [:commit :sha])))
                       (map :name))
         statuses (statuses-for-ref owner repo status-sha config)]
     (log/info (pr-str {:api-call :statuses-for-ref
                        :sha status-sha
                        :count (count statuses)}))
     ;; only proceed when non-empty success status from CI
     (when (->> statuses
                (filter #(= "success" (:state %)))
                (filter #(= (:context %)
                            (get-in config [:auto-merge :ci-status-context])))
                seq)
       (let [pulls (if (= 1 (count branches))
                     (search-open-pulls-by-branch
                      owner repo (first branches) config)
                     (search-open-pulls owner repo config))]
         (log/info (pr-str {:api-call :list-pulls
                            :branch-context branches
                            :count (count pulls)
                            :numbers (map :number pulls)}))
         (doseq [pull pulls]
           (let [pull-number (:number pull)
                 pull-sha (get-in pull [:head :sha])
                 reviews (pull-reviews owner repo pull-number config)]
             (log/info (pr-str {:api :list-pull-reviews
                                :pull-number pull-number
                                :count (count reviews)}))
             (when (and (= status-sha pull-sha)
                        (->> pull
                             :labels
                             (filter #(= (:name %)
                                         (get-in config [:auto-merge :auto-merge-label])))
                             seq)
                        (->> reviews
                             (filter #(= "APPROVED" (:state %)))
                             (filter #(= pull-sha (:commit_id %)))
                             seq))
                ;; shadow `pull` with fuller fetch (need to get merge status)
                (let [pull (specific-pull owner repo pull-number config)]
                  (log/info (pr-str {:api :specific-pull
                                     :pull-number pull-number}))
                  (when (and (= false (:merged pull))
                             (= true (:mergeable pull)))
                    (merge-pull owner repo pull-number config)
                    (log/info (pr-str {:api-call :merge-pull-request
                                       :url (:html_url pull)}))
                    (delete-branch owner repo (get-in pull [:head :ref]) config)
                    (log/info (pr-str {:api-call :delete-pr-branch
                                       :url (:html_url pull)
                                       :ref (get-in pull [:head :ref])}))))))))))))
