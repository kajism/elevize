(ns elevize.cljs.device-states
  (:require #_[clojure.core.rrb-vector :as rrbv]
            [clojure.string :as str]
            [elevize.cljc.calc-derived :as cljc.calc-derived]
            [elevize.cljc.util :as cljc.util]
            [elevize.cljs.alarm-history :as alarm-history]
            [elevize.cljs.comp.data-table :as data-table]
            [elevize.cljs.comp.status-table :as status-table]
            [elevize.cljs.comp.zplynovac :as zplynovac]
            [elevize.cljs.dygraph :as dygraph]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.sente :refer [server-call]]
            [elevize.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [reagent.ratom :as ratom]
            [secretary.core :as secretary]
            [taoensso.timbre :as timbre]))

(re-frame/reg-sub
 ::history
 (fn [db [_]]
   (::history db)))

(re-frame/reg-sub-raw
 ::current
 (fn [db [_]]
   (let [history (re-frame/subscribe [::history])
         current-x (re-frame/subscribe [:elevize.cljs.common/path-value [:elevize.cljs.dygraph/selected-x]])]
     (ratom/reaction
      (if-not @current-x
        (peek @history)
        (let [current-inst (js/Date. @current-x)]
          (get @history (cljc.util/bin-search @history #(compare current-inst (get-in % ["EB1" "Cas"]))))))))))

(re-frame/reg-event-db
 ::load
 util/debug-mw
 (fn [db [_]]
   (server-call [:elevize/load-device-states]
                [::update])
   db))

(def stale-sec 5)

(re-frame/reg-sub-raw
 ::time
 (fn [db [_]]
   (let [device-states (re-frame/subscribe [::current])]
     (ratom/reaction
      {:instant (get-in @device-states ["EB1" "Cas"])
       :fresh-data? (and (::last-update-sec @db) (< (::last-update-sec @db) stale-sec))
       :last-update-sec (::last-update-sec @db)}))))

(re-frame/reg-event-db
 ::increment-last-update-sec
 (fn [db [_]]
   (update db ::last-update-sec (fn [s]
                                     (when (= s stale-sec)
                                       (.. js/document (getElementById "disconnected-sound") (play)))
                                     (inc s)))))

(def max-history-count 8000)
(def history-gc-count (int (/ max-history-count 10)))

(re-frame/reg-event-db
 ::update
 ;;util/debug-mw
 (fn [db [_ changes]]
   #_(timbre/debug (str ::device-states-change) changes)
   (if-not (::last-update-sec db)
     (js/setInterval #(re-frame/dispatch [::increment-last-update-sec])
                     1000) ;; install last update incrementer
     (when (> (get db ::last-update-sec) stale-sec) ;; connection restored
       (.. js/document (getElementById "connected-sound") (play))))

   (cond-> (assoc db ::last-update-sec 0)
     (seq changes)
     (update ::history (fn [xs]
                         (cond-> (cljc.calc-derived/merge-device-states-history
                                  #_(or xs (rrbv/vector))
                                  (or xs [])
                                  changes
                                  (:elevize.cljs.power-avg-settings/avg-settings db))
                           (> (count xs) max-history-count)
                           #_(rrbv/subvec (- (count xs) (- max-history-count history-gc-count)) (count xs))
                           (-> (subvec (- (count xs)
                                          (- max-history-count history-gc-count))
                                       (count xs))
                               (seq)
                               (vec))))))
   ))

(re-frame/reg-event-db
 ::set-variable
 util/debug-mw
 (fn [db [_ var new-value]]
   (let [devices (:device db)
         device-code (->> var :device-id (get devices) :code)
         current-value (get-in (peek (::history db)) [device-code (:name var)])]
     (when (not= (str current-value) new-value)
       (server-call [:elevize/set-device-state-variable {:device-code device-code
                                                         :var-name (:name var)
                                                         :var-set-name (:set-name var)
                                                         :value new-value}]
                    [:set-msg :info]))
     db)))

(re-frame/reg-event-db
 ::save-var-group
 util/debug-mw
 (fn [db [_ var-group-id var-group-name var-ids append?]]
   (server-call [(if append?
                   :var-group/append-vars
                   :var-group/save)
                 (cond-> {:member-ids var-ids}
                   (not var-group-id)
                   (assoc :title var-group-name)
                   var-group-id
                   (assoc :id var-group-id))]
                [::saved-var-group (get-in db [::variables-filter :device-code])])
   db))

(re-frame/reg-event-db
 ::saved-var-group
 util/debug-mw
 (fn [db [_ device-code ent-id]]
   (re-frame/dispatch [::variables-filter-change [:var-group-id device-code] ent-id])
   (re-frame/dispatch [:set-msg :info "Záznam byl uložen"])
   db))

(re-frame/reg-sub-raw
 ::variables-filter
 (fn [db [_]]
   (ratom/reaction
    (::variables-filter @db))))

(re-frame/reg-event-db
 ::variables-filter-change
 util/debug-mw
 (fn [db [_ kws val]]
   (assoc-in db (into [::variables-filter] kws) val)))

(re-frame/reg-sub-raw
 ::vars-by-device-code&name
 (fn [db [_]]
   (let [vars (re-frame/subscribe [:entities :variable])
         devices (re-frame/subscribe [:entities :device])]
     (ratom/reaction
      (reduce (fn [out var]
                (assoc-in out [(some-> (:device-id var) (@devices) :code)
                               (:name var)] var))
              {}
              (vals @vars))))))

(re-frame/reg-sub-raw
 ::selected-device
 (fn [db [_]]
   (let [filters (re-frame/subscribe [::variables-filter])
         devices-by-code (re-frame/subscribe [:elevize.cljs.common/entities-by :device :code])]
     (ratom/reaction
      (get @devices-by-code (:device-code @filters))))))

(re-frame/reg-sub-raw
 ::filtered-device-vars-indexed
 (fn [db [_]]
   (let [selected-device (re-frame/subscribe [::selected-device])
         vars (re-frame/subscribe [:entities :variable])
         vars-by-device-code&name (re-frame/subscribe [::vars-by-device-code&name])
         table-id (re-frame/subscribe [::visible-table-id])]
     (ratom/reaction
      (let [checked-ids (data-table/checked-ids @(re-frame/subscribe [:table-state @table-id]))]
        (cond
          (and @selected-device (seq @vars-by-device-code&name) (empty? checked-ids))
          (->> (str/split (:var-header @selected-device) #"\n")
               (map-indexed (fn [idx var-name]
                              (-> (get-in @vars-by-device-code&name [(:code @selected-device) var-name])
                                  (assoc :idx (inc idx))))))
          (and @selected-device (seq checked-ids))
          (->> (vals @vars)
               (filter (fn [var]
                         (or (contains? checked-ids (:id var))
                             (= (:id @selected-device) (:device-id var))))))
          :else
          @vars))))))

(def table-search-colls (atom {}))

(defn- get-table-search-colls [device-code]
  (if-let [out (get @table-search-colls device-code)]
    out
    (let [out (reagent/atom {})]
      (swap! table-search-colls assoc device-code out)
      out)))

(re-frame/reg-event-db
 ::reset-table-search-colls
 util/debug-mw
 (fn reset-table-search-colls [db [_ new-state]]
   (reset! (get-table-search-colls (get-in db [::variables-filter :device-code])) (or new-state {}))
   db))

(re-frame/reg-sub-raw
 ::visible-table-id
 (fn [db [-]]
   (let [filters (re-frame/subscribe [::variables-filter])]
     (ratom/reaction (keyword "device-states" (:device-code @filters))))))

(defn filters-comp []
  (let [var-group-name (reagent/atom "")
        var-groups (re-frame/subscribe [:entities :var-group])]
    (fn [devices table-id filters]
      (let [var-group-id (get-in @filters [:var-group-id (:device-code @filters)])
            table-state @(re-frame/subscribe [:table-state table-id])]
        [re-com/h-box :gap "5px" :align :center :children
         [[:h4 "Stavy"]
          [:div.dropdown
           [re-com/single-dropdown
            :choices (->> @devices
                          vals
                          (util/sort-by-locale :title)
                          (cons {:code "vse" :title "--- všechna zařízení ---"}))
            :label-fn :title
            :id-fn :code
            :model (:device-code @filters)
            :on-change #(set! js/window.location.hash (str "#/stavy/" %))
            :filter-box? true
            :width "400px"]]
          [re-com/label :label "Skupiny:"]
          [:div.dropdown
           [re-com/single-dropdown
            :choices (->> @var-groups
                          vals
                          (util/sort-by-locale :title)
                          (cons {:id nil :title "--- nová skupina ---"}))
            :label-fn :title
            :model var-group-id
            :placeholder "--- nová skupina ---"
            :on-change (fn [group-id]
                         (re-frame/dispatch [::variables-filter-change [:var-group-id (:device-code @filters)] group-id])
                         (re-frame/dispatch [:table-state-change table-id :row-states (->> (get @var-groups group-id)
                                                                                           :member-ids
                                                                                           (map (fn [id] [id {:checked? true}]))
                                                                                           (into {}))])
                         (re-frame/dispatch [::reset-table-search-colls {-1 (boolean group-id)}]))
            :filter-box? true
            :width (if (not var-group-id) "200px" "400px")]]
          (when-not var-group-id
            [re-com/input-text
             :model var-group-name
             :change-on-blur? false
             :on-change #(reset! var-group-name %)])
          [re-com/button
           :label "Uložit"
           :class "btn-success"
           :disabled? (and (not var-group-id) (str/blank? @var-group-name))
           :on-click #(do
                        (re-frame/dispatch [::save-var-group var-group-id @var-group-name (data-table/checked-ids table-state)])
                        (reset! var-group-name ""))]
          (when  var-group-id
            [re-com/md-icon-button :md-icon-name "zmdi-plus-circle"
             :on-click #(do
                          (re-frame/dispatch [::save-var-group var-group-id @var-group-name (data-table/checked-ids table-state) true])
                          (reset! var-group-name ""))
             :tooltip "Přidat proměnné do skupiny (žádné neodstraní)"])]]))))

(defn charts-comp []
  (let [chart-var-ids (reagent/atom [])
        devices (re-frame/subscribe [:entities :device])
        vars (re-frame/subscribe [:entities :variable])]
    (fn [table-state]
      [:div
       (when-let [ids (not-empty (data-table/checked-ids table-state))]
         [re-com/button :label "Zkopíruj graf" :on-click #(swap! chart-var-ids conj ids)])
       (let [charts-var-ids (map-indexed vector (cons (data-table/checked-ids table-state) @chart-var-ids))]
         [re-com/v-box :children
          (mapv (fn [row]
                  [re-com/h-box :children
                   (mapv (fn [[idx var-ids]]
                           [re-com/v-box :align :end :children
                            [[re-com/md-icon-button :md-icon-name "zmdi-close-circle"
                              :on-click #(swap! chart-var-ids (fn [$]
                                                                (into (subvec $ 0 (dec idx))
                                                                      (subvec $ idx))))
                              :tooltip "Zrušit graf"
                              :disabled? (zero? idx)]
                             ^{:key (str/join ";" var-ids)}
                             [dygraph/variables-state-plotter (map #(let [var (get @vars %)]
                                                                      [(:code (get @devices (:device-id var))) (:name var)])
                                                                   var-ids)
                              :showLabelsOnHighlight true]]])
                         row)])
                (partition-all 2 charts-var-ids))])])))

(defn page []
  (let [device-states (re-frame/subscribe [::current])
        devices (re-frame/subscribe [:entities :device])
        selected-device (re-frame/subscribe [::selected-device])
        vars (re-frame/subscribe [:entities :variable])
        filters (re-frame/subscribe [::variables-filter])
        enums-by-group (re-frame/subscribe [:elevize.cljs.enum-item/by-group&order])
        vars-by-device-code&name (re-frame/subscribe [::vars-by-device-code&name])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      (if-not (and @vars @devices @enums-by-group @filters (cljc.util/power-user? @user))
        [re-com/throbber]
        (let [table-id @(re-frame/subscribe [::visible-table-id])
              table-state @(re-frame/subscribe [:table-state table-id])
              filtered-vars (re-frame/subscribe [::filtered-device-vars-indexed])]
          [:div
           [status-table/status-table (:device-code @filters)]
           [:br]
           (when (not= "vse" (:device-code @filters))
             [:table.table.tree-table.table-hover.table-striped
              [:tbody
               [:tr
                (status-table/device-status&commands @selected-device @device-states @vars-by-device-code&name @enums-by-group (cljc.util/power-user? @user))]]])
           [filters-comp devices table-id filters]
           ^{:key table-id}
           [data-table/data-table
            :table-id table-id
            :search-colls (get-table-search-colls (:device-code @filters))
            :colls [["Pořadí" :idx]
                    ["Zařízení" #(-> % :device-id (@devices) :code)]
                    [[re-com/label :label "KKS" :width "100px"] :kks]
                    {:header "Jméno proměnné"
                     :val-fn :name
                     :td-comp (fn [& {:keys [value row]}]
                                [:td {:class (if (or (str/includes? value "Error")
                                                     (str/includes? value "Chyba"))
                                               "error"
                                               "")}
                                 value])}
                    {:header "Hodnota v PLC"
                     :val-fn #(let [value (get-in @device-states [(-> % :device-id (@devices) :code) (:name %)])]
                                (or (some-> % :data-type (@enums-by-group) (get value) :label) value))
                     :td-comp (fn [& {:keys [value row]}]
                                (if-not value
                                  [:td ""]
                                  (cond
                                    (and (= "BOOL" (:data-type row)) (some? value))
                                    [:td {:class (str (if (= value 0) "false" "true")
                                                      " text-right")}
                                     (str value)]
                                    (= "INTERPOL_TABLE" (:data-type row))
                                    [:td
                                     (doall
                                      (for [x (str/split value #":")]
                                        ^{:key x}
                                        [:div x]))]
                                    :else
                                    [data-table/td-comp* value false])))}
                    ["Nastavit hodnotu"
                     (fn [row]
                       (when (:set-name row)
                         [zplynovac/edit-value row (get-in @device-states [(-> row :device-id (@devices) :code) (:name row)]) @enums-by-group]))]
                    ["Datový typ" :data-type]
                    ["Komentář" #(vector :span {:class "text-nowrap"} (:comment %))]
                    [[re-com/v-box :children
                      [[re-com/md-icon-button :md-icon-name "zmdi-close-circle"
                        :on-click #(re-frame/dispatch [::reset-table-search-colls]) :tooltip "Zrušit fitry"]
                       [re-com/md-icon-button
                        :md-icon-name "zmdi-refresh"
                        :tooltip "Aktualizovat celý stav"
                        :tooltip-position :left-center
                        :on-click #(re-frame/dispatch [::load])]]] #(-> "") :none]]
            :rows filtered-vars
            :row-checkboxes? true
            :order-by 0]
           [charts-comp table-state]])))))

(pages/add-page ::page #'page)

(secretary/defroute "/stavy" []
  (re-frame/dispatch [:set-current-page ::page]))

(secretary/defroute "/stavy/:device-code" [device-code]
  (re-frame/dispatch [:set-current-page ::page])
  (re-frame/dispatch [::variables-filter-change [:device-code] device-code]))

(secretary/defroute "/stavy/:device-code/:kks" [device-code kks]
  (re-frame/dispatch [:set-current-page ::page])
  (re-frame/dispatch [::variables-filter-change [:device-code] device-code])
  (re-frame/dispatch [::reset-table-search-colls {2 kks}]))
