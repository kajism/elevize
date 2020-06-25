(ns elevize.component.alarm-listener
  (:require [clojure.edn :as edn]
            [com.stuartsierra.component :as component]
            [elevize.component.plc :as plc]
            [elevize.component.sente :as sente]
            [elevize.db.service :as service]
            [net.tcp.server :as tcp-server]
            [taoensso.timbre :as timbre]))

(defn make-handler [db-spec sente]
  (let [client-broadcast-fn (sente/make-client-broadcast-fn sente)]
    (fn [reader writer]
      (try
        (let [alarm-msg (plc/sanitize-response (.readLine reader))]
          (timbre/info {:a ::alarm-received :msg alarm-msg})
          #_(service/process-alarm db-spec alarm-msg client-broadcast-fn)
          (.append writer "ok"))
        (catch Exception e
          (timbre/error {:a ::alarm-read-or-processing-error :ex-info (ex-info "Chyba při přijetí alarmu" {} e)}))))))

(defrecord AlertListener [db sente alarm-port]
  component/Lifecycle
  (start [component]
    (let [port (or alarm-port 11000)
          s (tcp-server/tcp-server
             :host "0.0.0.0"
             :port port
             :handler (tcp-server/wrap-io (make-handler (:spec db) sente)))]
      (timbre/info {:a ::starting-alarm-listener :port port})
      (tcp-server/start s)
      (assoc component :server s)))
  (stop [component]
    (when (:server component)
      (timbre/info {:a ::stopping-alarm-listener})
      (tcp-server/stop (:server component)))
    (dissoc component :server)))

(defn alarm-listener [opts]
  (map->AlertListener opts))
