(defproject github-bot-app "0.1.0-SNAPSHOT"
  :description "A GitHub Webhook Bot"
  :url "https://github.com/dwhjames/github-bot-app"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :manual
            :comments "same as Clojure"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.6"]
                 [tentacles "0.2.6"]
                 [cheshire "5.3.1"]
                 [metrics-clojure-ring "1.0.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [ch.qos.logback/logback-classic "1.1.1"]]
  :plugins [[lein-ring "0.8.10"]
            [lein-elastic-beanstalk "0.2.8-SNAPSHOT"]
            ;[lein-beanstalk "0.2.7"]
            [lein-awsuberwar "0.1.0"]]
  :ring {:handler github-bot-app.handler/app}
  :jvm-opts ["-Dcatalina.base=."]
  :profiles
  {:test {:dependencies [[javax.servlet/servlet-api "2.5"]
                         [ring-mock "0.1.5"]]}}
  :war-resources-path "war-resources")
