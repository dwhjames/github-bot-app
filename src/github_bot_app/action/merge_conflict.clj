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
                  :number merged-pull-id
                  :base-ref base-ref
                  :others {:count (count open-pulls)
                           :numbers (map :number open-pulls)}}))
      (doseq [p open-pulls]
        (pools/dispatch
         #(let [pull (specific-pull owner repo (:number p) config)]
            (-> pull
                (select-keys [:number :state :mergeable])
                (merge {:api-call :specific-pull})
                pr-str
                log/info))))
      (pools/schedule
       5 TimeUnit/MINUTES
       (fn []
         (log/info (pr-str {:schedule :run
                            :task schedule-uuid}))
         (pools/dispatch
          (fn []
            (let [open-pulls-later (search-open-pulls owner repo base-ref config)]
              (log/info {:api-call :open-pulls
                         :base-ref base-ref
                         :count (count open-pulls-later)
                         :numbers (map :number open-pulls-later)})
              (doseq [p open-pulls-later]
                (pools/dispatch
                 (fn []
                   (let [pull-id (:number p)
                         pull (specific-pull owner repo pull-id config)]
                     (-> pull
                         (select-keys [:number :state :mergeable])
                         (merge {:api-call :specific-pull})
                         pr-str
                         log/info)                     
                     (when (not (get pull :mergeable true))
                       (log/info (pr-str
                                  {:info :merge-conflict
                                   :number pull-id
                                   :base-ref base-ref
                                   :head-ref (get-in pull [:head :ref])
                                   :last-merged merged-pull-id}))
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
                                   :number pull-id
                                   :info :merge-conflict}))))))))))))
      (log/info (pr-str
                 {:schedule
                  {:delay 5
                   :unit :minutes}
                  :task schedule-uuid
                  :description (str "\"search for merge conflicts against " base-ref "\"")})))))
