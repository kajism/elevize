(ns elevize.cljc.util
  #?@(:clj
       [(:require
         [clj-time.coerce :as tc]
         [clj-time.core :as t]
         [clj-time.format :as tf]
         [clojure.edn :as edn]
         [clojure.string :as str]
         [taoensso.timbre :as timbre])
        (:import java.lang.IllegalArgumentException)]
       :cljs
       [(:require
         [cljs-time.coerce :as tc]
         [cljs-time.core :as t]
         [cljs-time.format :as tf]
         [cljs.tools.reader.edn :as edn]
         [clojure.string :as str]
         [taoensso.timbre :as timbre])]))

(def plc-date-regex #"^\d{4}-\d{2}-\d{2}-\d{2}:\d{2}:\d{2}\.\d+")
(def formatter-plc (tf/formatter "yyyy-MM-dd-HH:mm:ss.SSS"))

(defn more-than-time-change? [vars]
  (not
   (and (= (count vars) 1)
        (= "Cas" (-> vars ffirst)))))

(defn plc-time [s]
  (when-not (str/blank? s)
    (->> (subs s 0 23)
         (tf/parse formatter-plc)
         (tc/to-date))))

(defn parse-plc-value [s]
  (when s
    (cond
      (re-find #"(?i)[a-df-z~]" s)
      s
      (re-find plc-date-regex s)
      (plc-time s)
      :else
      #?(:clj (try
                (edn/read-string s)
                (catch IllegalArgumentException e
                  (timbre/warn {:a ::read-string-exception :msg (.getMessage e)})
                  s))
         :cljs (edn/read-string s)))))

(defn find-variables-history [device-states-history paths]
  (->> device-states-history
       (map (fn [device-state]
              (->> (map #(get-in device-state %) paths)
                   (into [(get-in device-state ["EB1" "Cas"])]))))))

(defn bool [x]
  (= x 1)
  #_(boolean
   (when (some? x)
     (or (true? x) (= "1" (str x)) (= "true" (str/lower-case (str x)))))))

#?(:clj (def clj-tz (t/time-zone-for-id "Europe/Prague")))

