(ns elevize.component.sente
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [GET POST routes]]
            [elevize.component.plc :as plc]
            [elevize.db.common-db :as common-db]
            [elevize.db.import-xlsx :as import-xlsx]
            [elevize.db.service :as service]
            [elevize.db.runtime-diary]
            [taoensso.sente :as sente]
            [taoensso.sente.packers.transit :as sente-transit]
            [taoensso.timbre :as timbre])
  (:import java.sql.Timestamp))

#_(defn sente-routes [{{ring-ajax-post :ring-ajax-post
                        ring-ajax-get-or-ws-handshake :ring-ajax-get-or-ws-handshake} :sente}]
    (routes
     (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
     (POST "/chsk" req (ring-ajax-post                req))))

(defn save-n-broadcast [client-broadcast-fn db-spec table-kw ent]
  (let [saved (common-db/save! db-spec table-kw ent)]
    (client-broadcast-fn [:elevize/entities-updated {table-kw {(:id saved) saved}}])
    (:id saved)))

(defn send-request-to-plc [plc db-spec user client-broadcast-fn req]
  (let [resp (plc/send-request plc (:login user) req)]
    (save-n-broadcast client-broadcast-fn db-spec :plc-msg-history
                      {:req req
                       :resp (or (:error/msg resp) resp)
                       :user-id (:id user)
                       :user-login (:login user)
                       :created (some-> (get-in @(:device-states plc) ["EB1" "Cas"]) (.getTime) (Timestamp.))})
    resp))

