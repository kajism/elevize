(ns elevize.cljs.comp.zplynovac
  (:require [elevize.cljc.util :as cljc.util]
            [elevize.cljs.util :as util]
            [elevize.cljs.comp.status-table :as status-table]
            [elevize.cljs.dygraph :as dygraph]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.sente :refer [server-call]]
            [goog.string :as gstr]
            [goog.string.format]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]
            [taoensso.timbre :as timbre]
            [reagent.core :as reagent]
            [clojure.string :as str]))

(re-frame/reg-event-db
 ::set-variable
 util/debug-mw
 (fn [db [_ var new-value]]
   (let [devices (:device db)
         device-code (->> var :device-id (get devices) :code)
         current-value (get-in (peek (:elevize.cljs.device-states/history db)) [device-code (:name var)])]
     (when (not= (str current-value) new-value)
       (server-call [:elevize/set-device-state-variable {:device-code device-code
                                                         :var-name (:name var)
                                                         :var-set-name (:set-name var)
                                                         :value new-value}]
                    [:set-msg :info]))
     db)))

(defn edit-interpol-table []
  (let [edit-str (reagent/atom nil)]
    (fn [row value]
      (if-not @edit-str
        [re-com/button :label "Upravit"
         :on-click #(let [lines (-> value
                                    (str/split #":"))]
                      (reset! edit-str (some->> lines (str/join "\n") (str/trim))))]
        [:div
         [re-com/input-textarea
          :model edit-str
          :on-change #(reset! edit-str %)
          :width "150px"
          :change-on-blur? false
          :rows (max 3 (inc (count (str/split @edit-str #"\n"))))]
         [re-com/button :label "Uložit"
          :on-click #(do (re-frame/dispatch [::set-variable row (str/replace @edit-str #"(\r?\n)+" ":")])
                         (reset! edit-str nil))]
         [re-com/button :label "Zrušit"
          :on-click #(do (reset! edit-str nil))]]))))

(defn edit-value []
  (let [set-value (reagent/atom "")]
    (fn [var value enums-by-group]
      (cond
        (= "BOOL" (:data-type var))
        [:div
         [:button.true {:on-click #(re-frame/dispatch [::set-variable var "TRUE"])} "ZAP"]
         " "
         [:button.false {:on-click #(re-frame/dispatch [::set-variable var "FALSE"])} "VYP"]]
        (= "INTERPOL_TABLE" (:data-type var))
        [edit-interpol-table var value]
        (some-> var :data-type enums-by-group)
        [re-com/single-dropdown
         :width "150px"
         :choices (-> var :data-type enums-by-group vals)
         :model value
         :id-fn :order-pos
         :on-change #(re-frame/dispatch [::set-variable var %])]
        :else
        [re-com/input-text
         :model set-value
         :on-change #(do
                       (re-frame/dispatch [::set-variable var %])
                       (reset! set-value %)
                       (js/setTimeout (fn [] (reset! set-value "")) 3000))
         :width "70px"]))))

(defn teplota
  ([label]
   (teplota label nil))
  ([label color]
   [:div (when color
           {:class "underline"
            :style {:text-decoration-color color}})
    label " " [util/danger "&#8451;"]]))

(defn prumer [label]
  [:div [util/danger "&empty;"] " " label])

(defn tr-for-path* []
  (let [enums-by-group (re-frame/subscribe [:elevize.cljs.enum-item/by-group&order])]
    (fn [& {:keys [label value var read-only? kks? kks-col?]}]
      [:tr
       [:td label]
       [:td {:class (str "bold "
                         (when (number? value) "right "))}
        (if-let [enum-label (some-> var :data-type (@enums-by-group) (get value) :label)]
          (cljc.util/shorten enum-label 22)
          (if (and (or (nil? (:data-type var))
                       (= (:data-type var) "REAL"))
                   (number? value))
            (gstr/format "%.3f" value)
            (str value)))]
       [:td
        (if read-only?
          (when (and kks? (not kks-col?))
            (:kks var))
          [edit-value var value @enums-by-group])]
       (when kks-col?
         [:td (:kks var)])])))

(defn tr-for-path []
  (let [device-states (re-frame/subscribe [:elevize.cljs.device-states/current])
        vars-by-device-code&name (re-frame/subscribe [:elevize.cljs.device-states/vars-by-device-code&name])
        set-value (reagent/atom "")
        disp-graph? (reagent/atom false)]
    (fn [& {:keys [label path read-only? kks? kks-col? val-fn] :or {kks? true val-fn identity}}]
      [:tbody
       [tr-for-path* :label [:div {:on-click #(swap! disp-graph? not)} label] :value (val-fn (get-in @device-states path)) :var (get-in @vars-by-device-code&name path) :read-only? read-only? :kks? kks? :kks-col? kks-col?]
       (when @disp-graph?
         [:tr
          [:td {:col-span 3 :height "100px"}
           [dygraph/variables-state-plotter [path]
            :width "100%"
            :height "150px"
            :last-minutes 10
            :show-controls? false]]])])))

(defn tr-for-avgs []
  (let [device-states (re-frame/subscribe [:elevize.cljs.device-states/current])
        disp-graph? (reagent/atom false)]
    (fn [& {:keys [label device-code avg]}]
      (let [path0 [device-code (keyword (str (name avg) 0))]
            path1 [device-code (keyword (str (name avg) 1))]
            path2 [device-code (keyword (str (name avg) 2))]]
        [:tbody
         [:tr
          [:td [:div {:on-click #(swap! disp-graph? not)} label]]
          [:td.right {:style {:min-width "55px"}} (some->> (get-in @device-states path0) (gstr/format "%3.2f"))]
          [:td.right {:style {:min-width "55px"}} (some->> (get-in @device-states path1) (gstr/format "%3.2f"))]
          [:td.right {:style {:min-width "55px"}} (some->> (get-in @device-states path2) (gstr/format "%3.2f"))]]
         (when @disp-graph?
           [:tr
            [:td {:col-span 4 :height "100px"}
             [dygraph/variables-state-plotter [path0 path1 path2]
              :width "100%"
              :height "150px"
              :last-minutes 10
              :colors ["green" "navy" "red"]
              :show-controls? false]]])]))))

(defn klapka []
  (let [device-states (re-frame/subscribe [:elevize.cljs.device-states/current])
        vars-by-device-code&name (re-frame/subscribe [:elevize.cljs.device-states/vars-by-device-code&name])
        set-value (reagent/atom "")]
    (fn [device-no no]
      (let [var-name (str "IO_Klapka" device-no no)
            value (get-in @device-states ["SV1" (str var-name "_Act")])]
        [:tbody
         [:tr
          [:td {:class (str (when (cljc.util/bool (get-in @device-states ["SV1" (str var-name "_Error")]))
                              "error"))}
           (str "Klapka " no " %")]
          [:td.bold (when (number? value) {:class "right"})
           value]
          [:td
           [re-com/input-text
            :model set-value
            :on-change #(do
                          (re-frame/dispatch [::set-variable
                                              (get-in @vars-by-device-code&name ["SV1" var-name])
                                              %])
                          (reset! set-value %)
                          (js/setTimeout (fn [] (reset! set-value "")) 3000))
            :width "70px"]]]]))))

#_(defn bezp-klapka [device-no]
  (let [device-states @(re-frame/subscribe [:elevize.cljs.device-states/current])
        vars-by-device-code&name @(re-frame/subscribe [:elevize.cljs.device-states/vars-by-device-code&name])
        var-name (str "IO_BezpKlapka" device-no)
        act (cljc.util/bool (get-in device-states ["SV1" var-name]))
        open? (cljc.util/bool (get-in device-states ["SV1" (str var-name "_DorazOtv")]))
        closed? (cljc.util/bool (get-in device-states ["SV1" (str var-name "_DorazZav")]))
        error? (cljc.util/bool (get-in device-states ["SV1" (str var-name "_Error")]))]
    [:tbody
     [:tr
      [:td "Bezpečnostní klapka"]
      [:td {:style {:font-weight "bold"}
            :class (str (cond error? "error"
                              open? "true"
                              closed? "false"))}
       (cond
         error? "chyba"
         open? "otevřená"
         closed? "zavřená"
         act "otevírám"
         :else "zavírám")]
      [:td
       (when-not (or open? error?)
         [:button.true {:on-click #(re-frame/dispatch [::set-variable
                                                       (get-in vars-by-device-code&name ["SV1" var-name])
                                                       "TRUE"])}
          "otevřít"])
       [:br]
       (when-not (or closed? error?)
         [:button.false {:on-click #(re-frame/dispatch [::set-variable
                                                        (get-in vars-by-device-code&name ["SV1" var-name])
                                                        "FALSE"])}
          "zavřít"])]]]))

(defn pistnice [vars-by-device-code&name device-states device-code no]
  (let [var-name (str "IO_Pistnice" no)
        act-set (cljc.util/bool (get-in device-states [device-code var-name]))
        error? (cljc.util/bool (get-in device-states [device-code (str "ChybaPistnice" no)]))
        open? (cljc.util/bool (get-in device-states [device-code (str "IO_DorazFalsePistnice" no)]))
        opening? (cljc.util/bool (get-in device-states [device-code (str var-name "_Zav")]))
        closing? (cljc.util/bool (get-in device-states [device-code (str var-name "_Otv")]))
        closed? (cljc.util/bool (get-in device-states [device-code (str "IO_DorazTruePistnice" no)]))
        var (get-in vars-by-device-code&name [device-code var-name])]
    [:tbody
     [:tr
      [:td "Pístnice " no]
      [:td {:style {:font-weight "bold"}
            :class (str (cond
                          error? "error"
                          open? "true"
                          closed? "false"))}
       (cond
         error? "chyba"
         open? "otevřená"
         closed? "zavřená"
         opening? "otevírám"
         closing? "zavírám"
         :else "?")]
      [:td
       (when (false? act-set)
         [:button.true {:on-click #(re-frame/dispatch [::set-variable var "TRUE"])}
          "zavřít"])
       " "
       (when (true? act-set)
         [:button.false {:on-click #(re-frame/dispatch [::set-variable var "FALSE"])}
          "otevřít"])]
      [:td (:kks var)]]]))

(defn tr-for-path-bool []
  (let [device-states (re-frame/subscribe [:elevize.cljs.device-states/current])
        vars-by-device-code&name (re-frame/subscribe [:elevize.cljs.device-states/vars-by-device-code&name])
        blink-show? (re-frame/subscribe [:elevize.cljs.common/path-value [:elevize.cljs.comp.status-table/blink-show?]])]
    (fn [& {:keys [label path path-err path-ready path-act read-only? kks? kks-col? klapka? obracena-logika? settings class] :or {kks? true}}]
      (let [error? (cljc.util/bool (get-in @device-states path-err))
            on? (cljc.util/bool (get-in @device-states path))
            on-act? (cond-> (cljc.util/bool (get-in @device-states (or path-act path)))
                      obracena-logika?
                      (not))
            var (get-in @vars-by-device-code&name path)]
        [:tbody
         [:tr {:class (str class (when (and error? @blink-show?) " KritickaChyba"))}
          [:td label]
          [:td {:class (str "bold " (cond error? "error"
                                          on-act? "true"
                                          (false? on-act?) "false"
                                          :else ""))}
           (cond
             error? "chyba"
             on-act? (if klapka? "otevřená" "zapnuto")
             (false? on-act?) (if klapka? "zavřená" "vypnuto")
             :else "?")]
          (cond
            read-only?
            [:td (when (and kks? (not kks-col?))
                   (:kks var))]
            settings
            [:th settings]
            :else
            [:td
             (when (false? on-act?)
               [:button.true {:on-click #(re-frame/dispatch [::set-variable var (if obracena-logika? "FALSE" "TRUE")])}
                (if klapka? "otevřít" "zapnout")])
             " "
             (when (true? on-act?)
               [:button.false {:on-click #(re-frame/dispatch [::set-variable var (if obracena-logika? "TRUE" "FALSE")])}
                (if klapka? "zavřít" "vypnout")])])
          (when kks-col?
            [:td (:kks var)])
          (when (and path-ready
                     (not (cljc.util/bool (get-in @device-states path-ready))))
            [:td {:class "error"} "není ready"])]]))))

(defn time-by-path [path device-states]
  (let [value (get-in device-states path)]
    (when (and (= js/Date (type value)) (some-> value .getTime zero? not))
      value)))

(defn flowswitch [device-no]
  (let [device-states (re-frame/subscribe [:elevize.cljs.device-states/current])
        disp-graph? (reagent/atom false)
        disp-hm-graph? (reagent/atom false)]
    (fn []
      (let [device-code (str "TE" device-no)
            prutok-path ["SV1" (str "IO_Flowswitch" device-no)]
            hm-prutok-path [device-code :hm-prutok-vzduchu-kg-s]
            hm-prutok-kg-s (get-in @device-states hm-prutok-path)]
        [:tbody
         [:tr
          [:td {:on-click #(swap! disp-graph? not)}
           [util/danger (str "Průtok vzduchu <sup>m</sup>&frasl;<sub>s</sub> | "
                        "<sup>m<sup>3</sup></sup>&frasl;<sub>h</sub>")]]
          [:td.bold {:class "right"} (some->> (get-in @device-states prutok-path) (gstr/format "%.3f"))]
          [:td.bold {:class "right"} (some->> (get-in @device-states [device-code :prutok-vzduchu-m3-hod]) (gstr/format "%.2f"))]]
         (when @disp-graph?
           [:tr
            [:td {:col-span 3 :height "100px"}
             [dygraph/variables-state-plotter [prutok-path]
              :width "100%"
              :height "150px"
              :last-minutes 10
              :show-controls? false]]])
         [:tr {:style {:background-color "#f9f9f9"}}
          [:td {:on-click #(swap! disp-hm-graph? not)}
           [util/danger (str "Hm. průtok <sup>kg</sup>&frasl;<sub>s</sub> | "
                             "<sup>kg</sup>&frasl;<sub>min</sub>")]]
          [:td.bold {:class "right"} (some->> hm-prutok-kg-s (gstr/format "%.3f"))]
          [:td.bold {:class "right"} (some->> hm-prutok-kg-s (* 60) (gstr/format "%.2f"))]]
         (when @disp-hm-graph?
           [:tr
            [:td {:col-span 3 :height "100px"}
             [dygraph/variables-state-plotter [hm-prutok-path]
              :width "100%"
              :height "150px"
              :last-minutes 10
              :show-controls? false]]])]))))

(defn tos-status [& {:keys [device-code label]}]
  (let [device-states @(re-frame/subscribe [:elevize.cljs.device-states/current])]
    [:div.bold label " "
     [:span {:class (str (boolean (cljc.util/bool (get-in device-states [device-code "IO_Snek1_Chod"]))))}
      " Š1 "]
     [:span {:class (str (boolean (cljc.util/bool(get-in device-states [device-code "IO_Pistnice1_Otv"]))))}
      " P1 "]
     [:span {:class (str (boolean (cljc.util/bool(get-in device-states [device-code "IO_Snek2_Chod"]))))}
      " Š2 "]
     [:span {:class (str (boolean (cljc.util/bool(get-in device-states [device-code "IO_Pistnice2_Otv"]))))}
      " P2 "]]))

(defn casy [device-code]
  (let [device-states @(re-frame/subscribe [:elevize.cljs.device-states/current])]
    [:table.table.tree-table.table-hover.table-striped.table-sm
     [:thead
      [:tr
       [:th "Časy"]
       [:th.normal "Začátek"]
       [:th.normal "Délka h:m:s"]]]
     (let [rezim-auto (get-in device-states [device-code "RezimAuto"])
           cas-nabehu (time-by-path [device-code "dtCasNabehu"] device-states)
           cas-spusteni (time-by-path [device-code "dtCasSpusteni"] device-states)
           cas-odstavovani (time-by-path [device-code "dtCasOdstavovani"] device-states)
           cas-odstaveni (time-by-path [device-code "dtCasOdstaveni"] device-states)
           eb1-time (or (time-by-path ["EB1" "Cas"] device-states) (js/Date.))]
       [:tbody
        (when cas-nabehu
          [:tr
           [:td "Náběh"]
           [:td
            (cljc.util/to-format cas-nabehu cljc.util/ddMMyyyyHHmm)]
           [:td.bold
            (cljc.util/since-days-hours-mins-sec cas-nabehu (if (= rezim-auto 1) eb1-time cas-spusteni))]])
        (when (and cas-spusteni (or (zero? rezim-auto) (> rezim-auto 1)))
          [:tr
           [:td "Spuštění"]
           [:td
            (cljc.util/to-format cas-spusteni cljc.util/ddMMyyyyHHmm)]
           [:td.bold
            (cljc.util/since-days-hours-mins-sec cas-spusteni (if (= rezim-auto 2) eb1-time cas-odstavovani))]])
        (when (and cas-odstavovani (or (zero? rezim-auto) (= rezim-auto 3)))
          [:tr
           [:td "Odstavování"]
           [:td
            (cljc.util/to-format cas-odstavovani cljc.util/ddMMyyyyHHmm)]
           [:td.bold
            (cljc.util/since-days-hours-mins-sec cas-odstavovani (if (= rezim-auto 3) eb1-time cas-odstaveni))]])
        (if (and cas-odstaveni (= rezim-auto 0))
          [:tr
           [:td "Odstavení"]
           [:td
            (cljc.util/to-format cas-odstaveni cljc.util/ddMMyyyyHHmm)]
           [:td.bold (cljc.util/since-days-hours-mins-sec cas-nabehu cas-odstaveni)]]
          [:tr
           [:td "Celkem"]
           [:td]
           [:td.bold (cljc.util/since-days-hours-mins-sec cas-nabehu eb1-time)]])])]))

(defn palivo-popup [& {:keys [device-no]}]
  (let [show?       (reagent/atom nil)
        device-code (str "TE" device-no)
        tos-code   (str "TOS_P" device-no)]
    (fn []
      (let [devices-by-code          @(re-frame/subscribe [:elevize.cljs.common/entities-by :device :code])
            device-states            @(re-frame/subscribe [:elevize.cljs.device-states/current])
            vars-by-device-code&name @(re-frame/subscribe [:elevize.cljs.device-states/vars-by-device-code&name])
            enums-by-group           @(re-frame/subscribe [:elevize.cljs.enum-item/by-group&order])
            user                     @(re-frame/subscribe [:auth-user])]
        [re-com/v-box :children
         [[:button.bold {:on-click #(reset! show? true)} "Nastavení"]
          (when @show?
            [re-com/modal-panel :backdrop-on-click #(reset! show? false) :child
             [re-com/v-box :class "body" :gap "10px" :align :center :children
              [[re-com/box :child
                [:table.table.tree-table.table-hover.table-striped.table-sm
                 [:tbody
                  (let [device (get devices-by-code tos-code)]
                    (into [:tr
                           ^{:key 1000}
                           [:td.buttons [:h5 (:title device)]]]
                          (status-table/device-status&commands device device-states vars-by-device-code&name enums-by-group (cljc.util/power-user? user))))]]]
               [re-com/box :width "800px" :child
                [:table.table.tree-table.table-hover.table-striped.table-sm
                 [:thead
                  [:tr
                   [:th "Palivo"]
                   [:th.normal "Aktuálně"]
                   [:th.normal "Nastavit"]
                   [:th.normal "KKS"]]]
                 [tr-for-path-bool
                  :label [tos-status :label"Chod" :device-code tos-code]
                  :path [device-code "PalivoOn"]
                  :path-err [device-code "ChybaPalivo"]
                  :kks-col? true]
                 [tr-for-path
                  :label "Frekvence pož. Hz"
                  :path [device-code "PalivoFreqReq"]
                  :kks-col? true]
                 [tr-for-path :label "Frekvence skut. Hz"
                  :path [device-code "IO_PalivoFreqAct"]
                  :read-only? true
                  :kks-col? true]
                 [tr-for-path
                  :label "Minimální otáčky Hz"
                  :path [device-code "iPOtackyMin"]
                  :kks-col? true]
                 [tr-for-path
                  :label "Maximální otáčky Hz"
                  :path [device-code "iPOtackyMax"]
                  :kks-col? true]
                 [pistnice vars-by-device-code&name device-states tos-code 1]
                 [pistnice vars-by-device-code&name device-states tos-code 2]
                 [tr-for-path-bool
                  :label "Šnek 1"
                  :path [tos-code "IO_Snek1"]
                  :path-act [tos-code "IO_Snek1_Chod"]
                  :path-err [tos-code "IO_Snek1_Error"]
                  :path-ready [tos-code "IO_Snek1_Ready"]
                  :kks-col? true]
                 [tr-for-path-bool
                  :label "Šnek 2"
                  :path [tos-code "IO_Snek2"]
                  :path-act [tos-code "IO_Snek2_Chod"]
                  :path-err [tos-code "IO_Snek2_Error"]
                  :path-ready [tos-code "IO_Snek2_Ready"]
                  :kks-col? true]]]
               [re-com/button :label "Zavřít" :on-click #(reset! show? false)]]]])]]))))

(defn odpopelneni-popup [& {:keys [device-no]}]
  (let [show?       (reagent/atom nil)
        device-code (str "TE" device-no)
        tos-code   (str "TOS_O" device-no)]
    (fn []
      (let [devices-by-code          @(re-frame/subscribe [:elevize.cljs.common/entities-by :device :code])
            device-states            @(re-frame/subscribe [:elevize.cljs.device-states/current])
            vars-by-device-code&name @(re-frame/subscribe [:elevize.cljs.device-states/vars-by-device-code&name])
            enums-by-group           @(re-frame/subscribe [:elevize.cljs.enum-item/by-group&order])
            user                     @(re-frame/subscribe [:auth-user])]
        [re-com/v-box :children
         [[:button.bold {:on-click #(reset! show? true)} "Nastavení"]
          (when @show?
            [re-com/modal-panel :backdrop-on-click #(reset! show? false) :child
             [re-com/v-box :class "body" :gap "10px" :align :center :children
              [[re-com/box :child
                [:table.table.tree-table.table-hover.table-striped.table-sm
                 [:tbody
                  (let [device (get devices-by-code tos-code)]
                    (into [:tr
                           ^{:key 1000}
                           [:td.buttons [:h5 (:title device)]]]
                          (status-table/device-status&commands device device-states vars-by-device-code&name enums-by-group (cljc.util/power-user? user))))]]]
               [re-com/box :width "800px" :child
                [:table.table.tree-table.table-hover.table-striped.table-sm
                 [:thead
                  [:tr
                   [:th "Odpopelnění"]
                   [:th.normal "Aktuálně"]
                   [:th.normal "Nastavit"]
                   [:th.normal "KKS"]]]
                 [pistnice vars-by-device-code&name device-states tos-code 1]
                 [pistnice vars-by-device-code&name device-states tos-code 2]
                 [tr-for-path-bool
                  :label "Šnek 1"
                  :path [tos-code "IO_Snek1"]
                  :path-act [tos-code "IO_Snek1_Chod"]
                  :path-err [tos-code "IO_Snek1_Error"]
                  :path-ready [tos-code "IO_Snek1_Ready"]
                  :kks-col? true]
                 [tr-for-path-bool
                  :label "Šnek 2"
                  :path [tos-code "IO_Snek2"]
                  :path-act [tos-code "IO_Snek2_Chod"]
                  :path-err [tos-code "IO_Snek2_Error"]
                  :path-ready [tos-code "IO_Snek2_Ready"]
                  :kks-col? true]
                 [tr-for-path-bool :label "Ventil popelnice"
                  :path [tos-code "IO_VentilPopelniceOtevren"]
                  :read-only? true
                  :kks-col? true]]]
               [re-com/button :label "Zavřít" :on-click #(reset! show? false)]]]])]]))))

(defn suche-chlazeni-popup []
  (let [show? (reagent/atom nil)
        device-code "VTSCH1"]
    (fn []
      (let [devices-by-code          @(re-frame/subscribe [:elevize.cljs.common/entities-by :device :code])
            device-states            @(re-frame/subscribe [:elevize.cljs.device-states/current])
            vars-by-device-code&name @(re-frame/subscribe [:elevize.cljs.device-states/vars-by-device-code&name])
            enums-by-group           @(re-frame/subscribe [:elevize.cljs.enum-item/by-group&order])
            user                     @(re-frame/subscribe [:auth-user])]
        [re-com/v-box :children
         [[:button.bold {:on-click #(reset! show? true)} "Nastavení"]
          (when @show?
            [re-com/modal-panel :backdrop-on-click #(reset! show? false) :child
             [re-com/v-box :class "body" :gap "10px" :align :center :children
              [[re-com/box :child
                [:table.table.tree-table.table-hover.table-striped.table-sm
                 [:tbody
                  (let [device (get devices-by-code device-code)]
                    (into [:tr
                           ^{:key 1000}
                           [:td.buttons [:h5 (:title device)]]]
                          (status-table/device-status&commands device device-states vars-by-device-code&name enums-by-group (cljc.util/power-user? user))))]]]
               [re-com/box :width "800px" :child
                [:table.table.tree-table.table-hover.table-striped.table-sm
                 [:thead
                  [:tr
                   [:th "Suché chlazení"]
                   [:th.normal "Aktuálně"]
                   [:th.normal "Nastavit"]
                   [:th.normal "KKS"]]]
                 [tr-for-path
                  :label "Počet aktivních ventilátorů"
                  :path [device-code "iPocetAkt"]
                  :kks-col? true]
                 [tr-for-path
                  :label "Otáčky čerpadel okruhu při manuálním řízení"
                  :path [device-code "rFreqCerpadlaManual"]
                  :kks-col? true]
                 [tr-for-path-bool
                  :label "Klapka uzavírající obtok kolem výměníku suchého chlazení"
                  :path [device-code "IO_SCH_KlapkaObtok"]
                  :klapka? true
                  :kks-col? true]
                 [tr-for-path-bool
                  :label "Klapka vstup"
                  :path [device-code "IO_SCH_KlapkaVstup"]
                  :klapka? true
                  :kks-col? true]
                 [tr-for-path
                  :label "Teplota vratu"
                  :path [device-code "IO_SCH_TeplotaVratu"]
                  :kks-col? true]
                 [tr-for-path
                  :label "Teplota za ventilátory"
                  :path [device-code "IO_SCH_TeplotaZaVentilatory"]
                  :kks-col? true]]]
               [re-com/button :label "Zavřít" :on-click #(reset! show? false)]]]])]]))))

(defn rozvody-chladu-popup []
  (let [show? (reagent/atom nil)
        device-code "VTRCH1"]
    (fn []
      (let [devices-by-code          @(re-frame/subscribe [:elevize.cljs.common/entities-by :device :code])
            device-states            @(re-frame/subscribe [:elevize.cljs.device-states/current])
            vars-by-device-code&name @(re-frame/subscribe [:elevize.cljs.device-states/vars-by-device-code&name])
            enums-by-group           @(re-frame/subscribe [:elevize.cljs.enum-item/by-group&order])
            user                     @(re-frame/subscribe [:auth-user])]
        [re-com/v-box :children
         [[:button.bold {:on-click #(reset! show? true)} "Nastavení"]
          (when @show?
            [re-com/modal-panel :backdrop-on-click #(reset! show? false) :child
             [re-com/v-box :class "body" :gap "10px" :align :center :children
              [[re-com/box :child
                [:table.table.tree-table.table-hover.table-striped.table-sm
                 [:tbody
                  (let [device (get devices-by-code device-code)]
                    (into [:tr
                           ^{:key 1000}
                           [:td.buttons [:h5 (:title device)]]]
                          (status-table/device-status&commands device device-states vars-by-device-code&name enums-by-group (cljc.util/power-user? user))))]]]
               [re-com/box :width "800px" :child
                [:table.table.tree-table.table-hover.table-striped.table-sm
                 [:thead
                  [:tr
                   [:th "Rozvody chladu"]
                   [:th.normal "Aktuálně"]
                   [:th.normal "Nastavit"]
                   [:th.normal "KKS"]]]
                 [tr-for-path
                  :label "Parametry pro řízení čerpadel podle teploty"
                  :path [device-code "itRizeniCerpadlaPodleTeploty"]
                  :kks-col? true]
                 [tr-for-path
                  :label "Otáčky čerpadel okruhu při manuálním řízení (musí být v rozsahu nastaveném na měniči)"
                  :path [device-code "rCerpadlaFreqReqManual"]
                  :kks-col? true]
                 [tr-for-path
                  :label "Řídící teplota"
                  :path [device-code "IO_RCH_RidiciTeplota"]
                  :kks-col? true]
                 [tr-for-path
                  :label "Teplota oleje 1"
                  :path [device-code "IO_RCH_TeplotaOleje01"]
                  :kks-col? true]
                 [tr-for-path
                  :label "Teplota oleje 2"
                  :path [device-code "IO_RCH_TeplotaOleje02"]
                  :kks-col? true]
                 [tr-for-path
                  :label "Teplota pláště turbíny 1"
                  :path [device-code "IO_RCH_TeplotaPlasteTurbiny1"]
                  :kks-col? true]
                 [tr-for-path
                  :label "Teplota pláště turbíny 2"
                  :path [device-code "IO_RCH_TeplotaPlasteTurbiny2"]
                  :kks-col? true]]]
               [re-com/button :label "Zavřít" :on-click #(reset! show? false)]]]])]]))))


(defn odtah-popup []
  (let [show? (reagent/atom nil)]
    (fn []
      (let [devices-by-code          @(re-frame/subscribe [:elevize.cljs.common/entities-by :device :code])
            device-states            @(re-frame/subscribe [:elevize.cljs.device-states/current])
            vars-by-device-code&name @(re-frame/subscribe [:elevize.cljs.device-states/vars-by-device-code&name])
            enums-by-group           @(re-frame/subscribe [:elevize.cljs.enum-item/by-group&order])
            user                     @(re-frame/subscribe [:auth-user])]
        [re-com/v-box :children
         [[:button.bold {:on-click #(reset! show? true)} "Nastavení"]
          (when @show?
            [re-com/modal-panel :backdrop-on-click #(reset! show? false) :child
             [re-com/v-box :class "body" :gap "10px" :align :center :children
              [[re-com/box :child
                [:table.table.tree-table.table-hover.table-striped.table-sm
                 [:tbody
                  (let [device (get devices-by-code "SVO1")]
                    (into [:tr
                           ^{:key 1000}
                           [:td.buttons [:h5 (:title device)]]]
                          (status-table/device-status&commands device device-states vars-by-device-code&name enums-by-group (cljc.util/power-user? user))))]]]
               [re-com/box :width "800px" :child
                [:table.table.tree-table.table-hover.table-striped.table-sm
                 [:thead
                  [:tr
                   [:th "Odtah"]
                   [:th.normal "Aktuálně"]
                   [:th.normal "Nastavit"]
                   [:th.normal "KKS"]]]
                 [tr-for-path-bool
                  :label "Bezp. kl. obtoku turbíny"
                  :path ["SVO1" "IO_KlapkaBezpecnostniTurbina2"]
                  :klapka? true
                  :obracena-logika? true
                  :kks-col? true]
                 [tr-for-path-bool
                  :label "Přisávání komínu"
                  :path ["SVO1" "IO_ServoKomin_Otv"]
                  :klapka? true
                  :kks-col? true]
                 [tr-for-path-bool
                  :label "Obtoková kl. SSV2"
                  :path ["SVO1" "IO_KlapkaObtokSSV2"]
                  :klapka? true
                  :kks-col? true]
                 [tr-for-path-bool
                  :label "Vstupni klapka SSV2"
                  :path ["SVO1" "IO_KlapkaVstupSSV2"]
                  :klapka? true
                  :kks-col? true]]]
               [re-com/box :child
                [:table.table.tree-table.table-hover.table-striped.table-sm
                 [:tbody
                  (let [device (get devices-by-code "SV1")]
                    (into [:tr
                           ^{:key 1000}
                           [:td.buttons [:h5 (:title device)]]]
                          (status-table/device-status&commands device device-states vars-by-device-code&name enums-by-group (cljc.util/power-user? user))))]]]
               [re-com/box :width "800px" :child
                [:table.table.tree-table.table-hover.table-striped.table-sm
                 [:thead
                  [:tr
                   [:th "Spalovací vzduch"]
                   [:th.normal "Aktuálně"]
                   [:th.normal "Nastavit"]
                   [:th.normal "KKS"]]]
                 [tr-for-path-bool
                  :label "Klapka těsná 3"
                  :path ["SV1" "IO_KlapkaTesna3"]
                  :path-err ["SV1" "IO_KlapkaTesna3_Error"]
                  :path-act ["SV1" (str "IO_KlapkaTesna3_DorazTrue")]
                  :klapka? true
                  :kks-col? true]
                 [tr-for-path-bool
                  :label "Klapka těsná 4"
                  :path ["SV1" "IO_KlapkaTesna4"]
                  :path-err ["SV1" "IO_KlapkaTesna4_Error"]
                  :path-act ["SV1" (str "IO_KlapkaTesna4_DorazTrue")]
                  :klapka? true
                  :kks-col? true]
                 [tr-for-path-bool
                  :label "Spalinová klapka 3"
                  :path ["SV1" "SpalinovaKlapka3153"]
                  :path-act ["SV1" "IO_SpalinovaKlapka3153_Zav"]
                  :klapka? true
                  :kks-col? true]
                 [tr-for-path-bool
                  :label "Spalinová klapka 4"
                  :path ["SV1" "SpalinovaKlapka3154"]
                  :path-act ["SV1" "IO_SpalinovaKlapka3154_Zav"]
                  :klapka? true
                  :kks-col? true]
                 [tr-for-path-bool
                  :label "Klapka turba 3"
                  :path ["SV1" "KlapkaTurb3"]
                  :path-act ["SV1" "IO_KlapkaTurb3_Otv"]
                  :klapka? true
                  :kks-col? true]
                 [tr-for-path-bool
                  :label "Klapka turba 4"
                  :path ["SV1" "KlapkaTurb4"]
                  :path-act ["SV1" "IO_KlapkaTurb4_Otv"]
                  :klapka? true
                  :kks-col? true]]]
               [re-com/button :label "Zavřít" :on-click #(reset! show? false)]]]])]]))))
