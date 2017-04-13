(ns github-bot-app.action.auto-label
  (:require [clojure.tools.logging :as log]
            [tentacles.issues :as gh-issues]
            [github-bot-app.action.timer :refer [github-api-time!]]))

(defn- mark-review-done [payload config]
  (let [owner (get-in payload [:repository :owner :login])
        repo (get-in payload [:repository :name])
        ;; support being called from both an issue_comment
        ;; and pull_request_review payload context
        pr-obj (or (:issue payload) (:pull_request payload))
        pr-id (:number pr-obj)
        pr-url (:url pr-obj)
        {:keys [review-done-remove-labels review-done-add-label]} (:auto-label config)]
   (github-api-time!
           (gh-issues/add-labels
            owner repo pr-id
            [review-done-add-label]
            (:auth-options config)))
   (log/info (pr-str
              {:api-call :add-labels
               :url pr-url
               :add-labels [review-done-add-label]}))
   (doseq [label review-done-remove-labels]
    (github-api-time!
      (gh-issues/remove-label owner repo pr-id label
       (:auth-options config)))
    (log/info (pr-str
               {:api-call :remove-label
                :url pr-url
                :remove-label label})))))

(defn run-action [event payload config]
  (cond
   (and (= event
           "pull_request")
        (= (:action payload)
           "opened"))
   (let [owner (get-in payload [:repository :owner :login])
         repo (get-in payload [:repository :name])
         issue-id (get-in payload [:pull_request :number])
         labels-to-add [(get-in config [:auto-label :review-start-label])]]
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
   ;; https://developer.github.com/v3/issues/#get-a-single-issue
   ;; Note: In the past, pull requests and issues were more closely aligned than they are now. As far as the API is concerned, every pull request is an issue, but not every issue is a pull request.
   ;; This endpoint may also return pull requests in the response. If an issue is a pull request, the object will include a pull_request key.
   (when (get-in payload [:issue :pull_request])
     (let [issue-user (get-in payload [:issue :user :login])
           comment-user (get-in payload [:comment :user :login])
           comment-body (get-in payload [:comment :body])
           {:keys [review-done-comment]} (:auto-label config)
           review-done-pat (re-pattern review-done-comment)]
       (when (and (not (= issue-user comment-user))
                  (re-find review-done-pat comment-body))
        (mark-review-done payload config))))

   (and (= event
           "pull_request_review")
        (= (:action payload) "submitted")
        (= (get-in payload [:review :state]) "approved"))
   (let [issue-user (get-in payload [:issue :user :login])
         review-user (get-in payload [:review :user :login])]
    (when (not (= issue-user review-user))
      (mark-review-done payload config)))))
