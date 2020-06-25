(ns elevize.db.common-db
  (:require [clojure.edn :as edn]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [crypto.password.scrypt :as scrypt]
            [taoensso.timbre :as timbre])
  (:import [java.sql SQLException Timestamp]))

(defn esc
  "Prevadi (escapuje) nazvy DB tabulek a sloupcu z keywordu do stringu ohraniceneho uvozovkami.
   Diky tomu muzeme mit nazvy tabulek a sloupcu v Clojure tvaru s pomlckou.
   Umi prevadet samostatne keywordy, mapy s keywordovymi klici (rows) a taky keywordy v retezcich - sql dotazech,
   kde navic retezce umi spojujit, takze neni potreba pouzit (str)"
  ([to-esc]
   (cond
     (keyword? to-esc)
     (format "\"%s\"" (str (when-let [n (namespace to-esc)]
                             (str n "/"))
                           (name to-esc)))
     (string? to-esc)
     (format "\"%s\"" to-esc)
     (map? to-esc)
     (->> to-esc
          (map (fn [[k v]]
                 [(esc k) v]))
          (into {}))
     :default
     (esc to-esc "")))
  ([s & ss]
   (str/replace (str s (apply str ss)) #":([a-z0-9\-]+)" "\"$1\"")))

(defn where
  ([where-m]
   (where "" where-m))
  ([prefix where-m]
   (let [prefix (if (str/blank? prefix) "" (str prefix "."))]
     (->> where-m
          (map (fn [[k v]] (str "AND " prefix (esc k)
                                (if (coll? v)
                                  (str " IN (" (str/join "," (take (count v) (repeat "?"))) ")")
                                  " = ?"))))
          (into [" WHERE 1=1"])
          (str/join " ")))))

(def limit-max-rows 4000)

(defn default-select
  ([db-spec table-kw where-m] (default-select db-spec table-kw where-m limit-max-rows))
  ([db-spec table-kw where-m max-rows]
   (let [out (jdbc/query db-spec (into [(str "SELECT * FROM " (esc table-kw) (where where-m) " ORDER BY \"id\" DESC LIMIT " max-rows)]
                                       (flatten (vals where-m))))]
     (timbre/debug {:a ::select :table table-kw :where-m where-m :results (count out)})
     out)))

(defmulti select (fn [_ table-kw _] table-kw))

(defmethod select :default [db-spec table-kw where-m]
  (default-select db-spec table-kw where-m))

(defmethod select :device [db-spec table-kw where-m]
  (->> (default-select db-spec table-kw where-m)
       (remove (comp #{"OH1" "OH2" "VTRCH1" "TOS_O1" "TOS_O2" "TOS_P1" "TOS_P2" "TE1" "TE2" "VV1" "VV2"} :code))))

(defmethod select :var-group [db-spec table-kw where-m]
  (->> (default-select db-spec table-kw where-m)
       (map (let [members (->> (select db-spec :var-group-member {})
                               (group-by :var-group-id))]
              #(-> %
                   (assoc :member-ids (map :variable-id (get members (:id %)))))))))

(defmethod select :user [db-spec table-kw where-m]
  (->> (default-select db-spec table-kw where-m)
       (map #(dissoc % :passwd))))

(defmethod select :status-history [db-spec table-kw where-m]
  (->> (jdbc/query db-spec (into [(str "SELECT * FROM " (esc table-kw) (where where-m) " ORDER BY \"timestamp\" DESC LIMIT " limit-max-rows)]
                                 (flatten (vals where-m))))
       (map #(update % :value edn/read-string))))

(defn insert! [db-spec table-kw row]
  (when-not (= table-kw :status-history)
    (timbre/debug {:a ::insert :table table-kw :row row}))
  (first
   (jdbc/insert! db-spec
                 (esc table-kw)
                 (esc row))))

(defn update! [db-spec table-kw row]
  (timbre/debug {:a ::update :table table-kw :row row})
  (jdbc/update! db-spec
                (esc table-kw)
                (esc row)
                ["\"id\" = ?" (:id row)])
  row)

(defmulti save! (fn [_ table-kw _] table-kw))

(defn default-save! [db-spec table-kw row]
  (try
    (if (:id row)
      (update! db-spec table-kw row)
      (insert! db-spec table-kw row))
    (catch SQLException e
      (timbre/error {:a ::db-save-error :ex-info (ex-info "Chyba při ukládání záznamu do DB" {:row row} e)})
      (throw e))))

(defmethod save! :default [db-spec table-kw row]
  (default-save! db-spec table-kw row))

(defmethod save! :var-group [db-spec table-kw row]
  (jdbc/with-db-transaction [tx db-spec]
    (let [var-group (default-save! tx table-kw (dissoc row :member-ids))]
      (jdbc/delete! tx (esc :var-group-member) ["\"var-group-id\" = ?" (:id var-group)])
      (doseq [[idx var-id] (map-indexed vector (:member-ids row))]
        (save! tx :var-group-member {:var-group-id (:id var-group)
                                     :variable-id var-id
                                     :pos idx}))
      (first (select tx table-kw {:id (:id var-group)})))))

(defmethod save! :user [db-spec table-kw row]
  (default-save! db-spec table-kw (if-not (str/blank? (:passwd row))
                                    (assoc row :passwd (scrypt/encrypt (:passwd row)))
                                    (dissoc row :passwd))))

(defmethod save! :status-history [db-spec table-kw row]
  (default-save! db-spec table-kw (-> row
                                      (update :timestamp #(when % (Timestamp. (.getTime %))))
                                      (update :value pr-str))))

(defn delete! [db-spec table-kw id]
  (timbre/debug {:a ::delete :table table-kw :id id})
  (jdbc/delete! db-spec (esc table-kw) ["\"id\" = ?" id]))
