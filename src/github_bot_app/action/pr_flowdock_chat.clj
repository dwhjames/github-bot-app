(ns github-bot-app.action.pr-flowdock-chat
  (:require [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [clj-http.client :as http-client]
            [tentacles.issues :as gh-issues]
            [github-bot-app.pools :as pools]
            [github-bot-app.action.timer :refer [github-api-time!]]))


(def data-cmd-pat
  #"^[@](\S+)[^{]*([{].*)")


(defn- send-flowdock-chat
  [content tags config]
  (http-client/post
   (format "https://api.flowdock.com/v1/messages/chat/%s"
           (get-in config [:flowdock :token]))
   {:content-type :json
    :body
    (json/generate-string
     {:content content
      :tags tags
      :external_user_name "GitHub-bot-app"})}))


(defn run-action [event payload config]
  (when (and (= event
                "pull_request")
             (= (:action payload)
                "closed")
             (get-in payload [:pull_request :merged] false))
    (let [owner (get-in payload [:repository :owner :login])
          repo (get-in payload [:repository :name])
          pull-id (get-in payload [:pull_request :number])
          comments
          (github-api-time!
           (gh-issues/issue-comments
            owner repo pull-id
            (:auth-options config)))]
      (log/info (pr-str
                 {:api-call :issue-comments
                  :url (get-in payload [:pull_request :comments_url])
                  :count (count comments)}))
      (doseq [comment comments]
        (when-let [matches (re-find data-cmd-pat (:body comment))]
          (let [name (nth matches 1)
                m (-> matches (nth 2) read-string)]
            (when (and (= name (:bot-name config))
                       (map? m)
                       (= (:event m)
                          :merged)
                       (= (get-in m [:do :send])
                          :flowdock)
                       (get-in m [:do :say]))
              (pools/dispatch
               "send flowdock chat"
               (fn []
                 (send-flowdock-chat (get-in m [:do :say])
                                     [(str "#" pull-id)]
                                     config)
                 (log/info (pr-str
                            {:api-call :flowdock-push-chat
                             :say (get-in m [:do :send])})))))))))))
