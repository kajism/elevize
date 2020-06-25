(ns elevize.cljs.inventory-item
  (:require [clojure.string :as str]
            [elevize.cljc.util :as cljc.util]
            [elevize.cljs.common :as common]
            [elevize.cljs.comp.buttons :as buttons]
            [elevize.cljs.comp.data-table :as data-table]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.util :as util]
            [reagent.ratom :as ratom]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]))

(re-frame/reg-sub-raw
 ::pcs-by-item-id
 (fn [db [_]]
   (let [txs (re-frame/subscribe [:entities :inventory-tx])]
     (ratom/reaction
      (->> @txs
           (vals)
           (reduce (fn [out tx]
                     (update out (:item-id tx) #(+ (or % 0) (:delta-pcs tx))))
                   {}))))))

(re-frame/reg-sub-raw
 ::txs-by-item-id
 (fn [db [_ item-id]]
   (let [txs (re-frame/subscribe [:entities :inventory-tx])]
     (ratom/reaction
      (->> @txs
           (vals)
           (filter #(= item-id (:item-id %))))))))

(defn form-new [item]
  [:div
   [:div.form-group
    [:label "Název položky"]
    [re-com/input-text
     :model (str (:name item))
     :on-change #(re-frame/dispatch [:entity-change :inventory-item (:id item) :name %])
     :width "400px"]]
   [:label "Poznámka"]
   [re-com/input-text
    :model (str (:note item))
    :on-change #(re-frame/dispatch [:entity-change :inventory-item (:id item) :note %])
    :width "400px"]
   [:br]
   [re-com/h-box
    :gap "5px"
    :children
    [[re-com/button :label "Uložit" :class "btn-success" :on-click #(re-frame/dispatch [:entity-save :inventory-item])]
     (when (:id item)
       [re-com/hyperlink-href :label [re-com/button :label "Nový"] :href (str "#/sklad/e")])
     [re-com/hyperlink-href :label [re-com/button :label "Seznam"] :href (str "#/sklad")]]]])

(defn detail [item]
  (let [pcs-by-item-id @(re-frame/subscribe [::pcs-by-item-id])
        user @(re-frame/subscribe [:auth-user])]
    [:div
     ;; [:label "Kdy"]
     ;; [:p (cljc.util/to-format (:created item) cljc.util/ddMMyyyyHHmmss)]
     [:label "Název položky"]
     [:p (str (:name item))]
     [:label "Počet ks"]
     [:p (get pcs-by-item-id (:id item))]
     [:label "Poznámka"]
     [:p (str (:note item))]
     [:h4 "Pohyby skladu"]
     [data-table/data-table
      :table-id :item-txs
      :colls [[(when ((:-rights user) :inventory-tx/save)
                 [re-com/md-icon-button
                  :md-icon-name "zmdi-plus-square"
                  :tooltip "Přidat pohyb"
                  :on-click #(do
                               (re-frame/dispatch [:entity-new :inventory-tx {:item-id (:id item)}])
                               (set! js/window.location.hash "#/sklad-pohyb/e"))])
               #(-> "")
               :none]
              ["Kdy" :created]
              ["Kdo" :user-login]
              ["Změna ks" :delta-pcs :sum]
              ["Poznámka" :note]]
      :rows (re-frame/subscribe [::txs-by-item-id (:id item)])]
     [re-com/hyperlink-href :label [re-com/button :label "Seznam"] :href (str "#/sklad")]]))

(defn page-inventory-items []
  (let [items (re-frame/subscribe [:entities :inventory-item])
        user (re-frame/subscribe [:auth-user])
        table-state (re-frame/subscribe [:table-state :inventory-items])
        pcs-by-item-id (re-frame/subscribe [::pcs-by-item-id])]
    (fn []
      [:div
       [:h3 "Skladové položky"]
       (if-not @items
         [re-com/throbber]
         [data-table/data-table
          :table-id :inventory-items
          :colls [[[re-com/h-box :gap "5px" :justify :end
                    :children
                    [(when ((:-rights @user) :inventory-item/save)
                       [re-com/md-icon-button
                        :md-icon-name "zmdi-plus-square"
                        :tooltip "Přidat"
                        :on-click #(set! js/window.location.hash "#/sklad/e")])
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Přenačíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :inventory-item])]]]
                   (fn [row]
                     (when (and (= (:id row) (:selected-row-id @table-state)))
                       [re-com/h-box
                        :gap "5px" :justify :end
                        :children
                        [#_[re-com/hyperlink-href
                          :href (str "#/sklad/" (:id row))
                          :label [re-com/md-icon-button
                                  :md-icon-name "zmdi-view-web"
                                  :tooltip "Detail"]]
                         (when ((:-rights @user) :inventory-item/save)
                           [re-com/hyperlink-href
                            :href (str "#/sklad/" (:id row) "e")
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]])
                         (when ((:-rights @user) :inventory-item/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :inventory-item (:id row)])])]]))
                   :none]
                  #_["Kdy" :created]
                  ["Název položky" :name]
                  ["Počet ks" #(->> % :id (get @pcs-by-item-id)) :sum]
                  ["Poznámka" :note]]
          :rows items])])))

(defn page-inventory []
  (let [edit? (re-frame/subscribe [:entity-edit? :inventory-item])
        item (re-frame/subscribe [:entity-edit :inventory-item])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Skladová položka"]
       (if (and @edit? ((:-rights @user) :inventory-item/save) (not (:id @item)))
         [form-new @item]
         [detail @item])])))

(pages/add-page :inventory-items #'page-inventory-items)
(pages/add-page :inventory-item #'page-inventory)

(secretary/defroute "/sklad" []
  (re-frame/dispatch [:set-current-page :inventory-items]))

(secretary/defroute #"/sklad/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :inventory-item (util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :inventory-item]))

(common/add-kw-url :inventory-item "sklad")