(defn make-event-msg-handler [plc client-broadcast-fn]
  (fn [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
    (let [user (get-in ring-req [:session :user])
          db-spec (get-in plc [:db :spec])
          result (if-not ((:-rights user) id)
                   {:error/msg (str "Přístup odmítnut (" id ")")}
                   (case id
                     :user/auth (get-in ring-req [:session :user])
                     :user/select (common-db/select db-spec :user {})
                     :user/save (save-n-broadcast client-broadcast-fn db-spec :user ?data)
                     :user/delete (common-db/delete! db-spec :user ?data)
                     :subsystem/select (common-db/select db-spec :subsystem {})
                     :subsystem/save (save-n-broadcast client-broadcast-fn db-spec :subsystem ?data)
                     :subsystem/delete (common-db/delete! db-spec :subsystem ?data)
                     :inventory-item/select (common-db/select db-spec :inventory-item {})
                     :inventory-item/save (save-n-broadcast client-broadcast-fn db-spec :inventory-item ?data)
                     :inventory-item/delete (common-db/delete! db-spec :inventory-item ?data)
                     :inventory-tx/select (common-db/select db-spec :inventory-tx {})
                     :inventory-tx/save (save-n-broadcast client-broadcast-fn db-spec :inventory-tx (assoc ?data :user-login (:login user)))
                     :device/select (common-db/select db-spec :device {})
                     :device/save (save-n-broadcast client-broadcast-fn db-spec :device ?data)
                     :device/delete (common-db/delete! db-spec :device ?data)
                     :enum-item/select (common-db/select db-spec :enum-item {})
                     :enum-item/save (save-n-broadcast client-broadcast-fn db-spec :enum-item ?data)
                     :enum-item/delete (common-db/delete! db-spec :enum-item ?data)
                     :variable/select (common-db/select db-spec :variable {})
                     :variable/save (save-n-broadcast client-broadcast-fn db-spec :variable ?data)
                     :variable/delete (common-db/delete! db-spec :variable ?data)
                     :var-group/select (common-db/select db-spec :var-group {})
                     :var-group/save (save-n-broadcast client-broadcast-fn db-spec :var-group ?data)
                     :var-group/append-vars (let [saved (service/var-group-append-vars db-spec ?data)]
                                              (client-broadcast-fn [:elevize/entities-updated {:var-group {(:id saved) saved}}])
                                              (:id saved))
                     :var-group/delete (common-db/delete! db-spec :var-group ?data)
                     :plc-msg-history/select (common-db/select db-spec :plc-msg-history {})
                     :status-history/select (common-db/select db-spec :status-history {})
                     :device-state/select (service/select-device-states db-spec
                                                                        (:from ?data)
                                                                        (:to ?data)
                                                                        (:full-state? ?data))
                     :elevize/version-info (service/app-version-info)
                     :elevize/command (send-request-to-plc plc db-spec user client-broadcast-fn (:msg ?data))
                     :elevize/set-device-state-variable
                     (let [{:keys [device-code var-name var-set-name value]} ?data]
                       (swap! (:device-states plc) #(assoc-in % [device-code var-name] nil))
                       (send-request-to-plc plc db-spec user client-broadcast-fn (str "SET " device-code ":" var-set-name ":=" value)))
                     :elevize/load-device-states @(:device-states plc)
                     :import-xlsx/select (import-xlsx/select-history)
                     :import-xlsx/download-and-import
                     (let [msg (service/config-refresh db-spec (:login user))]
                       (client-broadcast-fn [:elevize/entities-updated {:import-xlsx (->> (import-xlsx/select-history)
                                                                                          (map (juxt :id identity))
                                                                                          (into {}))}])
                       msg)
                     :import-xlsx/rollback-last
                     (let [out (service/config-rollback db-spec ?data)]
                       (client-broadcast-fn [:elevize/entities-updated {:import-xlsx (assoc (->> (import-xlsx/select-history)
                                                                                                 (map (juxt :id identity))
                                                                                                 (into {}))
                                                                                            ?data {:id ?data :user-login "smazáno"})}])
                       out)
                     :reception-error/select (map (fn [[device-code msg]]
                                                    {:id device-code :errors msg})
                                                  @(:reception-errors plc))
                     :alarm-history/select (common-db/select db-spec :alarm-history {})
                     :runtime-diary/select (common-db/select db-spec :runtime-diary {:daily-rec? true})
                     :runtime-diary/delete (common-db/delete! db-spec :runtime-diary ?data)
                     :alarm/select (vals @(:alarms plc))
                     :fuel-supply/get-s-weights (service/get-silo-weights)
                     :chsk/ws-ping nil ;; ignore this
                     (do
                       (timbre/error {:a ::unhandled-event :event event})
                       {:error/msg (str "Neznámá událost: " id)})))]
      (when ?reply-fn
        (?reply-fn result)))))

(defn make-client-broadcast-fn [{:keys [send-fn connected-uids] :as sente}]
  (fn [msg]
    (doseq [uid (:any @connected-uids)]
      (send-fn uid msg))))

(defrecord ChannelSocketServer
           [db plc
            ring-ajax-post ring-ajax-get-or-ws-handshake ch-chsk send-fn connected-uids router web-server-adapter handler options]
  component/Lifecycle
  (start [component]
    (let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids] :as _sente}
          (sente/make-channel-socket-server! web-server-adapter options)
          client-broadcast-fn (make-client-broadcast-fn _sente)]
      (plc/add-state-watches plc client-broadcast-fn)
      (-> component
          (merge _sente)
          (assoc :router (atom (sente/start-chsk-router! ch-recv (make-event-msg-handler plc client-broadcast-fn)))))))
  (stop [component]
    (if-let [stop-f (and router @router)]
      (assoc component :router (stop-f))
      component)))

(defn new-channel-socket-server
  ([web-server-adapter]
   (new-channel-socket-server nil web-server-adapter {}))
  ([event-msg-handler web-server-adapter]
   (new-channel-socket-server event-msg-handler web-server-adapter {}))
  ([event-msg-handler web-server-adapter options]
   (map->ChannelSocketServer {:web-server-adapter web-server-adapter
                              :handler event-msg-handler
                              :options (merge {:packer (sente-transit/get-transit-packer)} options)})))
