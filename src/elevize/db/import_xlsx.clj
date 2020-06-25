(ns elevize.db.import-xlsx
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [clojure.pprint :refer [pprint]]
            [clojure.set :as set]
            [clojure.string :as str]
            [dk.ative.docjure.spreadsheet :as xls]
            [elevize.cljc.util :as cljc.util]
            [elevize.db.common-db :refer [select insert! update!]]
            [taoensso.timbre :as timbre])
  (:import java.net.URL
           java.util.Date))

;; --- import of SUBSYSTEMs -----------------------------------------
(def subsystems-and-devices-sheet-name "Subsystémy a zařízení")

(defn- read-subsystems-and-devices-from-xlsx [workbook]
  (let [out (->> workbook
                 (xls/select-sheet subsystems-and-devices-sheet-name)
                 (xls/select-columns {:A :subsystem-code
                                      :B :subsystem-title
                                      :C :device-num
                                      :D :device-code
                                      :E :device-title}))]
    (if (not= (first out) {:subsystem-code "ID subsystému"
                           :subsystem-title "Název subsystému"
                           :device-num "Číslo zařízení"
                           :device-code "ID zařízení"
                           :device-title "Název zařízení"})
      (do
        (timbre/error {:a ::read-subsystems-and-devices :msg (str "Chybná hlavička listu " subsystems-and-devices-sheet-name)})
        [])
      (subvec out 1))))

(defn- remap-subsystem [m]
  (-> m
      (select-keys [:subsystem-title :subsystem-code])
      (set/rename-keys {:subsystem-title :title
                        :subsystem-code :code})))

(defn- update-subsystems [db-spec subsystems]
  (doseq [row subsystems]
    (if-let [id (-> (select db-spec :subsystem (select-keys row [:code]))
                    (first)
                    :id)]
      (update! db-spec :subsystem (assoc row :id id))
      (insert! db-spec :subsystem row))))

(defn- import-subsystems [db-spec wb]
  (->> (read-subsystems-and-devices-from-xlsx wb)
       (filter :subsystem-title)
       (map remap-subsystem)
       (update-subsystems db-spec)))

;; --- import of DEVICEs -----------------------------------------
(defn- remap-device [subs-code->id m]
  (-> m
      (select-keys [:device-num :device-title :device-code])
      (set/rename-keys {:device-title :title
                        :device-code :code})
      (assoc :subsystem-id (get subs-code->id (:subsystem-code m)))))

(defn- update-devices [db-spec devices]
  (doseq [row devices]
    (if-not (:subsystem-id row)
      (timbre/error {:a ::update-devices :msg (str "Nelze zapsat zařízení bez subsystému" row)})
      (if-let [id (-> (select db-spec :device (select-keys row [:code]))
                      first
                      :id)]
        (update! db-spec :device (assoc row :id id))
        (insert! db-spec :device row)))))

(defn- import-devices [db-spec wb]
  (->> (read-subsystems-and-devices-from-xlsx wb)
       (map (partial remap-device
                     (->> (select db-spec :subsystem {})
                          (map (juxt :code :id))
                          (into {}))))
       (update-devices db-spec)))

;; --- import of VARIABLEs -----------------------------------------
(defn- variables-sheet-header [device-count]
  (merge {:A :name
          :B :data-type
          :F :set-name}
         (zipmap
          (->> (int \G)
               (iterate inc)
               (map (comp keyword str char))
               (take (inc device-count)))
          (conj (->> (range device-count)
                     (mapv (comp keyword
                                 (partial str "kks")
                                 inc)))
                :comment))))

(defn- read-vars-from-xlsx-sheet [sheet device-count]
  (let [out (xls/select-columns (variables-sheet-header device-count)
                                 sheet)]
    (if (not= (:name (first out)) (xls/sheet-name sheet))
      (throw (Exception. (str "Chybná hlavička listu " (xls/sheet-name sheet))))
      (->> out
           (rest)
           (filter :name)))))

(defn- update-variables [db-spec vars]
  (doseq [row vars]
    (if-let [id (-> (select db-spec :variable (select-keys row [:name :device-id]))
                    first
                    :id)]
      (update! db-spec :variable (assoc row :id id))
      (insert! db-spec :variable row))))

(defn- remap-variables [device m]
  (-> m
      (select-keys [:name :data-type :set-name :comment])
      (assoc :device-id (:id device)
             :kks (get m (keyword (str "kks" (:device-num device)))))))

