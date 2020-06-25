(ns elevize.cljs.power-history
  (:require [clojure.string :as str]
            [elevize.cljc.util :as cljc.util]
            [elevize.cljs.common :as common]
            [elevize.cljs.comp.buttons :as buttons]
            [elevize.cljs.comp.data-table :as data-table]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.util :as util]
            [goog.string :as gstr]
            [reagent.ratom :as ratom]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]
            [taoensso.timbre :as timbre]))

(defn- device-label-paths [device-no]
  (let [device-code (str "TE" device-no)]
    (array-map
     (str "Výkon " device-no " kW") [device-code :vykon-zplynovace]
     (str "Vzduch " device-no " kg/s") [device-code :hm-prutok-vzduchu-kg-s]
     (str "Vzduch " device-no " m3/h") [device-code :prutok-vzduchu-m3-hod]
     (str "Palivo " device-no " kg/s") [device-code :fuel-params :hm-prutok-kg-s]
     (str "Tepl. vzd. před " device-no " °C") ["SV1" (str "IO_TeplotaVzduchuPredZplynovacem" device-no)]
     (str "Tlak vzd. před " device-no " kPa") ["SV1" (str "IO_TlakVzduchuPredZplynovacem" device-no)]
     (str "Teplota " device-no " °C") [device-code "IO_Teplota"]
     (str "Tlak spalin " device-no " kPa") [device-code "IO_TlakSpalin"]
     (str "Tepl. za vým. " device-no " °C") ["SVO1" (str "IO_TeplotaZaVymenikem" device-no "PredKompresorem" device-no)]
     (str "Tlak. za vým. " device-no " kPa") ["SVO1" (str "IO_TlakZaVymenikem" device-no "PredKompresorem" device-no)]
     (str "Tepl. za turb. " device-no " °C") ["SVO1" (str "IO_TeplotaZaTurbinou" device-no)]
     (str "Tlak. za turb. " device-no " kPa") ["SVO1" (str "IO_TlakZaTurbinou" device-no)]

     ;; problem s html hlavickou pri exportu do CSV:
     ;; [util/danger (str "Vzduch " device-no " <sup>kg</sup>&frasl;<sub>s</sub>")] [device-code :hm-prutok-vzduchu-kg-s]
     ;; [util/danger (str "Vzduch " device-no " <sup>m<sup>3</sup></sup>&frasl;<sub>h</sub>")] [device-code :prutok-vzduchu-m3-hod]
     ;; [util/danger (str "Palivo " device-no " <sup>kg</sup>&frasl;<sub>s</sub>")] [device-code :fuel-params :hm-prutok-kg-s]
     ;; [util/danger(str "Teplota " device-no " &#8451;")] [device-code "IO_Teplota"]
     ;;(str "Teplota před " device-no) ["SV1" (str "IO_TeplotaVzduchuPredZplynovacem" device-no)]
     )))

(def common-paths
  (array-map
   "Tepl. před turb. °C" ["SVO1" "IO_TeplotaVstup2"]
   "Tlak. před turb. kPa" ["SVO1" "IO_TlakVstup2"]
   "Otáčky 1 RPM" ["OH2" "IO_SnimacOtacek1"]
   "Otáčky 2 RPM" ["OH2" "IO_SnimacOtacek2"]))

(re-frame/reg-sub-raw
 ::label-paths
 (fn [db [_]]
   (let [device-numbers (re-frame/subscribe [:elevize.cljs.common/path-value [::device-numbers]])]
     (ratom/reaction
      (vec (mapcat device-label-paths @device-numbers))))))

(re-frame/reg-sub-raw
 ::avg-paths
 (fn [db [_]]
   (let [label-paths (re-frame/subscribe [::label-paths])]
     (ratom/reaction
      (into
       (mapv second @label-paths)
       (map second common-paths))))))

