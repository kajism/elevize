(ns elevize.cljs.util
  (:require [cljs-time.coerce :as tc]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]
            [clojure.string :as str]
            [cognitect.transit :as tran]
            [re-frame.core :as re-frame]))

(defonce id-counter (atom 0))
(defn new-id []
  (swap! id-counter dec))

(def cid-counter (atom 0))
(defn new-cid []
  (swap! cid-counter inc))

(defn abs [n] (max n (- n)))

(def debug-mw [(when ^boolean goog.DEBUG re-frame/debug)
               #_(when ^boolean goog.DEBUG (re-frame/after valid-schema?))])

(defn dissoc-temp-keys [m]
  (into {} (remove (fn [[k v]]
                     (or (str/starts-with? (name k) "-")
                         (and (str/starts-with? (name k) "_")
                              (sequential? v))))
                   m)))

(defn sort-by-locale
  "Tridi spravne cestinu (pouziva funkci js/String.localeCompare). keyfn musi vracet string!"
  [keyfn coll]
  (sort-by (comp str/capitalize str keyfn) #(.localeCompare %1 %2) coll))

(defn parse-int [s]
  (when s
    (let [n (js/parseInt (str/replace s #"\s+" ""))]
      (if (js/isNaN n)
        nil
        n))))

(defn parse-float [s]
  (when s
    (let [n (js/parseFloat (-> s
                               (str/replace #"\s+" "")
                               (str/replace #"," ".")))]
      (if (js/isNaN n)
        nil
        n))))

(defn boolean->text [b]
  (if b "Ano" "Ne"))

(defn money->text [n]
  (->> n
       (str)
       (reverse)
       (partition-all 3)
       (map #(apply str %))
       (str/join " ")
       (str/reverse)))

(defn file-size->str [n]
  (cond
    (nil? n) ""
    (neg? n) "-"
    (zero? n) "0"
    :else
    (reduce (fn [div label]
              (let [q (quot n div)]
                (if (pos? q)
                  (reduced (str (.toFixed (/ n div) 1) " " label))
                  (/ div 1000))))
            1000000000000
            ["TB" "GB" "MB" "kB" "B"])))

(defn hiccup->val [x]
  (cond
    (and (vector? x) (fn? (first x)))
    (->> x
         rest
         (apply hash-map)
         :label)
    (vector? x)
    (let [out (keep hiccup->val x)]
      (if (> (count out) 1)
        (apply str out)
        (first out)))
    (map? x)
    nil
    (fn? x)
    nil
    (symbol? x)
    nil
    (keyword? x)
    nil
    (tran/bigdec? x)
    (parse-float (.-rep x))
    :else
    x))

(defn danger [s]
  [:span {:dangerously-set-inner-HTML {:__html s}}])
