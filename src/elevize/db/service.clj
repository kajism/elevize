(ns elevize.db.service
  (:require [clj-http.client :as client]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [clojure.pprint :refer [pprint]]
            [clojure.set :as set]
            [clojure.string :as str]
            [cognitect.transit :as transit]
            [crypto.password.scrypt :as scrypt]
            [elevize.cljc.util :as cljc.util]
            [elevize.config :as config]
            [elevize.db.common-db :as common-db :refer [esc]]
            [elevize.db.import-xlsx :as import-xlsx]
            [elevize.db.protocols :as protocols]
            [environ.core :refer [env]]
            [taoensso.timbre :as timbre])
  (:import java.net.URL
           [java.sql SQLException Timestamp]
           java.text.SimpleDateFormat
           java.util.Date))

(def plc-write-rights #{:elevize/set-device-state-variable
                        :elevize/command})

(def inventory-rights #{:inventory-item/select :inventory-item/save
                        :inventory-tx/select :inventory-tx/save})

(def rights-by-role {"kotelnik" plc-write-rights
                     "power-user" (-> #{:runtime-diary/select}
                                      (into plc-write-rights))
                     "admin" (-> #{:user/save :user/delete
                                   ;; :subsystem/save :subsystem/delete
                                   ;; :variable/save :variable/delete
                                   ;; :enum-item/save :enum-item/delete
                                   ;; :device/save :device/delete
                                   :runtime-diary/select :runtime-diary/delete
                                   :import-xlsx/select :import-xlsx/download-and-import :import-xlsx/rollback-last
                                   :reception-error/select
                                   :inventory-item/delete}
                                 (into plc-write-rights)
                                 (into inventory-rights))
                     "skladnik" inventory-rights})

(defn- get-rights-by-roles [user-roles]
  (->> (str/split (or user-roles "") #"\s*,\s*")
       (reduce
        (fn [out role]
          (into out (get rights-by-role role)))
        #{:user/auth
          :user/select
          :subsystem/select :variable/select :device/select :enum-item/select
          :alarm-history/select
          :alarm/select
          :plc-msg-history/select
          :status-history/select
          :device-state/select
          :elevize/version-info
          :elevize/load-device-states
          :fuel-supply/get-s-weights
          :var-group/select :var-group/save :var-group/append-vars :var-group/delete})))

(defn login
  "If user-name and password is valid returns user map including :-rights"
  [db-spec user-name pwd]
  (let [user (first (common-db/default-select db-spec :user {:login user-name}))]
    (if (and user (scrypt/check pwd (:passwd user)))
      (do
        (timbre/info {:a ::login-success :user-name user-name})
        (-> user
            (dissoc :passwd)
            (assoc :-rights (get-rights-by-roles (:roles user)))))
      (do
        (timbre/info {:a ::login-failure :user-name user-name})
        nil))))

(defn var-group-append-vars
  "Appends variables into a variable group"
  [db-spec {var-group-id :id member-var-ids :member-ids :as var-group-to-append}]
  (let [var-group (first (common-db/select db-spec :var-group {:id var-group-id}))]
    (common-db/save! db-spec :var-group
                     (update var-group :member-ids #(set/union (set %)
                                                               (set member-var-ids))))))

