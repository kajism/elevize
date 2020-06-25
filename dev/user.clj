(ns user
  (:require [clojure.repl :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [eftest.runner :as eftest]
            [meta-merge.core :refer [meta-merge]]
            [reloaded.repl :refer [system init start stop go reset]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [duct.component.ragtime :as ragtime]
            [duct.component.figwheel :as figwheel]
            [elevize.config :as config]
            [elevize.system :as system]))

(def dev-config
  {:app {:middleware [wrap-stacktrace]}
   :figwheel
   {:server-port 3450
    :css-dirs ["resources/elevize/public/css"]
    :builds   [{:source-paths ["src" "dev"]
                :build-options
                {:optimizations :none
                 :main "cljs.user"
                 :asset-path "/js"
                 :output-to  "target/figwheel/elevize/public/js/main.js"
                 :output-dir "target/figwheel/elevize/public/js"
                 :source-map true
                 :source-map-path "/js"}}]}})

(def config
  (meta-merge config/defaults
              config/environ
              dev-config))

(defn new-system []
  (into (system/new-system config)
        {:figwheel (figwheel/server (:figwheel config))}))

(ns-unmap *ns* 'test)

(defn test []
  (eftest/run-tests (eftest/find-tests "test") {:multithread? false}))

(defn cljs-repl []
  (figwheel/cljs-repl (:figwheel system)))

(defn migrate []
  (concat
   (-> system :ragtime ragtime/reload ragtime/migrate)))

(defn rollback
  ([]  (rollback 1))
  ([x] (concat
        (-> system :ragtime ragtime/reload (ragtime/rollback x)))))

(when (io/resource "local.clj")
  (load "local"))

(reloaded.repl/set-init! new-system)

(defn db-spec []
  (-> system :db :spec))
