(ns elevize.db.analysis
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [elevize.db.common-db :as common-db :refer [esc]]
            [elevize.db.service :as service]
            [taoensso.timbre :as timbre]
            [clojure.set :as set])
  (:import [java.sql SQLException Timestamp]
           java.util.Date))

(def psql-data-types {"INT" "SMALLINT"
                      "UDINT" "INTEGER"
                      "ULINT" "INTEGER"
                      "BOOL" "BOOLEAN"
                      "WORD" "SMALLINT"
                      "REAL" "REAL"
                      "DATE_AND_TIME" "TIMESTAMP"
                      "STRING[15]" "VARCHAR(15)"
                      "STRING[20]" "VARCHAR(20)"
                      "STRING[256]" "VARCHAR(256)"
                      "STRING[1986]" "VARCHAR(1986)"})

(defn gen-all-vars-create-table [db-spec]
  (let [var-ids-by-code&name (service/select-var-ids-by-code&name db-spec)
        vars-by-id (->> (common-db/select db-spec :variable {})
                        (map (juxt :id identity))
                        (into {}))]
    (str
     (str/join "\n"
               (for [[device-code var-ids-by-name] var-ids-by-code&name]
                 (str "DROP TABLE IF EXISTS " (esc (str "device-history-" device-code)) ";")))
     "\n\n"
     (str/join "\n"
               (for [[device-code var-ids-by-name] var-ids-by-code&name]
                 (str "CREATE TABLE " (esc (str "device-history-" device-code)) "(\n \"id\" TIMESTAMP PRIMARY KEY,\n "
                      (str/join ",\n "
                                (for [[var-name var-id] var-ids-by-name
                                      :let [var (get vars-by-id var-id)]
                                      :when (and (nil? (:set-name var))
                                                 #_(str/starts-with? (:name var) "IO_"))]
                                  (str (esc var-name) " " (get psql-data-types (:data-type var) "SMALLINT")
                                       #_" NOT NULL")))
                      ");\n"))))))

(def min-analysis-temperature 50)

#_(defn- fill-device-state-history
  [db-spec var-group-id date-from date-to]
  (let [var-group (first (common-db/select db-spec :var-group {:id var-group-id}))
        date-to (or date-to (Date.))
        rows (->> (service/select-device-states db-spec var-group-id date-from date-to true)
                  (reduce (fn [out changes]
                            (conj out (merge-with merge (peek out) changes)))
                          [])
                  (filter #(or (>= (get-in % ["TE3" "IO_Teplota"]) min-analysis-temperature)
                               (>= (get-in % ["TE4" "IO_Teplota"]) min-analysis-temperature)))
                  (map (fn [device-states]
                         (reduce (fn [out [device-code xs]]
                                   (->> xs
                                        (map (fn [[var-name value]]
                                               (let [key (keyword device-code var-name)
                                                     key (if (= key :EB1/Cas) :id key)]
                                                 [key value])))
                                        (into out)))
                                 {}
                                 device-states))))]
    (timbre/debug {:a ::filling-device-state-history :var-group var-group :from date-from :to date-to :rows (count rows)})
    (when (seq rows)
      (jdbc/with-db-transaction [tx db-spec]
        (apply jdbc/insert! tx (esc :device-state-history) (map esc rows))))
    (count rows)))

(defn- fill-device-state-history
  [db-spec date-from date-to]
  (let [date-to (or date-to (Date.))
        states (->> (service/select-device-states db-spec date-from date-to true)
                    (reduce (fn [out changes]
                              (conj out (merge-with merge (peek out) changes)))
                            [])
                    (filter #(or (>= (get-in % ["TE3" "IO_Teplota"]) min-analysis-temperature)
                                 (>= (get-in % ["TE4" "IO_Teplota"]) min-analysis-temperature))))]
    (timbre/debug {:a ::filling-device-state-history :from date-from :to date-to :rows (count states)})
    (when (seq states)
      (doseq [state states]
        (jdbc/with-db-transaction [tx db-spec]
          (doseq [[device-code values] state
                  :let [row (esc (assoc values "id" (get-in state ["EB1" "Cas"])))]]
            (try
              (jdbc/insert! tx (esc (str "device-history-" device-code)) row)
              (catch Exception e
                (pprint row)
                (throw e)))))))
    (count states)))