(defn app-version-info
  "Returns a map with :data instant and :version number string"
  []
  (let [[date-line version-line]
        (some-> (io/resource "META-INF/maven/elevize/elevize/pom.properties")
                (slurp)
                (str/split #"\n")
                (subvec 1 3)) #_["#Mon Mar 27 12:50:15 CEST 2017" "version=0\".1.17"]
        date-parser (SimpleDateFormat. "EEE MMM dd HH:mm:ss z yyyy")]
    {:date  (when date-line
              (.parse date-parser (subs date-line 1)))
     :version (some-> version-line (subs 8))
     :orphan? config/orphan?}))

(defn- read-var-last-change-timestamp
  "Returns when was a var last changed before date. When only date is supplied returns the time when all state was saved."
  ([db-spec date]
   (let [eb1-device-id (:id (first (common-db/select db-spec :device {:code "EB1"})))
         unchanging-var-id (:id (first (common-db/select db-spec :variable {:name "NazevBloku"
                                                                            :device-id eb1-device-id})))]
     (read-var-last-change-timestamp db-spec date unchanging-var-id)))
  ([db-spec date var-id]
   (->
    (jdbc/query db-spec [(str "SELECT \"timestamp\" FROM \"status-history\" "
                              " WHERE \"variable-id\" = ?  AND \"timestamp\" <= ?"
                              " ORDER BY \"timestamp\" DESC"
                              " LIMIT 1")
                         var-id
                         (-> date (.getTime) (Timestamp.))])
    (first)
    :timestamp
    (cljc.util/with-date #(t/minus % (t/minutes 2))))))

(def dev&var-cache (atom {}))

(defn- get-from-cache-or-load [kw load-fn]
  (or (get @dev&var-cache kw)
      (kw (swap! dev&var-cache assoc kw (load-fn)))))

(defn select-devices-by-code
  "Select devices and create a map indexed by device code.
  Result is cached until new xls config imported."
  [db-spec]
  (get-from-cache-or-load ::devices-by-code
                          #(try
                             (->> (common-db/select db-spec :device {})
                                  (map (juxt :code
                                             (fn [device]
                                               (assoc device :-var-headers (-> device :var-header str (str/split #"\n"))))))
                                  (into {}))
                             (catch SQLException e
                               (timbre/error {:a ::select-devices-by-code-error :ex-info (ex-info "Chyba cteni zarizeni z DB" {:db-spec db-spec} e)})
                               nil))))

(defn select-device-codes-by-id
  "Select devices and create a map of device codes indexed by device id.
  Result is cached until new xls config imported."
  [db-spec]
  (get-from-cache-or-load ::device-codes-by-id
                          #(->> (common-db/select db-spec :device {})
                                (map (juxt :id :code))
                                (into {}))))

(defn select-var-ids-by-code&name
  "Select variables and create a map of ids indexed by device code and variable name [dev-code var-name].
  Result is cached until new xls config imported."
  [db-spec]
  (get-from-cache-or-load ::var-ids-by-code&name
                          #(let [device-codes-by-id (select-device-codes-by-id db-spec)]
                             (->> (common-db/select db-spec :variable {})
                                  (reduce (fn [out var]
                                            (assoc-in out [(get device-codes-by-id (:device-id var)) (:name var)] (:id var)))
                                          {})))))

(defn select-var-code&names-by-id
  "Select variables and create a map of device code and variable name [dev-code var-name] indexed by variable id.
  Result is cached until new xls config imported."
  [db-spec]
  (get-from-cache-or-load ::var-code&names-by-id
                          #(let [device-codes-by-id (select-device-codes-by-id db-spec)]
                             (->> (common-db/select db-spec :variable {})
                                  (reduce (fn [out var]
                                            (assoc out (:id var) [(get device-codes-by-id (:device-id var)) (:name var)]))
                                          {})))))

(defn save-device-state
  "Saves a map of device state into status-history table. Changes map MUST contain EB1 Cas timestamp.  Input example below."
  #_{"EB1" {"Cas" #inst "2017-05-02T10:11:12.123"
            "var-nameX" 1}
     "device-code" {"var-name2" 2.0
                    "var-name3" "3"}}
  [db-spec device-state]
  (if-let [ts (some-> device-state (get-in ["EB1" "Cas"]) (.getTime) (Timestamp.))]
    (let [var-ids-by-code&name (select-var-ids-by-code&name db-spec)]
      (jdbc/with-db-transaction [db-tx db-spec]
        (doseq [[device-code var-value-by-code&name] device-state
                :let [rows (->> (dissoc var-value-by-code&name "Cas")
                                (keep (fn [[var-name value]]
                                        (if-let [var-id (get-in var-ids-by-code&name [device-code var-name])]
                                          (esc {:timestamp ts
                                                :variable-id var-id
                                                :value (pr-str value)})
                                          (do (timbre/error {:a ::variable-not-found :device-code device-code :var-name var-name})
                                              nil)))))]
                :when (seq rows)]
          (apply jdbc/insert! db-tx (esc :status-history) rows))))
    (timbre/error {:a ::save-device-state-without-timestamp :device-state device-state})))

(def status-history-limit 1000000)

(defn- select-status-history [db-spec var-group-id from to]
  (jdbc/query db-spec (cond-> [(str "SELECT * FROM " (esc :status-history)
                                              " WHERE \"timestamp\" >= ? AND \"timestamp\" < ? "
                                              (when var-group-id
                                                (str
                                                 " AND \"variable-id\" IN "
                                                 "  (SELECT " (esc :variable-id) " FROM " (esc :var-group-member)
                                                 "    WHERE " (esc :var-group-id) " = ?)"))
                                              #_" ORDER BY \"timestamp\""
                                              " LIMIT " status-history-limit)
                                         (some-> from (.getTime) (Timestamp.))
                                         (some-> to (.getTime) (Timestamp.))]
                                  var-group-id
                        (conj var-group-id))))

(defn select-plc-msg-history [db-spec from to]
  (jdbc/query db-spec [(str "SELECT * FROM " (esc :plc-msg-history)
                            " WHERE \"created\" >= ? AND \"created\" < ? "
                            " LIMIT " status-history-limit)
                       (some-> from (.getTime) (Timestamp.))
                       (some-> to (.getTime) (Timestamp.))]))

(defn select-alarm-history [db-spec from to]
  (jdbc/query db-spec [(str "SELECT * FROM " (esc :alarm-history)
                            " WHERE \"timestamp\" >= ? AND \"timestamp\" < ? "
                            " LIMIT " status-history-limit)
                       (some-> from (.getTime) (Timestamp.))
                       (some-> to (.getTime) (Timestamp.))]))

(defn select-device-states
  "Reads from status-history table from instant (incl) to instant (excl).
  When full-state? is true then the first value contains complete device state (all devices and its variables).
  Returns a vector containing changed device states values in maps per device-code and var-name.
  Timestamp of each item can be obtained calling (get-in ds [\"EB1\" \"Cas\"]) ."
  ([db-spec from to full-state?]
   (select-device-states db-spec nil from to full-state?))
  ([db-spec var-group-id from to full-state?]
   (let [status-history (select-status-history db-spec
                                               var-group-id
                                               (if full-state?
                                                 (read-var-last-change-timestamp db-spec from)
                                                 from)
                                               to)]
     (if (= (count status-history) status-history-limit)
       (do
         (timbre/error {:a ::status-history-count-limit-reached :msg "Cannot provide accurate results!"})
         {:error/msg "Nelze načíst plnou historii před 27.4.2017!"})
       (let [out (->> status-history
                      (group-by :timestamp)
                      (sort-by first)
                      (reduce (let [var-code&names-by-id (select-var-code&names-by-id db-spec)]
                                (fn [out [ts rows]]
                                  (let [ds (reduce (fn [out {:keys [timestamp variable-id value]}]
                                                     (assoc-in out (get var-code&names-by-id variable-id) (edn/read-string value)))
                                                   {"EB1" {"Cas" ts}}
                                                   rows)]
                                    (if (<= (.getTime ts)
                                            (.getTime from))
                                      [(merge-with merge (first out) ds)]
                                      (conj out ds)))))
                              []))]
         (timbre/info {:a ::device-states-selected :from from :to to :full-state? full-state? :count (count out)})
         out)))))

(defn device-states-to-csv
  "Creates a string in Comma Separated Values format (in fact, semicollons are used) of status-history variable values
  from requested variable group."
  [db-spec var-group-id date-from date-to]
  (let [var-group (first (common-db/select db-spec :var-group {:id var-group-id}))
        date-to (or date-to (Date.))
        var-code&names-by-id (select-var-code&names-by-id db-spec)
        rows (select-device-states db-spec var-group-id date-from date-to true)]
    (timbre/debug {:a ::device-states-to-csv :var-group var-group :from date-from :to date-to})
    (str "Timestamp;" (->> (:member-ids var-group)
                           (map #(str/join "/" (get var-code&names-by-id %)))
                           (str/join ";"))
         "\r\n"
         (->>
          rows
          (reduce (fn [out row]
                    (conj out (merge-with merge (peek out) row)))
                  [])
          (map (fn [row]
                 (str (cljc.util/to-format (get-in row ["EB1" "Cas"]) cljc.util/ddMMyyyyHHmmss)
                      ";"
                      (->> (:member-ids var-group)
                           (map #(get-in row (get var-code&names-by-id %)))
                           (str/join ";")))))
          (str/join "\r\n")))))

(defn write-transit-file [name data]
  (let [writer (transit/writer (io/make-output-stream (str "./" name ".tr") {:encoding "UTF-8"}) :json)]
    (transit/write writer data)))

(defn read-transit-file [name]
  (let [reader (transit/reader (io/make-input-stream (io/file (str "./" name ".tr")) {:encoding "UTF-8"}) :json)]
    (transit/read reader)))

(defn read-and-insert-status-history
  "Reads status history from input data created by
  (->> (select-device-states db-spec from to full-state?) (write-transit-file tr-file-name))
  and saves to DB."
  [tr-file-name db-spec]
  (jdbc/with-db-transaction [tx db-spec]
    (doseq [changes (read-transit-file tr-file-name)]
      (save-device-state tx changes))))

(defn download-device-states
  "Remote request for device states history"
  [hostname-port from-ms to-ms]
  (try
    (:body
     (client/post (str "http://" hostname-port "/api/device-states")
                  {:accept :transit+json
                   :socket-timeout 15000
                   :conn-timeout 15000
                   :form-params {:from-ms from-ms
                                 :to-ms to-ms}
                   :as :transit+json}))
    (catch Exception e
      (timbre/error {:a ::download-device-states-failed :ex-type (type e) :ex-msg (.getMessage e)}))))

(def max-sync-ms (* 60 60 1000)) ;; 1 hour

(defn- select-status-history-latest-timestamp [db-spec]
  (:max (first (jdbc/query db-spec ["SELECT MAX(\"timestamp\") FROM \"status-history\""]))))

(defn- get-timestamp [device-state]
  (get-in device-state ["EB1" "Cas"]))

(defn- device-states-by-timestamp [xs]
  (->> xs
       (map (juxt get-timestamp
                  identity))
       (into {})))

(def google-docs-url "https://docs.google.com/spreadsheets/d/1s7HtaX55JaZit8PcjMpybgsCN5XoEAUj0NWkiBtXkfY/export?format=xlsx")

(defn config-refresh [db-spec user-login]
  (let [msg (import-xlsx/download-and-import db-spec user-login google-docs-url)]
    (reset! dev&var-cache {})
    msg))

(defn config-rollback [db-spec last-id]
  (import-xlsx/rollback-last db-spec last-id)
  (reset! dev&var-cache {}))

(defn- update-config-if-older [db-spec remote-config-date]
  (let [local-config-date (import-xlsx/last-config-import-date)]
    (when (or (not local-config-date)
              (and remote-config-date
                   (> (.getTime remote-config-date)
                      (.getTime local-config-date))))
      (config-refresh db-spec nil))))

(def ebsserver-elevize "localhost:3001")
(def ebsoperator-elevize "localhost:3031")

(defn- get-merged-device-states
  "Fetches device state history from ebsserver and ebsoperator and merges them.
  Returns nil when any of the requests fails to return data."
  [from-ms to-ms]
  (when-let [from-server (download-device-states ebsserver-elevize from-ms to-ms)]
    (when-let [from-operator (download-device-states ebsoperator-elevize from-ms to-ms)]
      (assoc from-server
             :device-states (->> (device-states-by-timestamp (:device-states from-server))
                                 (merge (device-states-by-timestamp (:device-states from-operator)))
                                 (vals)
                                 (sort-by get-timestamp))
             :plc-msg-history (into (:plc-msg-history from-server) (:plc-msg-history from-operator))
             :alarm-history (into (set (:alarm-history from-server)) (:alarm-history from-operator)) ))))

(defn sync-device-states-history
  "Saves rows returned by merged device state.
   When there is no data in the given period, tries to read the next one until now."
  [db-spec]
  (loop [from-ms (inc (.getTime (select-status-history-latest-timestamp db-spec)))
         to-ms (+ from-ms max-sync-ms)]
    (when-let [{:keys [device-states last-config-import-date plc-msg-history alarm-history]} (get-merged-device-states from-ms to-ms)]
      (cond
        (seq device-states)
        (do
          (update-config-if-older db-spec last-config-import-date)
          (jdbc/with-db-transaction [db-tx db-spec]
            (doseq [ds device-states]
              (save-device-state db-tx ds))
            (when (seq plc-msg-history)
              (apply jdbc/insert! db-tx (esc :plc-msg-history) (map #(-> %
                                                                         (update :created (fn [x] (-> x (.getTime) (Timestamp.))))
                                                                         (esc))
                                                                    plc-msg-history)))
            (when (seq alarm-history)
              (apply jdbc/insert! db-tx (esc :alarm-history) (map #(-> %
                                                                       (update :timestamp (fn [x] (-> x (.getTime) (Timestamp.))))
                                                                       (esc))
                                                                  alarm-history))))
          (timbre/info {:a ::device-states-history-synced :from (Date. from-ms) :to (Date. to-ms) :ds-count (count device-states) :alarm-count (count alarm-history) :plc-msg-count (count plc-msg-history)}))
        (< to-ms (.getTime (Date.)))
        (recur to-ms (+ to-ms max-sync-ms))))))

#_(defn- fill-status-history2 [db-spec]
    (loop [id 0]
      (jdbc/with-db-transaction [tx db-spec]
        (let [rows (->> (jdbc/query tx [(str "select * from " (esc :status-history)
                                             " where id > ?"
                                             " order by id"
                                             " limit 100") id])
                        (map (fn [row]
                               (let [v (edn/read-string (:value row))]
                                 (merge (-> row
                                            (dissoc :value)
                                            (update :timestamp #(Timestamp. (.getTime %))))
                                        (cond
                                          (integer? v)
                                          {:v-int v}
                                          (float? v)
                                          {:v-real v}
                                          (instance? Date v)
                                          {:v-inst (Timestamp. (.getTime v))}
                                          (or (true? v) (false? v))
                                          {:v-bool v}
                                          (string? v)
                                          {:v-str v}))))))]
          (when (seq rows)
            (try
              (apply jdbc/insert! tx (esc :status-history2) (map esc rows))
              (catch Exception e
                (pprint rows)
                (throw e)))
            (recur (:id (last rows))))))))

(defn get-s-weight [ip-addr]
  (try
    (as->
     (client/get (str "http://" ip-addr "/ValPoids.cgx")
                 {:socket-timeout 2000
                  :conn-timeout 2000}) $
      (:body $)
     (re-find #"(\-?\s*\d*) kg" $)
     (second $)
     (str/replace $ #"\s*" "")
     (edn/read-string $))
    (catch Exception e
      (timbre/error {:a ::get-s-weight-failed :ex-type (type e) :ex-msg (.getMessage e)}))))

(def scale1-hostname "192.168.33.230")
(def scale2-hostname "192.168.33.231")

(defn get-silo-weights
  "Makes HTTP request to silo scales"
  []
  {:s1 (get-s-weight scale1-hostname)
   :s2 (get-s-weight scale2-hostname)})
