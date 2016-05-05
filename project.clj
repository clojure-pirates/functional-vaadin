(defproject com.prajnainc/functional-vaadin "0.1.0-SNAPSHOT"
  :description "A functional interface to Vaadin"
  :url "https://github.com/wizardpb/functional-vaadin"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-codox "0.9.5"]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.vaadin/vaadin-server "7.6.5"]
                 [com.vaadin/vaadin-client-compiled "7.6.5"]
                 [com.vaadin/vaadin-themes "7.6.5"]]
  :codox {:namespaces [functional-vaadin.core
                       functional-vaadin.data-binding.conversion
                       functional-vaadin.ui.IUIDataStore]}
  :profiles {:provided {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]]}
             :dev      {:aot [functional-vaadin.ui.TestUI]
                        :source-paths ["src" "dev"]
                        :dependencies [[org.apache.directory.studio/org.apache.commons.io "2.4"]
                                       [org.clojure/tools.nrepl "0.2.11"]
                                       [org.eclipse.jetty/jetty-server "9.3.8.v20160314"]
                                       [org.eclipse.jetty/jetty-servlet "9.3.8.v20160314"]
                                       ]
                        }
             :jar {:aot [functional-vaadin.ui.TestUI]}}
  )
