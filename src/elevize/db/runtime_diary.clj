(ns elevize.db.runtime-diary
  (:require [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clojure.java.jdbc :as jdbc]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [elevize.cljc.calc-derived :as cljc.calc-derived]
            [elevize.cljc.util :as cljc.util]
            [elevize.db.common-db :as common-db :refer [esc]]
            [elevize.db.service :as service]
            [taoensso.timbre :as timbre])
  (:import [java.sql SQLException Timestamp]
           java.util.Date))

(comment
  (def xs (select-device-states (user/db-spec) #inst"2017-06-20T22:00:00" #inst"2017-06-23T22:00:00" true))
  (def fxs (reduce (fn [out row]
                     (conj out (merge-with merge (peek out) row)))
                   []
                   xs))
  (def fxs100 (filter #(>= (get-in % ["TE4" "IO_Teplota"]) 100) fxs))
  (count fxs100)
  (reduce max 0 (map #(get-in % ["TE4" "IO_Teplota"]) fxs100))
  (reduce + 0 (map #(get-in % ["TE4" "IO_Teplota"]) fxs100))
  (/ *1 (count fxs100) 1.0)
  (reduce #(max %1 (.getTime %2)) 0 (map #(get-in % ["EB1" "Cas"]) fxs100))
  (java.util.Date. *1)
  (reduce + 0 (map #(get-in % ["SVO1" "IO_TeplotaKomin"]) fxs100))
  (/ *1 (count fxs100) 1.0)
  (reduce + 0 (map #(:hm-prutok-kg-s (get-in elevize.cljc.util/default-fuel-table ["TE4" (Math/round (* 1.0 (get-in % ["TE4" "IO_PalivoFreqAct"])))]
                                             {:hm-prutok-kg-s 0
                                              :tepelny-vykon 0})) fxs100))
  (/ *1 (count fxs100) 1.0)
  (def millis (- (.getTime (get-in (last fxs100) ["EB1" "Cas"])) (.getTime (get-in (first fxs100) ["EB1" "Cas"]))))
  (* 0.016695183644346342 millis 0.001)
  )

(def device-codes ["TE3" "TE4" "OH2"])
(def teplota-konce-behu 50)
(def teplota-zacatku-behu 70)

(defn- truncate-runtime-diary [db-spec]
  (jdbc/delete! db-spec (esc :runtime-diary) ["1 = 1"]))

(defn calculate-runtime-diary [db-spec from]
  (let [;;dss-db-spec {:connection-uri "jdbc:postgresql://192.168.0.5/elevize?user=elevize&password=elepwd"}
        dss (->> from
                 (tc/to-local-date)
                 (iterate #(t/plus % (t/days 1)))
                 (take-while #(not (t/after? % (t/today))))
                 (mapcat (fn [ld]
                           (let [cz-day-start (cljc.util/from-format (cljc.util/to-format (tc/to-date ld) cljc.util/ddMMyyyy) cljc.util/ddMMyyyy)
                                 cz-day-end (cljc.util/with-date cz-day-start #(-> %
                                                                                   (t/plus (t/hours 24))
                                                                                   #_(t/plus (t/seconds 10))))]
                             (->> (service/select-device-states db-spec cz-day-start cz-day-end true)
                                  (reduce (fn [out row]
                                            (conj out (merge-with merge
                                                                  (or (peek out)
                                                                      {:cz-day-start cz-day-start
                                                                       :cz-day-end cz-day-end})
                                                                  row)))
                                          []))))))
        calc-data-ref (atom {})
        calc-data-watch-fn (fn [_ _ ov nv]
                             (doseq [device-code device-codes
                                     :let [odd (get ov device-code)
                                           ndd (get nv device-code)
                                           te-device? (str/starts-with? device-code "TE")]]

                               (when (and (:running-to ndd)
                                          (not (:running-to odd))) ;; save running period data
                                 (let [runtime-secs (/ (- (.getTime (:running-to ndd))
                                                          (.getTime (:running-from odd)))
                                                       1000)
                                       row {:device-code device-code
                                            :daily-rec? false
                                            :from (Timestamp. (.getTime (:running-from odd)))
                                            :to (Timestamp. (.getTime (:running-to ndd)))
                                            :runtime-mins (int (/ runtime-secs 60))
                                            :T-max (:running-T-max odd)
                                            :T-avg (when (:running-T-sum odd)
                                                     (int
                                                      (/ (:running-T-sum odd)
                                                         (:running-n odd))))
                                            :T-avg-exh (when (> (:running-T-exh-sum odd) 0)
                                                         (int
                                                          (/ (:running-T-exh-sum odd)
                                                             (:running-n odd))))
                                            :fuel-kg (when (> (:running-fuel-flow-kg-s-sum odd) 0)
                                                       (int
                                                        (* (/ (:running-fuel-flow-kg-s-sum odd)
                                                              (:running-n odd))
                                                           runtime-secs)))}]
                                   (timbre/info {:a ::saving-runtime-diary-record :row row})
                                   (common-db/save! db-spec :runtime-diary row)))

                               (when (and (:cz-day-start odd)
                                          (not= (:cz-day-start odd) (:cz-day-start ndd))
                                          (> (:daily-running-n odd) 0)) ;; save running daily data
                                 (let [row {:device-code device-code
                                            :daily-rec? true
                                            :from (Timestamp. (.getTime (:cz-day-start odd)))
                                            :to (Timestamp. (.getTime (:cz-day-end odd)))
                                            :runtime-mins (int
                                                           (* 24 60 (/ (:daily-running-n odd)
                                                                       (:daily-n odd))))
                                            :turbine-runtime-mins (when (> (:daily-turbine-running-n odd) 0)
                                                                    (int
                                                                     (* 24 60 (/ (:daily-turbine-running-n odd)
                                                                                 (:daily-n odd)))))
                                            :T-max (:daily-T-max odd)
                                            :T-avg (when (> (:daily-running-T-sum odd) 0)
                                                     (int
                                                      (/ (:daily-running-T-sum odd)
                                                         (:daily-running-n odd))))
                                            :T-avg-exh (when (> (:daily-running-T-exh-sum odd) 0)
                                                         (int
                                                          (/ (:daily-running-T-exh-sum odd)
                                                             (:daily-running-n odd))))
                                            :fuel-kg (when (> (:daily-fuel-flow-kg-s-sum odd) 0)
                                                       (int
                                                        (* (/ (:daily-fuel-flow-kg-s-sum odd)
                                                              (:daily-n odd))
                                                           (* 24 60 60))))
                                            :scale1-kg (when te-device?
                                                         (let [delta (- (:scale1-kg odd) (:scale1-kg ndd))]
                                                           (when (> delta 0)
                                                             delta)))
                                            :scale2-kg (when te-device?
                                                         (let [delta (- (:scale2-kg odd) (:scale2-kg ndd))]
                                                           (when (> delta 0)
                                                             delta)))}]
                                   (timbre/info {:a ::saving-runtime-diary-record :row row})
                                   (common-db/save! db-spec :runtime-diary row)))))
        fuel-flow-fn (fn [device-code ds]
                       (or (:hm-prutok-kg-s
                            (get-in cljc.calc-derived/default-fuel-table
                                    [device-code (int (get-in ds [device-code "IO_PalivoFreqAct"] 0))]))
                           0))
        previous-time (atom nil)]
    (add-watch calc-data-ref :calc-data-change calc-data-watch-fn)
    (doseq [ds dss
            :let [cas (get-in ds ["EB1" "Cas"])
                  diff-millis (some->> @previous-time (.getTime) (- (.getTime cas)))]]
      (reset! previous-time cas)
      (swap! calc-data-ref
             (fn [calc-data]
               (reduce (fn [out device-code]
                         (let [oh-device? (str/starts-with? device-code "OH")
                               T (or (get-in ds [device-code (if oh-device? "IO_TeplotaOleje" "IO_Teplota")])
                                     -1)
                               turbine-rotations (or (get-in ds [device-code "IO_SnimacOtacek1"])
                                                     (get-in ds [device-code "IO_SnimacOtacek2"])
                                                     (get-in ds [device-code "IO_SnimacOtacek3"])
                                                     -1)
                               te-device? (str/starts-with? device-code "TE")
                               auto? (not (cljc.util/bool (get-in ds [device-code "Manual"])))
                               rezim-auto (get-in ds [device-code "RezimAuto"] 0)]
                           (update out device-code
                                   (fn [device-data]
                                     (cond-> device-data

                                       ;; zacatek behu
                                       (and (not (:running-from device-data))
                                            (or
                                             (and auto? (#{1 2} rezim-auto))
                                             (and (not auto?)
                                                  (or
                                                   (and te-device?
                                                        (>= T teplota-zacatku-behu)
                                                        (> (get-in ds [device-code "IO_PalivoFreqAct"] 0) 0))
                                                   (and oh-device?
                                                        (> (get-in ds [device-code "IO_TlakOleje"] 0) 0))))))
                                       (->
                                        (assoc :running-from cas
                                               :running-to nil
                                               :running-T-max -220
                                               :running-T-sum 0
                                               :running-T-exh-sum 0
                                               :running-fuel-flow-kg-s-sum 0
                                               :running-n 1)
                                        #_(#(do
                                              (println "zacatek" device-code cas auto? rezim-auto (type rezim-auto) T)
                                              %)))

                                       ;; konec behu
                                       (and (:running-from device-data)
                                            (not (:running-to device-data))
                                            (or
                                             (and auto? (zero? rezim-auto))
                                             (and (not auto?)
                                                  (or
                                                   (and te-device?
                                                        (<= T (get-in ds [device-code "OdstavitPri"]
                                                                      teplota-konce-behu)))
                                                   (and oh-device?
                                                        (zero? (get-in ds [device-code "IO_TlakOleje"] 0)))))))
                                       (-> (assoc :running-from nil
                                                  :running-to cas)
                                           #_(#(do
                                               (println "konec" device-code cas auto? rezim-auto (type rezim-auto) T)
                                               %)))

                                       ;; beh
                                       (and (:running-from device-data)
                                            (not (:running-to device-data)))
                                       (-> (update :running-T-max #(let [T T]
                                                                     (if (> T %) T %)))
                                           (update :running-T-sum + (* T diff-millis))
                                           (update :running-T-exh-sum + (* (get-in ds ["SVO1" "IO_TeplotaKomin"] 0) diff-millis))
                                           (update :running-fuel-flow-kg-s-sum + (* (fuel-flow-fn device-code ds) diff-millis))
                                           (update :running-n + diff-millis))

                                       ;; zacatek/konec dne
                                       (not= (:cz-day-start device-data) (:cz-day-start ds))
                                       (-> (assoc :cz-day-start (:cz-day-start ds)
                                                  :cz-day-end (:cz-day-end ds)
                                                  :daily-T-max -220
                                                  :daily-running-T-sum 0
                                                  :daily-running-T-exh-sum 0
                                                  :daily-fuel-flow-kg-s-sum 0
                                                  :daily-running-n 0
                                                  :daily-n 0
                                                  :daily-turbine-running-n 0
                                                  :scale1-kg (get-in ds ["PP1" "IO_HmotnostVstup1"] 0)
                                                  :scale2-kg (get-in ds ["PP1" "IO_HmotnostVstup2"] 0)))

                                       ;; bezi ve dni
                                       (and (:running-from device-data)
                                            (= (:cz-day-start device-data) (:cz-day-start ds)))
                                       (-> (update :daily-running-T-sum + (* T diff-millis))
                                           (update :daily-running-T-exh-sum + (* (get-in ds ["SVO1" "IO_TeplotaKomin"] 0) diff-millis))
                                           (update :daily-running-n + diff-millis))

                                       ;; ve dni (bez ohledu na beh)
                                       (and (= (:cz-day-start device-data) (:cz-day-start ds)))
                                       (-> (update :daily-T-max #(if (some-> T (> %)) T %))
                                           (update :daily-fuel-flow-kg-s-sum + (* (fuel-flow-fn device-code ds) diff-millis))
                                           (update :daily-n + diff-millis)
                                           (cond->
                                               (> turbine-rotations 0)
                                             (update :daily-turbine-running-n + diff-millis))))))))
                       calc-data
                       device-codes))))))
