(defproject github-bot-app "0.1.0-SNAPSHOT"
  :description "A GitHub Webhook Bot"
  :url "https://github.com/dwhjames/github-bot-app"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :manual
            :comments "same as Clojure"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [commons-codec "1.10"]
                 [compojure "1.5.2"]
                 [ring/ring-defaults "0.2.3"]
                 [irresponsible/tentacles "0.6.1"]
                 [cheshire "5.7.0"]
                 [io.dropwizard.metrics/metrics-core "3.1.2"]
                 [io.dropwizard.metrics/metrics-jvm "3.1.2"]
                 [measure "0.1.7"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-api "1.7.25"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [net.logstash.logback/logstash-logback-encoder "4.9"]]
  :plugins [[lein-ring "0.9.4"]]
  :ring {:handler github-bot-app.handler/app
         :init github-bot-app.handler/init
         :destroy github-bot-app.handler/destroy}
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring-mock "0.1.5"]]}
             :uberjar {:aot :all}})
