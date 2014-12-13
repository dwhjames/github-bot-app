(defproject github-bot-app "0.1.0-SNAPSHOT"
  :description "A GitHub Webhook Bot"
  :url "https://github.com/dwhjames/github-bot-app"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :manual
            :comments "same as Clojure"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.3.1"]
                 [ring/ring-defaults "0.1.2"]
                 [tentacles "0.2.6"]
                 [cheshire "5.3.1"]
                 [io.dropwizard.metrics/metrics-core "3.1.0"]
                 [io.dropwizard.metrics/metrics-jvm "3.1.0"]
                 [measure "0.1.7"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.2"]]
  :plugins [[lein-ring "0.8.13"]
            [lein-elastic-beanstalk "0.2.8-SNAPSHOT"]
            ;[lein-beanstalk "0.2.7"]
            [lein-awsuberwar "0.1.0"]]
  :ring {:handler github-bot-app.handler/app
         :init github-bot-app.handler/init
         :destroy github-bot-app.handler/destroy}
  :jvm-opts ["-Dcatalina.base=."]
  :profiles
  {:dev  {:dependencies [[javax.servlet/servlet-api "2.5"]
                         [ring-mock "0.1.5"]]}}
  :war-resources-path "war-resources")