(re-frame/reg-sub-raw
 ::rows
 (fn [db [_]]
   (let [ds-history (re-frame/subscribe [:elevize.cljs.device-states/history])
         avg-mins (re-frame/subscribe [:elevize.cljs.common/path-value [::avg-mins]])
         avg-paths (re-frame/subscribe [::avg-paths])
         n+ (fnil + 0)]
     (ratom/reaction
      (if (or (not @avg-mins) (zero? @avg-mins))
        @ds-history
        (let [avg-millis (* @avg-mins 60 1000)]
          (->> @ds-history
               (reduce (fn [rr ds]
                         (let [eb1-cas (get-in ds ["EB1" "Cas"])
                               last-delta-millis (some->> rr :temp :last-time-millis (- (.getTime eb1-cas)))
                               start-delta-millis (some->> rr :temp :start-time-millis (- (.getTime eb1-cas)))]
                           (cond-> rr
                             (or (> start-delta-millis avg-millis)
                                 (nil? (:temp rr)))
                             (assoc :temp {:start-time-millis (.getTime eb1-cas)
                                           :last-time-millis (.getTime eb1-cas)})

                             (and (some? (:temp rr)) (<= start-delta-millis avg-millis))
                             (as-> $
                                 (reduce (fn [out path]
                                           (update-in out [:temp path] n+ (* (get-in ds path) last-delta-millis)))
                                         $
                                         @avg-paths)
                               (assoc-in $ [:temp :last-time-millis] (.getTime (get-in ds ["EB1" "Cas"]))))

                             (and (some? (:temp rr)) (> start-delta-millis avg-millis))
                             (update :out conj (reduce (fn [out path]
                                                         (assoc-in out path (/ (get-in rr [:temp path])
                                                                               start-delta-millis)))
                                                       {"EB1" {"Cas" eb1-cas}}
                                                       @avg-paths)))))
                       {:out []
                        :temp nil})
               :out)))))))

(defn table [items]
  (let [devices (re-frame/subscribe [:entities :device])
        vars-by-device-code&name (re-frame/subscribe [:elevize.cljs.device-states/vars-by-device-code&name])
        table-state (re-frame/subscribe [:table-state :power-history])
        device-numbers (re-frame/subscribe [:elevize.cljs.common/path-value [::device-numbers]])
        label-paths (re-frame/subscribe [::label-paths])]
    (fn []
      (if-not @devices
        [re-com/throbber]
        ^{:key @label-paths}
        [data-table/data-table
         :table-id :power-history
         :colls (-> [[[:div]
                      (fn [row] "")
                      :csv-export]
                     {:header "Kdy"
                      :val-fn #(get-in % ["EB1" "Cas"])
                      :td-comp (fn [& {:keys [value row row-state]}]
                                 [:td
                                  (cljc.util/to-format value cljc.util/ddMMyyyyHHmmss)])}]
                    (into (map (fn [[label path]]
                                 [(str label " " (:kks (get-in @vars-by-device-code&name path)))
                                  #(gstr/format "%.3f" (get-in % path))])
                               @label-paths))
                    (into (map (fn [[label path]]
                                 [(str label " " (:kks (get-in @vars-by-device-code&name path)))
                                  #(gstr/format "%.3f" (get-in % path))])
                               common-paths)))
         :rows items
         :desc? true]))))

(defn page-power-history []
  (let [items (re-frame/subscribe [::rows])
        user @(re-frame/subscribe [:auth-user])]
    (when-not @(re-frame/subscribe [:elevize.cljs.common/path-value [::device-numbers]])
      (re-frame/dispatch [:elevize.cljs.common/set-path-value [::device-numbers] [3 4]]))
    [re-com/v-box :children
     [[re-com/h-box :align :baseline :gap "10px" :children
       [[:h3 "Historie výkonu"]
        [:div.dropdown
         [re-com/single-dropdown
          :choices [{:id [3] :label "Zplynovač 3"}
                    {:id [4] :label "Zplynovač 4"}
                    {:id [3 4] :label "Zplynovač 3 a 4"}]
          :model @(re-frame/subscribe [:elevize.cljs.common/path-value [::device-numbers]])
          :on-change #(re-frame/dispatch [:elevize.cljs.common/set-path-value [::device-numbers] %])
          :width "150px"]]
        [re-com/label :label "Zprůměrovat po"]
        [re-com/input-text
         :model (str @(re-frame/subscribe [:elevize.cljs.common/path-value [::avg-mins]]))
         :on-change #(re-frame/dispatch [:elevize.cljs.common/set-path-value [::avg-mins] (util/parse-int %)])
         :validation-regex #"^\d{0,2}$"
         :width "50px"]
        [re-com/label :label "min"]]]
      (if-not (cljc.util/admin? user)
        [re-com/throbber]
        [table items])]]))


(pages/add-page :power-history #'page-power-history)

(secretary/defroute "/historie-vykonu" []
  (re-frame/dispatch [:set-current-page :power-history]))

