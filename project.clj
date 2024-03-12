(defproject com.github.jimpil/launchdarkly-clj "0.1.0-SNAPSHOT"
  :description "Thin Clojure wrapper around the LaunchDarkly Java SDK"
  :url "https://github.com/jimpil/launchdarkly-clj"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [metosin/jsonista "0.3.8"]
                 [com.launchdarkly/launchdarkly-java-server-sdk "7.2.6" :scope "provided"]]
  :repl-options {:init-ns launchdarkly-clj.core})
