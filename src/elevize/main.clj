(ns elevize.main
  (:gen-class)
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [duct.component.ragtime :as ragtime]
            [duct.middleware.errors :refer [wrap-hide-errors]]
            [duct.util.runtime :refer [add-shutdown-hook]]
            [elevize.config :as config]
            [elevize.db.common-db :as common-db :refer [esc]]
            [elevize.system :refer [new-system]]
            [meta-merge.core :refer [meta-merge]]
            [taoensso.timbre :as timbre]))

(def prod-config
  {:app {:middleware     [[wrap-hide-errors :internal-error]]
         :internal-error (io/resource "errors/500.html")}})

(def config
  (meta-merge config/defaults
              config/environ
              prod-config))

(defn -main [& args]
  (let [system (-> (new-system config)
                   component/start)
        db-spec (-> system :db :spec)]
    (println "Started HTTP server on port" (-> system :http :port))
    (println "Running DB migrations ...")
    (-> system :ragtime ragtime/migrate)
    (add-shutdown-hook ::stop-system #(component/stop system))

    ;; some ONE SHOT DB transformations
    ;; (when (zero? (:count (first (jdbc/query db-spec [(str "SELECT COUNT(*) FROM " (esc :alarm-history) " WHERE " (esc :device-code) " IS NOT NULL")]))))
    ;;   (timbre/info {:a ::filling-device-codes-into-alarm-history})
    ;;   (jdbc/with-db-transaction [db-tx db-spec]
    ;;     (doseq [device (common-db/select db-spec :device {})]
    ;;       (jdbc/update! db-tx (esc :alarm-history) (esc {:device-code (:code device)}) [" \"device-id\" = ? " (:id device)]))))

    ;; (when (zero? (:count (first (jdbc/query db-spec [(str "SELECT COUNT(*) FROM " (esc :plc-msg-history) " WHERE " (esc :user-login) " IS NOT NULL")]))))
    ;;   (timbre/info {:a ::filling-user-logins-into-plc-msg-history})
    ;;   (jdbc/with-db-transaction [db-tx db-spec]
    ;;     (doseq [user (common-db/select db-spec :user {})]
    ;;       (jdbc/update! db-tx (esc :plc-msg-history) (esc {:user-login (:login user)}) [" \"user-id\" = ? " (:id user)]))))
    ))