(def dMyyyy (tf/formatter "d.M.yyyy" #?(:clj clj-tz)))
(def ddMMyyyy (tf/formatter "dd.MM.yyyy" #?(:clj clj-tz)))
(def dMyyyyHmmss (tf/formatter "d.M.yyyy H:mm:ss" #?(:clj clj-tz)))
(def ddMMyyyyHHmmss (tf/formatter "dd.MM.yyyy HH:mm:ss" #?(:clj clj-tz)))
(def ddMMyyyyHHmm (tf/formatter "dd.MM.yyyy HH:mm" #?(:clj clj-tz)))
(def dMyyyyHmm (tf/formatter "d.M.yyyy H:mm" #?(:clj clj-tz)))
(def yyyyMMdd-HHmm (tf/formatter "yyyyMMdd-HHmm" #?(:clj clj-tz)))
(def yyyyMMdd-HHmmss (tf/formatter "yyyyMMdd-HHmmss" #?(:clj clj-tz)))
(def HHmm (tf/formatter "HH:mm" #?(:clj clj-tz)))
(def EEEMMMddHHmmssZZZyyyy (tf/formatter "EEE MMM dd HH:mm:ss ZZZ yyyy" #?(:clj clj-tz)))
(def HHmmss (tf/formatter "HH:mm:ss"))

(defn to-date
  "Prevede z cljs.time date objektu do java.util.Date resp. js/Date"
  [date]
  (when date
    (tc/to-date date)))

(defn from-date
  "Prevede z js Date do cljs.time date"
  [date]
  (when date
    (tc/from-date date)))

(defn to-format [date formatter]
  (if (nil? date)
    ""
    (->> date
         from-date
         #?(:cljs t/to-default-time-zone)
         (tf/unparse formatter))))

(defn from-format [s formatter]
  (when-not (str/blank? s)
    (-> (tf/parse formatter s)
        #?(:cljs (t/from-default-time-zone))
        (to-date))))

(defn full-dMyyyy [s]
  (when-not (str/blank? s)
    (let [today (t/today)
          s (str/replace s #"\s" "")
          end-year  (->> (re-find #"\d{1,2}\.\d{1,2}\.(\d{1,4})$" s)
                         second
                         (drop-while #(= % \0))
                         (apply str))
          s (str s (when (and (<= (count (re-seq #"\." s)) 1)
                              (not (str/ends-with? s ".")))
                     "."))
          s (cond
              (= (count (re-seq #"\." s)) 1)
              (str s (t/month today) "." (t/year today))
              (and (= (count (re-seq #"\." s)) 2)
                   (str/ends-with? s "."))
              (str s (t/year today))
              (and (= (count (re-seq #"\." s)) 2)
                   (< (count end-year) 4))
              (str (subs s 0 (- (count s) (count end-year)))
                   (+ 2000 (edn/read-string end-year)))
              :else s)]
      s)))

(defn from-dMyyyy [s]
  (from-format (full-dMyyyy s) dMyyyy))

(defn full-HHmm [s]
  (when-let [s (not-empty (str/replace (str s) #"\s" ""))]
    (when-let [[_ h m] (re-find #"^(\d{1,2}):?(\d{2,2})?$" s)]
      (str (when (= (count (str h)) 1) "0")
           h
           ":"
           (if (seq m) m "00")))))

(defn plus-day [date]
  (when date
    (let [dt (tc/to-date-time date)]
      (->
       (t/plus dt (t/days 1))
       (tc/to-date)))))

(defn with-date [d time-fn]
  (-> (tc/from-date d)
      (time-fn)
      (tc/to-date)))

(defn since-days-hours-mins-sec
  ([date1] (since-days-hours-mins-sec date1 nil))
  ([date1 date2]
   (let [date1 (some-> date1 (tc/from-date))
         date2 (if date2
                 (tc/from-date date2)
                 (t/now))]
     (when (and date1 date2)
       (let [negative? (t/after? date1 date2)
             interval (if negative?
                        (t/interval date2 date1)
                        (t/interval date1 date2))
             out (:s
                  (reduce (fn [{:keys [s rest-s] :as out} {:keys [unit d]}]
                            (-> out
                                (update :s (fn [s]
                                             (let [v (quot rest-s d)]
                                               (cond
                                                 (and (= unit "d") (zero? v))
                                                 s
                                                 :else
                                                 (str s (when (seq s) ":")
                                                      (when (< v 10) "0")
                                                      v #_unit)))))
                                (assoc :rest-s (rem rest-s d))))
                          {:s ""
                           :rest-s (t/in-seconds interval)}
                          [{:unit "d"
                            :d (* 24 60 60)}
                           {:unit "h"
                            :d (* 60 60)}
                           {:unit "m"
                            :d 60}
                           {:unit "s"
                            :d 1}]))]
         (if negative?
           [:span {:style {:color "red"}} "- " out]
           out))))))

(defn shorten
  ([s] (shorten s 110))
  ([s n]
   (if (<= (count s) n)
     s
     (str (subs s 0 n) " ..."))))

(defn bin-search
  ([xs x comparator]
   (bin-search xs (partial comparator x)))
  ([xs comparator]
   (let [last-idx (dec (count xs))]
     (loop [lower 0
            upper last-idx]
       (if (> lower upper)
         nil
         (let [mid (quot (+ lower upper) 2)
               midvalue (nth xs mid)]
           (case (comparator midvalue)
             -1 (recur lower (dec mid))
             1 (recur (inc mid) upper)
             mid)))))))

(defn date--edn-str [d]
  (subs (pr-str d) 7, 30))

(defn edn-str--date [s]
  (edn/read-string (str "#inst \"" s "\"")))

(defn power-user? [user]
  (or (= (:roles user) "admin")
      (= (:roles user) "power-user")))

(defn admin? [user]
  ((:-rights user) :user/save))

(defn mins--hm [mins]
  (when mins
    (let [h (quot mins 60)
          m (rem mins 60)]
      (str h ":" (when (< m 10) "0") m))))
