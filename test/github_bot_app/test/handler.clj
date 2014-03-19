(ns github-bot-app.test.handler
  (:use clojure.test
        ring.mock.request  
        github-bot-app.handler))

(deftest test-app
  (testing "main route"
    (let [response (app (request :get "/"))]
      (is (= (:status response) 200))
      (is (= (:body response) "<h1>GitHub-Bot-App</h1>"))))
  
  (testing "not-found route"
    (let [response (app (request :get "/invalid"))]
      (is (= (:status response) 404)))))
