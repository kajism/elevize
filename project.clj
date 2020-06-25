(defproject elevize "1.0.211-SNAPSHOT"
  :description "Heating plant visualisation"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :jvm-opts ["-Duser.timezone=UTC"]
  :dependencies [[com.andrewmcveigh/cljs-time "0.5.2"]
                 [org.postgresql/postgresql "9.4.1212"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.946"]
                 [org.clojure/core.async "0.3.465"]
                 [org.clojure/core.cache "0.6.5"]
                 [org.clojure/core.memoize "0.5.9"]
                 [nrepl "0.6.0"]
                 [com.stuartsierra/component "0.3.1"]
                 [compojure "1.5.1"]
                 [duct "0.5.10"]
                 [environ "1.0.2"]
                 [meta-merge "0.1.1"]
                 [ring "1.5.0"]
                 [ring/ring-defaults "0.2.1"]
                 [http-kit "2.3.0"]
                 [ring-webjars "0.1.1"]
                 [org.slf4j/slf4j-nop "1.7.14"]
                 [org.webjars/normalize.css "3.0.2"]
                 [duct/hikaricp-component "0.1.0"]
                 [duct/ragtime-component "0.1.3"]
                 [re-com "2.1.0" :exclusions [reagent]]
                 [re-frame "0.10.2"]
                 [secretary "1.2.3"]
                 [com.cognitect/transit-clj "0.8.300"]
                 [com.cognitect/transit-cljs "0.8.243"]
                 [ring-middleware-format "0.7.0"]
                 [crypto-password "0.2.0"]
                 [hiccup "1.0.5"]
                 [com.taoensso/timbre "4.7.4"]
                 [com.taoensso/sente "1.9.0"]
                 [tcp-server "0.1.0"]
                 [clojure-csv "2.0.2"]
                 [dk.ative/docjure "1.10.0" :exclusions [commons-codec]]
                 [cljsjs/dygraph "1.1.1-1"]
                 [twarc "0.1.9"]
                 #_[org.clojure/core.rrb-vector "0.0.11"]
                 [clj-http "3.5.0"]]
  :plugins [[lein-environ "1.0.2"]
            [lein-gen "0.2.2"]
            [lein-cljsbuild "1.1.5"]]
  :generators [[duct/generators "0.5.10"]]
  :duct {:ns-prefix elevize}
  :main ^:skip-aot elevize.main
  :target-path "target/%s/"
  :resource-paths ["resources" "target/cljsbuild"]
  :prep-tasks [["javac"] ["cljsbuild" "once"] ["compile"]]
  :cljsbuild
  {:builds
   {:main {:jar          true
           :source-paths ["src"]
           :compiler     {:output-to     "target/cljsbuild/elevize/public/js/main.js"
                          ;;:closure-defines {"goog.DEBUG" false}
                          :optimizations :advanced
                          :checked-arrays false}}}}
  :aliases {"gen"   ["generate"]
            "setup" ["do" ["generate" "locals"]]}
  :profiles
  {:dev           [:project/dev  :profiles/dev]
   :test          [:project/test :profiles/test]
   :repl          {:resource-paths ^:replace ["resources" "target/figwheel"]
                   :prep-tasks     ^:replace [["javac"] ["compile"]]}
   :uberjar       {:aot :all}
   :profiles/dev  {}
   :profiles/test {}
   :project/dev   {:dependencies [[binaryage/devtools "0.9.10"]
                                  [reloaded.repl "0.2.3"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [eftest "0.1.1"]
                                  [kerodon "0.7.0"]
                                  [cider/piggieback "0.4.0"]
                                  [duct/figwheel-component "0.3.4"]
                                  [figwheel "0.5.14"]]
                   :source-paths ["dev"]
                   :repl-options {:init-ns          user
                                  :nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
                   :env          {:dev "true"}}
   :project/test  {}}
  :release-tasks
  [["vcs" "assert-committed"]
   ["change" "version" "leiningen.release/bump-version" "release"]
   ["vcs" "commit"]
   ["vcs" "tag" "--no-sign"]
   ["uberjar"]
   ["change" "version" "leiningen.release/bump-version"]
   ["vcs" "commit"]])
