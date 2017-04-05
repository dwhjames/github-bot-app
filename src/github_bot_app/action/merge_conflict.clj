(ns github-bot-app.action.merge-conflict
  (:require [clojure.tools.logging :as log]
            [tentacles.issues :as gh-issues]
            [tentacles.pulls :as gh-pulls]
            [github-bot-app.pools :as pools]
            [github-bot-app.action.timer :refer [github-api-time!]])
  (:import java.util.concurrent.TimeUnit))


(defn- search-open-pulls
  [owner repo base-ref config]
  (github-api-time!
    (gh-pulls/pulls
     owner repo
     (merge
      (:auth-options config)
      {:state "open"
       :base base-ref}))))


(defn- specific-pull
  [owner repo pull-id config]
  (github-api-time!
    (gh-pulls/specific-pull
     owner repo pull-id
     (:auth-options config))))


(def conflict-comment-pat
  #"^It looks like #\d+ got there first")


(defn- last-conflict-comment-instant
  [owner repo pull-id config]
  (let [comments (github-api-time!
          (gh-issues/issue-comments
           owner repo pull-id
           (:auth-options config)))]
    (log/info (pr-str
               {:api-all :issue-comments
                :number pull-id
                :count (count comments)}))
    (->> comments
         (filter #(re-find conflict-comment-pat (:body %)))
         (map :created_at)
         sort
         last)))


(defn- last-commit-instant
  [owner repo pull-id config]
  (let [commits (github-api-time!
                 (gh-pulls/commits
                  owner repo pull-id
                  (:auth-options config)))]
    (log/info (pr-str
               {:api-all :pull-commits
                :number pull-id
                :count (count commits)}))
    (->> commits
         (map #(get-in % [:commit :committer :date]))
         sort
         last)))


(defn run-action [event payload config]
  (when (and (= event
                "pull_request")
             (= (:action payload)
                "closed")
             (get-in payload [:pull_request :merged] false))
    (let [owner (get-in payload [:repository :owner :login])
          repo (get-in payload [:repository :name])
          merged-pull-id (get-in payload [:pull_request :number])
          base-ref (get-in payload [:pull_request :base :ref])
          open-pulls (search-open-pulls owner repo base-ref config)
          schedule-uuid (java.util.UUID/randomUUID)]
      (log/info (pr-str
                 {:event :pull-request
                  :action :merged
                  :url (get-in payload [:repository :pulls_url])
                  :number merged-pull-id
                  :base-ref base-ref
                  :others {:count (count open-pulls)
                           :numbers (map :number open-pulls)}}))
      (doseq [p open-pulls]
        (pools/dispatch
         (str "poking PR #" (:number p))
         #(let [pull (specific-pull owner repo (:number p) config)]
            (-> pull
                (select-keys [:url :state :mergeable])
                (merge {:api-call :specific-pull})
                pr-str
                log/info))))
      (pools/schedule
       5 TimeUnit/MINUTES
       (fn []
         (log/info (pr-str {:schedule :run
                            :task schedule-uuid}))
         (pools/dispatch
          "scanning open PRs for merge conflicts"
          (fn []
            (let [open-pulls-later (search-open-pulls owner repo base-ref config)]
              (log/info {:api-call :open-pulls
                         :url (get-in payload [:repository :pulls_url])
                         :base-ref base-ref
                         :count (count open-pulls-later)
                         :numbers (map :number open-pulls-later)})
              (doseq [p open-pulls-later]
                (pools/dispatch
                 (str "checking PR #" (:number p) " for merge conflicts")
                 (fn []
                   (let [pull-id (:number p)
                         pull (specific-pull owner repo pull-id config)]
                     (-> pull
                         (select-keys [:url :state :mergeable])
                         (merge {:api-call :specific-pull})
                         pr-str
                         log/info)                     
                     (when (false? (:mergeable pull))
                       (log/info (pr-str
                                  {:info :merge-conflict
                                   :url (:url p)
                                   :base-ref base-ref
                                   :head-ref (get-in pull [:head :ref])
                                   :last-merged merged-pull-id}))
                       (let [comment-inst (last-conflict-comment-instant owner repo pull-id config)
                             commit-inst (last-commit-instant owner repo pull-id config)]
                         (log/info (pr-str
                                    {:info :merge-conflict
                                     :url (:url p)
                                     :comment-inst comment-inst
                                     :commit-inst commit-inst}))
                         (if (< (compare comment-inst commit-inst) 0) ;; true if comment-instant is nil
                           (do
                             (github-api-time!
                              (gh-issues/create-comment
                               owner repo pull-id
                               (str "It looks like #"
                                    merged-pull-id
                                    " got there first and ruined your clean merge!\n\n"
                                    "You'll need to need to resolve the conflicts with the `"
                                    base-ref
                                    "` branch.\n")
                               (:auth-options config)))
                             (log/info (pr-str
                                        {:api-call :create-comment
                                         :url (:comments_url p)
                                         :info :merge-conflict})))
                           (log/info (pr-str
                                      {:info :existing-merge-conflict
                                       :url (:url p)}))))))))))))))
      (log/info (pr-str
                 {:schedule
                  {:delay 5
                   :unit :minutes}
                  :task schedule-uuid
                  :description (str "\"search for merge conflicts against " base-ref "\"")})))))
