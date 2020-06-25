(ns elevize.log-transform
  (:require [clojure.edn :as edn]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]))

(defn read-lines
  ([] (read-lines "./log/log/elevize.log"))
  ([file-name]
   (with-open [rdr (clojure.java.io/reader file-name)]
     (let [headers (atom #{})
           spare-rows (->>
                       (line-seq rdr)
                       (reduce (fn [out line]
                                 (try
                                   (let [m (-> line
                                               (edn/read-string))
                                         m (first (:vargs m))]
                                     (if-not (= :elevize.component.plc/device-states-changes (:a m))
                                       out
                                       (do
                                         (assert (map? (:changes m)))
                                         (reduce (fn [out [device-code vars]]
                                                   (assert (map? vars))
                                                   (if-not (get vars "Cas")
                                                     out
                                                     (reduce (fn [out [var-name var-value]]
                                                               (let [header (str device-code ":" var-name)]
                                                                 (swap! headers conj header)
                                                                 (assoc-in out [(get vars "Cas") header] var-value)))
                                                             out
                                                             (dissoc vars "Cas"))))
                                                 out
                                                 (:changes m)))))
                                   (catch Exception e
                                     (timbre/warn {:a ::cannot-read-line :line line :ex-msg (.getMessage e)})
                                     out)))
                               {})
                       (keep (fn [[time m]]
                               (when (instance? java.util.Date time)
                                 (assoc m "Cas" time))))
                       (sort-by #(get % "Cas")))
           sorted-headers (sort @headers)
           csv-lines (->>
                      (conj (map (fn [row]
                                   (str (try (subs (pr-str (get row "Cas")) 7 30)
                                             (catch Exception e
                                               (timbre/warn {:a ::invalid-time :value (get row "Cas") :type (type (get row "Cas")) }))) ";"
                                        (str/join ";" (map #(get row %) sorted-headers))))
                                 spare-rows)
                            (str "Cas;" (str/join ";" sorted-headers))))]
       (spit (str file-name ".csv") (str/join "\r\n" csv-lines))))))
