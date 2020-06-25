(ns elevize.component.plc
  (:require [clojure.data :as data]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [elevize.cljc.util :as cljc.util]
            [elevize.db.common-db :as common-db]
            [elevize.db.service :as service]
            [elevize.tcp :as tcp]
            [taoensso.timbre :as timbre])
  (:import java.io.IOException
           [java.net ConnectException SocketTimeoutException]
           java.sql.SQLException
           java.sql.Timestamp
           java.util.Date))

(def simulation? (atom false))

(def simulation-update-hz 2)
(def simulation-fast-forward 30) ;; 1 = real-time

(defn start-simulation
  "Server side development REPL simulation from device-states historical data.
  Sends updates to all clients as if the data was received from PLC."
  [system start-inst]
  (let [db-spec (-> system :db :spec)
        vars-by-id (->> (common-db/select db-spec :variable {})
                        (map (juxt :id identity))
                        (into {}))
        devices-by-id (->> (common-db/select db-spec :device {})
                           (map (juxt :id identity))
                           (into {}))]
    (reset! simulation? true)
    (future
      (loop [inst (Timestamp. (.getTime start-inst))]
        (let [next-inst (-> inst .getTime (+ (quot 1000 simulation-update-hz)) Timestamp.)
              changes (->> (jdbc/query  db-spec [(str "SELECT * FROM " (common-db/esc :status-history)
                                                      " WHERE "
                                                      (common-db/esc :timestamp) " >= ? AND "
                                                      (common-db/esc :timestamp) " < ?")
                                                 inst
                                                 next-inst])
                           (reduce (fn [out row]
                                     (let [var (-> row :variable-id vars-by-id)]
                                       (assoc-in out
                                                 [(-> var
                                                      :device-id
                                                      devices-by-id
                                                      :code)
                                                  (:name var)]
                                                 (edn/read-string (:value row)))))
                                   {"EB1" {"Cas" next-inst}}))]
          (swap! (-> system :plc :device-states) #(merge-with merge % changes))
          (when @simulation?
            (Thread/sleep (/ 1000 simulation-update-hz simulation-fast-forward))
            (recur next-inst)))))))

(defn stop-simulation []
  (reset! simulation? false))

(defn parse-all-states [devices-by-code msg reception-errors]
  ;;(timbre/debug {:a ::received-data-msg :msg msg})
  (->> (str/split (str msg) #"\r\n")
       (map #(str/split % #";"))
       (map (juxt first rest))
       (into {})
       (reduce (fn [out [device-code values]]
                 (let [headers (:-var-headers (get devices-by-code device-code))]
                   (if (not= (count headers) (count values))
                     (do
                       (timbre/error {:a ::headers-dont-match :device device-code :headers-count (count headers) :values-count (count values)})
                       (swap! reception-errors assoc device-code (str "Počet hlaviček je " (count headers) ", ale hodnot " (count values) "!")))
                     (swap! reception-errors assoc device-code ""))
                   (let [device-values (zipmap headers (map cljc.util/parse-plc-value values))
                         cas (get device-values "Cas")]
                     (if (= (type cas) Date)
                       (-> out
                           (assoc device-code (dissoc device-values "Cas"))
                           (assoc-in ["EB1" "Cas"] cas))
                       (do
                         (timbre/error {:a ::discarding-msg-with-invalid-time :msg msg})
                         out)))))
               {})))

(defn add-state-watches [plc client-broadcast-fn]
  (let [db-spec (get-in plc [:db :spec])]
    (timbre/info {:a ::adding-device-states-watch})
    (add-watch (:device-states plc)
               :device-states-watch
               (fn [_ _ old new]
                 (let [[_ changes _] (data/diff old new)
                       changes (->> changes
                                    (filter (fn [[device vars]]
                                              (or (= device "EB1")
                                                  (cljc.util/more-than-time-change? vars))))
                                    (into {}))]
                   (when (seq changes)
                     (timbre/debug {:a ::device-states-changes :changes changes})
                     (client-broadcast-fn [:elevize/device-states changes])
                     (when-not @simulation?
                       (service/save-device-state db-spec changes))))))
    (add-watch (:reception-errors plc)
               :reception-errors-watch
               (fn [_ _ old new]
                 (let [[_ changes _] (data/diff old new)]
                   (when (seq changes)
                     (client-broadcast-fn [:elevize/entities-updated {:reception-error (->> new
                                                                                            (map (fn [[device-code msg]]
                                                                                                   [device-code {:id device-code
                                                                                                                 :errors msg}]))
                                                                                            (into {}))}])))))

    (defn- assoc-alarm-duration-min [alarm]
      (assoc alarm :duration-min (-> (.getTime (:last-received alarm))
                                     (- (.getTime (:timestamp alarm)))
                                     (/ 60000.0)
                                     (Math/round)
                                     (inc))))

    (add-watch (:alarms plc)
               :alarms-watch
               (fn [_ _ old new]
                 (let [[removes adds _] (data/diff old new)
                       removes (->> removes
                                    (vals)
                                    (remove (fn only-last-received-changed [alarm-changes]
                                              (= (count alarm-changes) 1))))
                       adds (->> adds
                                 (keys)
                                 (map #(get new %))
                                 (map assoc-alarm-duration-min)
                                 (map (juxt :id identity))
                                 (into {}))]
                   (doseq [alarm removes]
                     (common-db/save! db-spec :alarm-history (-> alarm
                                                                 (assoc-alarm-duration-min)
                                                                 (select-keys [:id :duration-min]))))
                   (client-broadcast-fn [:elevize/entities-updated {:alarm (reduce (fn [out alarm]
                                                                                     (assoc out (:id alarm) nil))
                                                                                   adds
                                                                                   removes)
                                                                    :alarm-history adds}]))))))

(def alarm-max-life-ms (* 70 1000))

(defn process-datagram [plc datagram]
  (let [[_ msg-type msg] (re-find #"^(\w);(.+)$" datagram)
        devices-by-code (service/select-devices-by-code (get-in plc [:db :spec]))]
    (case msg-type
      "A" ;; alarms
      (let [_ (timbre/info {:a ::alarm-received :msg msg})
            [device-code kks alarm-msg code info1 info2 timestamp] (str/split msg #";")
            timestamp (cljc.util/parse-plc-value timestamp)
            row {:device-id (:id (get devices-by-code device-code))
                 :device-code device-code
                 :kks kks
                 :alarm-id (cljc.util/parse-plc-value code)
                 :alarm-info-id (cljc.util/parse-plc-value info1)
                 :alarm-info2-id (cljc.util/parse-plc-value info2)
                 :msg alarm-msg
                 :timestamp (Timestamp. (.getTime timestamp))}
            key ((juxt :device-id :kks :alarm-id :alarm-info-id :alarm-info2-id) row)
            existing (get @(:alarms plc) key)
            row (-> (or existing (common-db/save! (get-in plc [:db :spec]) :alarm-history row))
                    (assoc :last-received timestamp))]
        (swap! (:alarms plc) (fn [alarms]
                                     (->> (assoc alarms key row)
                                          (remove (fn [[key alarm]]
                                                    (> (- (.getTime timestamp)
                                                          (.getTime (:last-received alarm)))
                                                       alarm-max-life-ms)))
                                          (into {})))))
      "C" ;; command ack
      (timbre/info {:a ::plc-cmd-received :msg msg})
      "D" ;; device states
      (try
        (let [new-state (parse-all-states devices-by-code msg (:reception-errors plc))]
          (swap! (:device-states plc) #(merge-with merge % new-state))
          nil)
        (catch Throwable e
          (timbre/error {:a ::datagram-processing-error :ex-info (ex-info "Chyba při zpracování multicastu z PLC" {:datagram datagram} e)})))
      (timbre/error {:a ::unknown-msg-type :datagram datagram}))))

(defn- save-from-logs [log-nums]
  (let [db-spec {:connection-uri "jdbc:postgresql://localhost/elevize?user=elevize&password=elepwd"}]
    (doseq [n log-nums]
      (jdbc/with-db-transaction [db-tx db-spec]
        (let [device-codes-by-id (service/select-device-codes-by-id db-tx)
              var-ids-by-code&name (service/select-var-ids-by-code&name db-tx device-codes-by-id)]
          (with-open [rdr (clojure.java.io/reader (str "./log/elevize.log.0" n))]
            (doseq [line (line-seq rdr)
                    :let [changes (:changes
                                   (try
                                     (edn/read-string line)
                                     (catch Exception e
                                       "")))]
                    :when (seq changes)]
              (service/save-device-state db-tx var-ids-by-code&name changes))))))))

(defrecord Plc [host port all-port db poll-ms multicast-group multicast-port]
  component/Lifecycle
  (start [component]
    (timbre/info {:a ::starting-plc-communicator :host host :port port})
    (timbre/info {:a ::multicast-config :group multicast-group :port multicast-port})
    (let [multicast-socket (tcp/multicast-socket multicast-group multicast-port)
          multicast-enabled (atom true)
          component (assoc component
                           :device-states (atom {})
                           ;;:device-states-history (atom nil)
                           :reception-errors (atom {})
                           :alarms (atom {}))]
      (future
        (while @multicast-enabled
          ;;(timbre/debug "Waiting for multicast datagram")
          (try
            (let [datagram (tcp/receive-datagram multicast-socket)]
              #_(timbre/debug {:a ::datagram-received :text datagram})
              (process-datagram component datagram))
            (catch SocketTimeoutException e
              (timbre/warn {:a ::multicast-socket-timeout-ex :port multicast-port :group multicast-group :ex-msg (.getMessage e)})
              (Thread/sleep 1000))
            (catch Throwable e
              (timbre/error {:a ::datagram-receive-error :ex-info (ex-info "Chyba při příjmu multicastu" {:port multicast-port :group multicast-group} e)}))))
        (timbre/info {:a ::multicast-stopped}))
      (assoc component :multicast {:socket multicast-socket
                                   :enabled multicast-enabled})))

  (stop [component]
    (timbre/info {:a ::stopping-plc-communicator})
    (when (:multicast component)
      (timbre/debug {:a ::cancelling-multicast-receiver})
      (reset! (some-> component :multicast :enabled) false)
      (some-> component :multicast :socket (tcp/multicast-leave-group multicast-group)))
    (dissoc component :multicast)
    component))

(defn plc [opts]
  (map->Plc opts))

(defn sanitize-response [resp]
  (-> resp
      str
      str/trim-newline
      (str/replace #"\x00" "")))

(defn send-request
  ([plc user-login msg]
   (send-request plc user-login msg true))
  ([plc user-login msg retry?]
   (timbre/info {:a ::plc-request :msg msg :user/login user-login})
   (try
     (let [response (sanitize-response (tcp/send-request (:host plc) (:port plc) (str user-login ";" msg)))]
       (timbre/info {:a ::plc-response :response response})
       response)
     (catch ConnectException e
       (let [exi (ex-info "Chyba komunikace s řídící jednotkou" {:host (:host plc) :port (:port plc) :msg msg} e)]
         (timbre/warn {:a ::send-request-connect-exception :ex-info exi})
         (if retry?
           (do
             (Thread/sleep 1000)
             (send-request plc user-login msg false))
           {:error/msg (str (.getMessage exi) " / " (.getMessage e))})))
     (catch Exception e
       (let [exi (ex-info "Chyba komunikace s řídící jednotkou" {:host (:host plc) :port (:port plc) :msg msg} e)]
         (timbre/error {:a ::send-request-exception :ex-info exi})
         {:error/msg (str (.getMessage exi) " / " (.getMessage e))})))))

