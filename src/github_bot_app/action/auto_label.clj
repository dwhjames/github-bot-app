(ns github-bot-app.action.auto-label
  (:require [clojure.tools.logging :as log]
            [tentacles.issues :as gh-issues]
            [github-bot-app.action.timer :refer [github-api-time!]]))


(defn run-action [event payload config]
  (cond
   (and (= event
           "pull_request")
        (= (:action payload)
           "opened"))
   (let [owner (get-in payload [:repository :owner :login])
         repo (get-in payload [:repository :name])
         issue-id (get-in payload [:pull_request :number])
         base-ref (get-in payload [:pull_request :base :ref])
         labels-to-add (concat
                        [(get-in config [:auto-label :review-label])]
                        (when (= base-ref "master")
                          [(get-in config [:auto-label :qa-label])]))]
     (github-api-time!
      (gh-issues/add-labels
       owner repo issue-id
       labels-to-add
       (:auth-options config)))
     (log/info (pr-str
                {:api-call :add-labels
                 :url (get-in payload [:pull_request :issue_url])
                 :add-labels labels-to-add})))

   (= event
      "issue_comment")
   (when (get-in payload [:issue :pull_request])
     (let [owner (get-in payload [:repository :owner :login])
           repo (get-in payload [:repository :name])
           issue-id (get-in payload [:issue :number])
           issue-user (get-in payload [:issue :user :login])
           comment-user (get-in payload [:comment :user :login])
           comment-body (get-in payload [:comment :body])
           {:keys [review-label review-done-label review-done-comment]} (:auto-label config)
           review-done-pat (re-pattern review-done-comment)]
       (when (and (not (= issue-user comment-user))
                  (re-find review-done-pat comment-body))
         (github-api-time!
          (gh-issues/add-labels
           owner repo issue-id
           [review-done-label]
           (:auth-options config)))
         (log/info (pr-str
                    {:api-call :add-labels
                     :url (get-in payload [:issue :labels_url])
                     :add-labels [review-done-label]}))
         (github-api-time!
          (gh-issues/remove-label
           owner repo issue-id
           review-label
           (:auth-options config)))
         (log/info (pr-str
                    {:api-call :remove-label
                     :url (get-in payload [:issue :labels_url])
                     :remove-label review-label})))))))
