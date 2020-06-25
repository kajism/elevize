(ns elevize.cljc.calc-derived
  #?@(:clj
      [(:require
        [clojure.edn :as edn]
        [clojure.string :as str]
        [taoensso.timbre :as timbre])
       (:import java.lang.IllegalArgumentException
                java.util.Date)]
      :cljs
      [(:require
        [cljs.tools.reader.edn :as edn]
        [clojure.string :as str]
        [taoensso.timbre :as timbre])]))

(defn- assoc-calc-air-flow [changes xs device-no]
  (let [device-state (peek xs)
        device-code (str "TE" device-no)
        prutok-path ["SV1" (str "IO_Flowswitch" device-no)]
        teplota-path ["SV1" (str "IO_TeplotaVzduchuPredZplynovacem" device-no)]
        tlak-path ["SV1" (str "IO_TlakVzduchuPredZplynovacem" device-no)]]
    (if-not (some #(get-in changes %) [prutok-path teplota-path tlak-path])
      changes
      (let [prutok-m-s (or (get-in changes prutok-path) (get-in device-state prutok-path) 0)
            prutok-m3-hod (* 0.0314 60 60 prutok-m-s)
            teplota-K (+ (or (get-in changes teplota-path) (get-in device-state teplota-path) 0)
                         273.15)
            tlak+atm-Pa (+ (* (or (get-in changes tlak-path) (get-in device-state tlak-path) 0)
                              1000)
                           101325) ;; kPa->Pa + atm. tlak
            hustota-kg-1m3 (/ (* tlak+atm-Pa 1.0 0.02896)
                              (* 8.31 teplota-K))
            hm-prutok-kg-s (/ (* hustota-kg-1m3 prutok-m3-hod) 3600)]
        (-> changes
            (assoc-in [device-code :prutok-vzduchu-m3-hod] prutok-m3-hod)
            (assoc-in [device-code :hm-prutok-vzduchu-kg-s] hm-prutok-kg-s))))))

(defn- read-fuel-table [csv]
  (->> (str/split csv
                  #"\n")
       (map (fn [line]
              (->> (str/split line #"\t")
                   (map #(edn/read-string (str/replace % "," ".")))
                   (zipmap [:otacky :vzduch :hm-prutok-kg-hod :hm-prutok-kg-s :mnozstvi-vzduchu :tepelny-vykon :mnozstvi-spalin]))))
       (map (juxt :otacky identity))
       (into {})))

(def default-fuel-table
  {"TE3" (read-fuel-table "6		73,032	0,0202866667	321,34	344,8733333333	383,42
7		78,252	0,0217366667	344,31	369,5233333333	410,82
8		83,472	0,0231866667	367,28	394,1733333333	438,23
9		88,692	0,0246366667	390,24	418,8233333333	465,63
10		93,912	0,0260866667	413,21	443,4733333333	493,04
11		99,132	0,0275366667	436,18	468,1233333333	520,44
12		104,352	0,0289866667	459,15	492,7733333333	547,85
13		108,866	0,0302405556	479,01	514,0894444444	571,55
14		113,38	0,0314944444	498,87	535,4055555556	595,25
15		117,894	0,0327483333	518,73	556,7216666667	618,94
16		122,408	0,0340022222	538,60	578,0377777778	642,64
17		126,922	0,0352561111	558,46	599,3538888889	666,34
18		131,436	0,03651	578,32	620,67	690,04
19		137,366	0,0381572222	604,41	648,6727777778	721,17
20		143,296	0,0398044444	630,50	676,6755555556	752,30
21		149,226	0,0414516667	656,59	704,6783333333	783,44
22		155,156	0,0430988889	682,69	732,6811111111	814,57
23		161,086	0,0447461111	708,78	760,6838888889	845,70
24		167,016	0,0463933333	734,87	788,6866666667	876,83
25		174,102	0,0483616667	766,05	822,1483333333	914,04
26		181,188	0,05033	797,23	855,61	951,24
27		188,274	0,0522983333	828,41	889,0716666667	988,44
28		195,36	0,0542666667	859,58	922,5333333333	1025,64
29		202,446	0,056235	890,76	955,995	1062,84
30		209,532	0,0582033333	921,94	989,4566666667	1100,04
31		216,618	0,0601716667	953,12	1022,9183333333	1137,24
32		223,704	0,06214	984,30	1056,38	1174,45
33		230,79	0,0641083333	1015,48	1089,8416666667	1211,65
34		237,876	0,0660766667	1046,65	1123,3033333333	1248,85
35		244,962	0,068045	1077,83	1156,765	1286,05
36		252,048	0,0700133333	1109,01	1190,2266666667	1323,25
37		259,134	0,0719816667	1140,19	1223,6883333333	1360,45
38		266,22	0,07395	1171,37	1257,15	1397,66
39		273,306	0,0759183333	1202,55	1290,6116666667	1434,86
40		280,392	0,0778866667	1233,72	1324,0733333333	1472,06
41		287,478	0,079855	1264,90	1357,535	1509,26
42		294,564	0,0818233333	1296,08	1390,9966666667	1546,46
43		301,65	0,0837916667	1327,26	1424,4583333333	1583,66
44		308,736	0,08576	1358,44	1457,92	1620,86
45		315,822	0,0877283333	1389,62	1491,3816666667	1658,07
46		322,908	0,0896966667	1420,80	1524,8433333333	1695,27
47		329,994	0,091665	1451,97	1558,305	1732,47
48		337,08	0,0936333333	1483,15	1591,7666666667	1769,67
49		344,166	0,0956016667	1514,33	1625,2283333333	1806,87
50		351,252	0,09757	1545,51	1658,69	1844,07")

   "TE4" (read-fuel-table "6		55,38	0,0153833333	243,67	261,5166666667	290,75
7		61,996	0,0172211111	272,78	292,76	325,48
8		68,612	0,0190588889	301,89	324,00	360,21
9		75,228	0,0208966667	331,00	355,24	394,95
10		81,844	0,0227344444	360,11	386,49	429,68
11		88,46	0,0245722222	389,22	417,73	464,42
12		95,076	0,02641	418,33	448,97	499,15
13		100,896	0,0280266667	443,94	476,45	529,70
14		106,716	0,0296433333	469,55	503,94	560,26
15		112,536	0,03126	495,16	531,42	590,81
16		118,356	0,0328766667	520,77	558,90	621,37
17		124,176	0,0344933333	546,37	586,39	651,92
18		129,996	0,03611	571,98	613,87	682,48
19		136,556	0,0379322222	600,85	644,85	716,92
20		143,116	0,0397544444	629,71	675,83	751,36
21		149,676	0,0415766667	658,57	706,80	785,80
22		156,236	0,0433988889	687,44	737,78	820,24
23		162,796	0,0452211111	716,30	768,76	854,68
24		169,356	0,0470433333	745,17	799,74	889,12
25		175,096	0,0486377778	770,42	826,84	919,25
26		180,836	0,0502322222	795,68	853,95	949,39
27		186,576	0,0518266667	820,93	881,05	979,52
28		192,316	0,0534211111	846,19	908,16	1009,66
29		198,056	0,0550155556	871,45	935,26	1039,79
30		203,796	0,05661	896,70	962,37	1069,93
31		209,536	0,0582044444	921,96	989,48	1100,06
32		215,276	0,0597988889	947,21	1016,58	1130,20
33		221,016	0,0613933333	972,47	1043,69	1160,33
34		226,756	0,0629877778	997,73	1070,79	1190,47
35		232,496	0,0645822222	1022,98	1097,90	1220,60
36		238,236	0,0661766667	1048,24	1125,00	1250,74
37		243,976	0,0677711111	1073,49	1152,11	1280,87
38		249,716	0,0693655556	1098,75	1179,21	1311,01
39		255,456	0,07096	1124,01	1206,32	1341,14
40		261,196	0,0725544444	1149,26	1233,43	1371,28
41		266,936	0,0741488889	1174,52	1260,53	1401,41
42		272,676	0,0757433333	1199,77	1287,64	1431,55
43		278,416	0,0773377778	1225,03	1314,74	1461,68
44		284,156	0,0789322222	1250,29	1341,85	1491,82
45		289,896	0,0805266667	1275,54	1368,95	1521,95
46		295,636	0,0821211111	1300,80	1396,06	1552,09
47		301,376	0,0837155556	1326,05	1423,16	1582,22
48		307,116	0,08531	1351,31	1450,27	1612,36
49		312,856	0,0869044444	1376,57	1477,38	1642,49
50		318,596	0,0884988889	1401,82	1504,48	1672,63")})

(defn- assoc-fuel-params [changes xs device-no fuel-table]
  (let [device-code (str "TE" device-no)
        otacky-paliva-path [device-code "IO_PalivoFreqAct"]]
    (assoc-in changes [device-code :fuel-params]
              (get-in fuel-table [device-code (Math/round (or (get-in changes otacky-paliva-path)
                                                              (get-in (peek xs) otacky-paliva-path)
                                                              0))]
                      {:hm-prutok-kg-s 0
                       :tepelny-vykon 0}))))

(def merne-teplo-vzd 1.1)

(defn- calc-avg-pure [xs path secs]
  (let [avg-millis (* secs 1000)
        peek-millis (some-> (get-in (peek xs) ["EB1" "Cas"]) (.getTime))
        avg-vals (->> xs
                      (reverse)
                      (take-while #(<= (- peek-millis (.getTime (get-in % ["EB1" "Cas"])))
                                       avg-millis))
                      (keep #(get-in % path)))]
    (if (zero? (count avg-vals))
      nil
      (/ (apply + avg-vals)
         (count avg-vals)))))

(def avg-cache (atom {}))

#_(add-watch avg-cache :debug (fn [_ _ o n]
                              (print n)
                              (pr (butlast (clojure.data/diff o n)))))

(defn- calc-avg-cached [xs path secs]
  (if (zero? (count xs))
    nil
    (let [cpath (conj path secs)
          c (get @avg-cache cpath)
          p (peek xs)
          peek-millis (.getTime (get-in p ["EB1" "Cas"]))]
      (when-not (= peek-millis (:last-millis c))
        (let [c (cond
                  (= (count xs) 1)
                  {:i 0 :sum 0}
                  (nil? c)
                  {:i (dec (count xs)) :sum 0}
                  :else
                  c)
              new-i (or (some->> (:i c)
                                 (iterate inc)
                                 (take-while #(>= (- peek-millis (-> (get xs %)
                                                                     (get-in ["EB1" "Cas"])
                                                                     .getTime))
                                                  (* secs 1000)))
                                 (last)
                                 (inc))
                        (:i c))
              c (-> c
                    (update :sum + (get-in p path))
                    (assoc :last-millis peek-millis))
              c (if (= new-i (:i c))
                  c
                  (-> c
                      (assoc :i new-i)
                      (update :sum (fn [s]
                                     (reduce (fn [out i]
                                               (- out (get-in (get xs i) path)))
                                             s
                                             (range (:i c) new-i))))))]
          (swap! avg-cache assoc cpath c)))
      (/ (:sum c)
         (- (count xs) (:i c))))))

(comment
  (def n 100)

  (def xs (mapv (fn [i]
                  {"EB1" {"Cas" #?(:clj (Date. (* i 300))
                                   :cljs (js/Date. (* i 300)))
                          :x (rand-int 1000)
                          #_(if (< i (/ n 2))
                              0
                              10)}})
                (range n)))

  (def p ["EB1" :x])

  (def s 20)

  (def avgs1 (time (doall (map #(calc-avg-pure (subvec xs 0 (inc %)) p s) (range n)))))

  (def avgs2 (time (doall (map #(calc-avg-cached (subvec xs 0 (inc %)) p s) (range n)))))

  #_(assert (= avgs1 avgs2)))

(defn- indexed-avg-secs [avg-secs]
  (map-indexed vector (keep identity avg-secs)))

(defn- assoc-calc-powers&avgs [changes xs device-no avg-secs avg-fn fuel-table]
  (let [device-code (str "TE" device-no)
        otacky-paliva-path [device-code "IO_PalivoFreqAct"]
        prutok-vzduchu-path [device-code :prutok-vzduchu-m3-hod]
        hm-prutok-vzduchu-path [device-code :hm-prutok-vzduchu-kg-s]
        T1-path ["SV1" (str "IO_TeplotaVzduchuPredZplynovacem" device-no)]
        T2-path [device-code "IO_Teplota"]
        T3-path ["SVO1" (str "IO_TeplotaZaVymenikem" device-no "PredKompresorem" device-no)]
        T4-path ["SVO1" "IO_TeplotaVstup2"]
        T5-path ["SVO1" (str "IO_TeplotaZaTurbinou" device-no)]
        otacky-bubnu-path [device-code "IO_BubenFreqAct"]
        current (merge-with merge (peek xs) changes)
        mezivysledek (* merne-teplo-vzd (+ (get-in current hm-prutok-vzduchu-path)
                                           (get-in current [device-code :fuel-params :hm-prutok-kg-s])))
        T1 (get-in current T1-path)]
    (as-> changes $
      (-> $
          (assoc-in [device-code :tepelny-vykon-paliva] (get-in current [device-code :fuel-params :tepelny-vykon]))
          (assoc-in [device-code :vykon-zplynovace] (* mezivysledek (- (get-in current T2-path) T1)))
          (assoc-in [device-code :vykon-za-vymenikem] (* mezivysledek (- (get-in current T3-path) T1)))
          (assoc-in [device-code :vykon-pred-turbinou] (* mezivysledek (- (get-in current T4-path) T1)))
          (assoc-in [device-code :vykon-za-turbinou] (* mezivysledek (- (get-in current T5-path) T1))))
      (reduce (fn [out [i avg-sec]]
                (if-let [otacky-paliva (avg-fn xs otacky-paliva-path avg-sec)]
                  (let [hm-prutok-paliva (avg-fn xs [device-code :fuel-params :hm-prutok-kg-s] avg-sec)
                        hm-prutok-vzduchu (avg-fn xs hm-prutok-vzduchu-path avg-sec)
                        T1-avg (avg-fn xs T1-path avg-sec)
                        T2-avg (avg-fn xs T2-path avg-sec)
                        T3-avg (avg-fn xs T3-path avg-sec)
                        T4-avg (avg-fn xs T4-path avg-sec)
                        T5-avg (avg-fn xs T5-path avg-sec)
                        mezivysledek (* merne-teplo-vzd (+ hm-prutok-vzduchu hm-prutok-paliva))
                        vykon-zplynovace (* mezivysledek (- T2-avg T1-avg))
                        tepelny-vykon (avg-fn xs [device-code :fuel-params :tepelny-vykon] avg-sec)]
                    (-> out
                        (assoc-in [device-code (keyword (str "prutok-vzduchu" i))] (avg-fn xs prutok-vzduchu-path avg-sec))
                        (assoc-in [device-code (keyword (str "hm-prutok-vzduchu" i))] hm-prutok-vzduchu)
                        (assoc-in [device-code (keyword (str "otacky-paliva" i))] otacky-paliva)
                        (assoc-in [device-code (keyword (str "otacky-bubnu" i))] (avg-fn xs otacky-bubnu-path avg-sec))
                        (assoc-in [device-code (keyword (str "hm-prutok-paliva" i))] (* hm-prutok-paliva 3600))
                        (assoc-in [device-code (keyword (str "tepelny-vykon-paliva" i))] tepelny-vykon)
                        (assoc-in [device-code (keyword (str "vykon-zplynovace" i))] vykon-zplynovace)
                        (assoc-in [device-code (keyword (str "vykon-za-vymenikem" i))] (* mezivysledek (- T3-avg T1-avg)))
                        (assoc-in [device-code (keyword (str "vykon-pred-turbinou" i))] (* mezivysledek (- T4-avg T1-avg)))
                        (assoc-in [device-code (keyword (str "vykon-za-turbinou" i))] (* mezivysledek (- T5-avg T1-avg)))
                        (assoc-in [device-code (keyword (str "ucinnost-zplynovace" i))] (if (zero? tepelny-vykon)
                                                                                          200
                                                                                          (/ vykon-zplynovace tepelny-vykon 0.01)))))
                  out))
              $
              (indexed-avg-secs avg-secs)))))

(defn- assoc-sum-powers [changes device-state avg-secs]
  (as-> changes $
    (reduce (fn [out kw]
              (assoc-in out ["TE3+4" kw] (+ (get-in changes ["TE3" kw])
                                            (get-in changes ["TE4" kw]))))
            $
            [:tepelny-vykon-paliva :vykon-zplynovace :vykon-za-vymenikem :vykon-pred-turbinou :vykon-za-turbinou])
    (reduce (fn [out [i avg-sec]]
              (let [kw-pred-turbinou (keyword (str "vykon-pred-turbinou" i))
                    kw-za-turbinou (keyword (str "vykon-za-turbinou" i))
                    sum-fn #(+ (or (get-in changes ["TE3" %])
                                   (get-in device-state ["TE3" %])
                                   0)
                               (or (get-in changes ["TE4" %])
                                   (get-in device-state ["TE4" %])
                                   0))
                    P-pred-turbinou (sum-fn kw-pred-turbinou)
                    P-za-turbinou (sum-fn kw-za-turbinou)]
                (-> out
                    (assoc-in ["TE3+4" kw-pred-turbinou] P-pred-turbinou)
                    (assoc-in ["TE3+4" kw-za-turbinou] P-za-turbinou))))
            $
            (indexed-avg-secs avg-secs))))

(def device-numbers [3 4])

(defn merge-device-states-history [xs changes avg-settings]
  (conj xs
        (merge-with merge (peek xs)
                    (as-> changes $
                      (reduce (fn [out device-no]
                                (-> out
                                    (assoc-calc-air-flow xs device-no)
                                    (assoc-fuel-params xs device-no (:fuel-table avg-settings))
                                    (assoc-calc-powers&avgs xs device-no
                                                            (:secs avg-settings)
                                                            (if (:cached-avg? avg-settings)
                                                              calc-avg-cached
                                                              calc-avg-pure)
                                                            (:fuel-table avg-settings))))
                              $
                              device-numbers)
                      (assoc-sum-powers $ (peek xs) (:secs avg-settings))))))

