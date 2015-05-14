(defproject github-bot-app "0.1.0-SNAPSHOT"
  :description "A GitHub Webhook Bot"
  :url "https://github.com/dwhjames/github-bot-app"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :manual
            :comments "same as Clojure"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [commons-codec "1.10"]
                 [compojure "1.3.4"]
                 [ring/ring-defaults "0.1.5"]
                 [tentacles "0.3.0"]
                 [cheshire "5.4.0"]
                 [io.dropwizard.metrics/metrics-core "3.1.2"]
                 [io.dropwizard.metrics/metrics-jvm "3.1.2"]
                 [measure "0.1.7"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.3"]]
  :plugins [[lein-ring "0.9.4"]]
  :ring {:handler github-bot-app.handler/app
         :init github-bot-app.handler/init
         :destroy github-bot-app.handler/destroy}
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring-mock "0.1.5"]]}
             :uberjar {:aot :all}})
