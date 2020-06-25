(ns elevize.cljs.comp.data-table
  (:require [clojure.string :as str]
            [cognitect.transit :as transit]
            [elevize.cljc.util :as cljc.util]
            [elevize.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [reagent.ratom :as ratom]
            [taoensso.timbre :as timbre]))

(def default-rows-per-page 25)

(re-frame/reg-sub-raw
 :table-state
 (fn [db [_ table-id]]
   (ratom/reaction (get-in @db [:table-states table-id]))))

(defn show-date [value]
  (let [s (cljc.util/to-format value cljc.util/ddMMyyyyHHmmss)]
    (if (re-find #" 0[12]:00:00$" s)
      (subs s 0 (- (count s) 9))
      s)))

(defn checked-ids [table-state]
  (->> table-state
       :row-states
       (keep (fn [[id {ch? :checked?}]]
               (when ch? id)))
       set))

(re-frame/reg-sub-raw
 :table-rows
 (fn [db [_ table-id colls] [orig-rows]]
   (let [state (re-frame/subscribe [:table-state table-id])
         rows  (ratom/reaction
                ((if (map? orig-rows)
                   vals
                   identity)
                 orig-rows))
         sort-key-fn* (ratom/reaction
                       (:val-fn (get colls (:order-by @state))))
         sort-fns (ratom/reaction
                   (let [sort-key-fn (when @sort-key-fn*
                                       (cond->> @sort-key-fn*
                                         (transit/bigdec? (some-> @rows first (@sort-key-fn*)))
                                         (comp util/parse-float #(when % (.-rep %)))
                                         (vector? (some-> @rows first (@sort-key-fn*)))
                                         (comp util/hiccup->val)))]
                     [sort-key-fn
                      (if (and sort-key-fn (string? (some-> @rows first sort-key-fn)))
                        util/sort-by-locale
                        sort-by)]))
         search-colls (ratom/reaction
                       (:search-colls @state))
         rows (ratom/reaction
               #_(timbre/debug (str ::filtering))
               (reduce
                (fn [out coll-idx]
                  (let [s (some-> (get @search-colls coll-idx) str str/lower-case)
                        f (:val-fn (get colls coll-idx))]
                    (if (str/blank? s)
                      out
                      (filterv
                       (fn [row]
                         (let [v (f row)
                               v (cond
                                   (or (true? v) (false? v))
                                   (util/boolean->text v)
                                   (= js/Date (type v))
                                   (show-date v)
                                   (vector? v)
                                   "[]"
                                   :else
                                   (str v))]
                           (-> v str/lower-case (str/index-of s))))
                       out))))
                (if-not (get @search-colls -1)
                  @rows
                  (let [ids (checked-ids @state)]
                    (filter #(->> % :id (contains? ids))
                            @rows)))
                (keys colls)))
         rows (ratom/reaction
               #_(timbre/debug (str ::sorting))
               (let [[sort-key-fn sort-fn]  @sort-fns]
                 (if (and sort-fn sort-key-fn)
                   (sort-fn (comp util/hiccup->val sort-key-fn) @rows)
                   @rows)))
         row-from (ratom/reaction
                   (* (or (:rows-per-page @state) 0) (:page-no @state)))
         row-to (ratom/reaction
                 (if (:rows-per-page @state)
                   (min (+ @row-from (:rows-per-page @state)) (count @rows))
                   (count @rows)))
         desc? (ratom/reaction
                (:desc? @state))
         final-rows (ratom/reaction
                     (->> (cond-> @rows
                            @desc?
                            reverse)
                          (drop @row-from)
                          (take (- @row-to @row-from))))]
     (ratom/reaction
      {:final-rows @final-rows
       :filtered-rows @rows
       :row-from @row-from
       :row-to @row-to}))))

(re-frame/reg-event-db
 :table-state-set
 util/debug-mw
 (fn [db [_ table-id state]]
   (assoc-in db [:table-states table-id] state)))

(re-frame/reg-event-db
 :table-state-change
 ;;util/debug-mw
 (fn [db [_ table-id key val]]
   ((if (fn? val) update-in assoc-in) db [:table-states table-id key] val)))

(defn make-csv [rows colls]
  (let [colls (->> (vals colls)
                   (remove #(contains? #{:none :csv-export} (:header-modifier %))))]
    (str (str/join ";" (map :header colls)) "\n"
         (apply str
                (for [row rows]
                  (str (->> colls
                            (map :val-fn)
                            (map #(% row))
                            (map #(cond
                                    (= js/Date (type %))
                                    (show-date %)
                                    (transit/bigdec? %)
                                    (util/parse-int (.-rep %))
                                    (= (type %) js/Boolean)
                                    (util/boolean->text %)
                                    :else
                                    %))
                            (str/join ";"))
                       "\n"))))))

(defn td-comp* [value buttons?]
  [:td {:class (str #_"text-nowrap"
                    (when buttons? " buttons")
                    (when (or (number? value) (transit/bigdec? value)) " text-right"))}
   (cond
     (or (string? value) (vector? value)) value
     (= js/Date (type value)) (show-date value)
     (pos-int? value) (util/money->text value)
     (transit/bigdec? value) (util/money->text (util/parse-int (.-rep value)))
     (= (type value) js/Boolean) (util/boolean->text value)
     :else (str value))])

(defn tr-comp [& {:keys [colls row change-state-fn selected? row-checkboxes? checked?]}]
  (let [on-enter #(change-state-fn :selected-row-id (:id row))]
    [:tr {:on-mouse-enter on-enter}
     (when row-checkboxes?
         [:td [re-com/checkbox
               :model checked?
               :on-change #(change-state-fn :row-states
                                            (fn [row-states]
                                              (update row-states (:id row) update :checked? not)))]])
     (doall
      (for [[coll-idx {:keys [val-fn td-comp]}] colls
            :let [value (val-fn row)]]
        (if td-comp
          ^{:key coll-idx}
          [td-comp :value value :row row :row-state {:selected? selected?}]
          ^{:key coll-idx}
          [td-comp* value (= coll-idx 0)])))]))

(defn vector-coll->map [[header val-fn header-modifier]]
  {:header header
   :val-fn val-fn
   :header-modifier header-modifier})

(defn data-table [& {:keys [table-id order-by desc? rows-per-page row-checkboxes? rows colls search-colls] :as args}]
  (let [order-by (or order-by 1)
        colls (into {} (->> colls
                            (keep identity)
                            (map #(if (vector? %)
                                    (vector-coll->map %)
                                    %))
                            (map-indexed vector)))
        init-state {:order-by order-by
                    :desc? (or desc? false)
                    :search-all ""
                    :search-colls (if search-colls @search-colls {})
                    :rows-per-page (or rows-per-page default-rows-per-page)
                    :page-no 0}
        state (if table-id
                (re-frame/subscribe [:table-state table-id])
                (reagent/atom init-state))
        table-rows (re-frame/subscribe [:table-rows table-id colls] [rows])
        table-name (if table-id (name table-id) (str "data-table" (rand-int 1000)))
        change-state-fn (if table-id
                          (fn [key val] (re-frame/dispatch [:table-state-change table-id key val]))
                          (fn [key val] (swap! state (if (fn? val) update assoc) key val)))
        on-click-order-by #(do
                             (if (= (:order-by @state) %)
                               (change-state-fn :desc? not)
                               (do
                                 (change-state-fn :order-by %)
                                 (change-state-fn :desc? false)))
                             (change-state-fn :page-no 0))
        on-change-rows-per-page (fn [evt]
                                  (change-state-fn :rows-per-page (util/parse-int (-> evt .-target .-value)))
                                  (change-state-fn :page-no 0))
        on-change-search-all (fn [evt]
                               (change-state-fn :search-all (-> evt .-target .-value))
                               (change-state-fn :page-no 0))
        search-colls (or search-colls (reagent/atom {}))
        on-change-search-colls (fn []
                                 (change-state-fn :search-colls @search-colls)
                                 (change-state-fn :page-no 0))]
    (add-watch search-colls :search-colls
               (fn [_ _ _ new-state]
                 (js/setTimeout #(when (= new-state @search-colls)
                                   (on-change-search-colls))
                                250)))
    (when-not @state
      (re-frame/dispatch [:table-state-set table-id init-state]))
    (when (and @state (empty? @search-colls))
      (reset! search-colls (:search-colls @state)))
    (fn data-table-render []
      (if-not @state
        [re-com/throbber]
        [:div.data-table-component
         [:table.table.tree-table.table-hover.table-striped
          [:thead
           [:tr
            (when row-checkboxes?
              (let [all-checked? (not-empty (checked-ids @state))]
                [:th
                 [re-com/v-box :gap "15px" :children
                  [[re-com/checkbox
                    :model (get @search-colls -1)
                    :on-change #(swap! search-colls assoc -1 %)]
                   [re-com/checkbox
                    :model all-checked?
                    :on-change #(let [new-val (not all-checked?)]
                                  (change-state-fn :row-states
                                                   (fn [row-states]
                                                     (into {} (map (fn [{id :id}]
                                                                     [id (assoc (get row-states id) :checked? new-val)])
                                                                   (if new-val
                                                                     (:final-rows @table-rows)
                                                                     @rows))))))]]]]))
            (doall
             (for [[coll-idx {:keys [header val-fn header-modifier]}] colls]
               ^{:key coll-idx}
               [:th.text-nowrap
                (when (or (= :filter header-modifier) (not header-modifier))
                  [:input.form-control
                   {:type "text"
                    :value (str (get @search-colls coll-idx))
                    :on-change #(swap! search-colls assoc coll-idx (-> % .-target .-value))
                    :on-blur #(on-change-search-colls)
                    :on-key-press #(when (= (.-charCode %) 13)
                                     (on-change-search-colls))}])
                (when (= :sum header-modifier)
                  [:div.suma [:span {:dangerously-set-inner-HTML {:__html "&Sigma; "}}]
                   (util/money->text
                    (->> (:filtered-rows @table-rows)
                         (keep val-fn)
                         (map #(if (transit/bigdec? %)
                                 (util/parse-float (.-rep %))
                                 %))
                         (apply +)
                         int)) [:br]])
                (if (#{:none :csv-export} header-modifier)
                  [re-com/h-box :gap "5px" :justify :end
                   :children
                   [header
                    (when (= :csv-export header-modifier)
                      [:a {:id (str "download-" table-name)}
                       [re-com/md-icon-button :md-icon-name "zmdi-download" :tooltip "Export do CSV"
                        :on-click (fn []
                                    (let [anchor (.getElementById js/document (str "download-" table-name))]
                                      (set! (.-href anchor) (str "data:text/plain;charset=utf-8," (js/encodeURIComponent (make-csv (:filtered-rows @table-rows) colls))))
                                      (set! (.-download anchor) (str table-name ".csv"))))]])]]
                  [:a {:on-click #(on-click-order-by coll-idx)}
                   [:span header]
                   [:span (if (not= (:order-by @state) coll-idx)
                            ""
                            (if (:desc? @state)
                              [re-com/md-icon-button :md-icon-name "zmdi-chevron-up" :tooltip "seřadit opačně" :size :smaller]
                              [re-com/md-icon-button :md-icon-name "zmdi-chevron-down" :tooltip "seřadit opačně" :size :smaller]))]])]))]]
          [:tbody
           (doall
            (map-indexed
             (fn [idx row]
               ^{:key (or (:id row) idx)}
               [tr-comp
                :colls colls
                :row-checkboxes? row-checkboxes?
                :change-state-fn change-state-fn
                :row row
                :selected? (= (:db/id row) (:selected-row-id @state))
                :checked? (get-in @state [:row-states (:id row) :checked?])])
             (:final-rows @table-rows)))]]
         (when (> (count @rows) 5)
           [:div
            [:span (str "Zobrazuji " (inc (:row-from @table-rows)) " - " (:row-to @table-rows) " z "
                        (count (:filtered-rows @table-rows)) " záznamů")
             (if (< (count (:filtered-rows @table-rows)) (count @rows))
               (str " (vyfiltrováno z celkem " (count @rows) " záznamů)"))]
            [:span
             ". Maximální počet řádků na stránce je "
             [:select {:size 1 :value (str (:rows-per-page @state)) :on-change on-change-rows-per-page}
              (for [x ["vše" 5 10 15 25 50 100 200 300 400 500]]
                ^{:key x} [:option {:value (if (string? x) "" x)} x])]]
            ;;          [:div.dataTables_filter
            ;;           [:label "Search"
            ;;           [:input {:type "text" :value (:search-all @state) :on-change #(on-change-search-all %)}]]]
            ])
         (when (> (count (:filtered-rows @table-rows)) (count (:final-rows @table-rows)))
           [:ul.pager
            [:li.previous
             [:a {:class (str "" (when (= (:row-from @table-rows) 0) "btn disabled"))
                  :on-click #(change-state-fn :page-no dec)}
              "Předchozí"]]
            [:li.next
             [:a {:class (str "" (when (= (:row-to @table-rows) (count (:filtered-rows @table-rows))) "btn disabled"))
                  :on-click #(change-state-fn :page-no inc)}
              "Následující"]]])]))))