(defn- import-variables [db-spec wb]
  (doseq [subs (select db-spec :subsystem {})]
    (if-let [sheet (xls/select-sheet (:title subs) wb)]
      (let [subs-devices (select db-spec :device {:subsystem-id (:id subs)})
            vars (read-vars-from-xlsx-sheet sheet (count subs-devices))
            var-header (str/join "\n" (map :name vars))]

        (update-devices db-spec (map #(assoc % :var-header var-header)
                                     subs-devices))
        (doseq [device subs-devices]
          (->> vars
               (map (partial remap-variables device))
               (update-variables db-spec))))
      (timbre/error {:a ::import-variables :msg (str "Nenalezen list proměnných subsystému " (:titleb subs))}))))

(def enums-sheet-name "ENUMY")

(defn- import-enums [db-spec wb]
  (if-let [sheet (xls/select-sheet enums-sheet-name wb)]
    (let [rows (xls/select-columns {:A :name
                                    :B :order-pos
                                    :C :label}
                                   sheet)
          group-name (atom nil)]
      (doseq [row rows]
        (when (:name row)
          (if-not (:order-pos row)
            (reset! group-name (:name row))
            (if-let [old (first (select db-spec :enum-item {:group-name @group-name :order-pos (:order-pos row)}))]
              (update! db-spec :enum-item (assoc row :id (:id old)))
              (insert! db-spec :enum-item (assoc row :group-name @group-name)))))))
    (timbre/error {:a ::import-enums :msg (str "Nenalezen list " enums-sheet-name)})))

(defn import-db-from-xlsx
  ([db-spec] (import-db-from-xlsx db-spec "VzdalenaSprava.xlsx"))
  ([db-spec xlsx-name]
   (let [wb (xls/load-workbook xlsx-name)]
     (jdbc/with-db-transaction [db-tx db-spec]
       (import-subsystems db-tx wb)
       (import-devices db-tx wb)
       (import-variables db-tx wb)
       (import-enums db-tx wb)
       "ok"))))

(defn- filename->ent [filename]
  (-> (zipmap [:id :user-login :date]
              (str/split filename #"[_\.]"))
      (update :id edn/read-string)
      (update :date #(cljc.util/from-format % cljc.util/yyyyMMdd-HHmmss))))

(defn- ent->filename [ent]
  (str (:id ent) "_" (:user-login ent) "_" (cljc.util/to-format (:date ent) cljc.util/yyyyMMdd-HHmmss) ".xlsx"))

(def imports-dir "./config_imports/")

(defn select-history []
  (->> (io/file imports-dir)
       (.list)
       (map filename->ent)
       (sort-by :id)))

(defn- new-file-name [user-login timestamp]
  (str imports-dir
       (-> (last (select-history))
           (update :id #(if % (inc %) 1))
           (assoc :user-login user-login
                  :date timestamp)
           (ent->filename))))

(defn- download-config-from-url [url new-filename]
  (io/make-parents new-filename)
  (with-open [in (.openStream (URL. url))
              out (io/output-stream new-filename)]
    (io/copy in out)))

(defn download-and-import [db-spec user-login url]
  (let [new-filename (new-file-name user-login (Date.))]
    (try
      (download-config-from-url url new-file-name)
      (import-db-from-xlsx db-spec new-filename)
      (catch Exception e
        (timbre/error {:a ::download-and-import-xlsx-error :ex-info (ex-info "Chyba při stahování nebo importu XLSX" {} e)})
        (str (type e) ": " (.getMessage e))))))

(defn rollback-last [db-spec last-id]
  (let [imports (select-history)]
    (cond
      (= (count imports) 1)
      {:error/msg "Nelze odstranit jediný import!"}
      (not= last-id (:id (last imports)))
      {:error/msg (str "Import s ID " last-id " není posledním importem!")}
      :else
      (let [last-filename (str imports-dir (ent->filename (last imports)))
            prev-filename (str imports-dir (ent->filename (last (butlast imports))))
            out (import-db-from-xlsx db-spec prev-filename)]
        (timbre/info {:a ::rollback-last :msg (str "Removing " last-filename)})
        (.delete (io/file last-filename))
        out))))

(defn last-config-import-date []
  (:date (last (select-history))))
