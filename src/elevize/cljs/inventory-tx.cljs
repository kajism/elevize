(ns elevize.cljs.inventory-tx
  (:require [clojure.string :as str]
            [elevize.cljc.util :as cljc.util]
            [elevize.cljs.common :as common]
            [elevize.cljs.comp.buttons :as buttons]
            [elevize.cljs.comp.data-table :as data-table]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]))

(defn form [tx]
  (let [items @(re-frame/subscribe [:entities :inventory-item])]
    [:div
     [:div.form-group
      [:label "Položka"][:br]
      [re-com/single-dropdown
       :choices (vec (sort-by :name (vals items)))
       :label-fn :name
       :model (:item-id tx)
       :on-change #(re-frame/dispatch [:entity-change :inventory-tx (:id tx) :item-id %])
       :placeholder "Vyberte položku"
       :filter-box? true
       :width "400px"]]
     [:div.form-group
      [:label "Změna ks (+/- sklad)"]
      [re-com/input-text
       :model (str (:pcs tx))
       :on-change #(re-frame/dispatch [:entity-change :inventory-tx (:id tx) :delta-pcs (util/parse-int %)])
       :validation-regex #"^-?[0-9]{0,3}$"
       :width "100px"]]
     [:label "Poznámka"]
     [re-com/input-text
      :model (str (:note tx))
      :on-change #(re-frame/dispatch [:entity-change :inventory-tx (:id tx) :note %])
      :width "400px"]
     [:br]
     [re-com/h-box
      :gap "5px"
      :children
      [[re-com/button :label "Uložit" :class "btn-success" :on-click #(re-frame/dispatch [:entity-save :inventory-tx])]
       (when (:id tx)
         [re-com/hyperlink-href :label [re-com/button :label "Nový"] :href (str "#/sklad-pohyb/e")])
       [re-com/hyperlink-href :label [re-com/button :label "Seznam"] :href (str "#/sklad-pohyby")]]]]))

(defn detail [tx]
  (let [items @(re-frame/subscribe [:entities :inventory-item])]
    [:div
     [:label "Kdy"]
     [:p (cljc.util/to-format (:created tx) cljc.util/ddMMyyyyHHmmss)]
     [:label "Kdo"]
     [:p (:user-login tx)]
     [:label "Název položky"]
     [:p (some-> tx :item-id items :name)]
     [:label "Změna ks"]
     [:p (str (:delta-pcs tx))]
     [:label "Poznámka"]
     [:p (str (:note tx))]
     [re-com/hyperlink-href :label [re-com/button :label "Položka"] :href (str "#/sklad/" (:item-id tx) "e")]
     [re-com/hyperlink-href :label [re-com/button :label "Pohyby"] :href (str "#/sklad-pohyby")]]))

(defn page-inventory-txs []
  (let [txs (re-frame/subscribe [:entities :inventory-tx])
        items (re-frame/subscribe [:entities :inventory-item])
        user (re-frame/subscribe [:auth-user])
        table-state (re-frame/subscribe [:table-state :inventory-txs])]
    (fn []
      [:div
       [:h3 "Pohyby skladu"]
       (if-not (and @txs @items)
         [re-com/throbber]
         [data-table/data-table
          :table-id :inventory-txs
          :colls [[[re-com/h-box :gap "5px" :justify :end
                    :children
                    [(when ((:-rights @user) :inventory-tx/save)
                       [re-com/md-icon-button
                        :md-icon-name "zmdi-plus-square"
                        :tooltip "Přidat"
                        :on-click #(set! js/window.location.hash "#/sklad-pohyb/e")])
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Přenačíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :inventory-tx])]]]
                   (fn [row]
                     (when (and (= (:id row) (:selected-row-id @table-state)))
                       [re-com/h-box
                        :gap "5px" :justify :end
                        :children
                        [#_[re-com/hyperlink-href
                          :href (str "#/sklad-pohyb/" (:id row))
                          :label [re-com/md-icon-button
                                  :md-icon-name "zmdi-view-web"
                                  :tooltip "Detail"]]
                         (when ((:-rights @user) :inventory-tx/delete)
                           [re-com/hyperlink-href
                            :href (str "#/sklad-pohyb/" (:id row) "e")
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]])
                         (when ((:-rights @user) :inventory-tx/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :inventory-tx (:id row)])])]]))
                   :none]
                  ["Kdy" :created]
                  ["Kdo" :user-login]
                  ["Název položky"
                   (fn [row]
                     (let [name (some->> row :item-id (get @items) :name)]
                       (if (and (= (:id row) (:selected-row-id @table-state)))
                         [re-com/hyperlink-href
                          :href (str "#/sklad/" (:item-id row) "e")
                          :label name]
                         name)))]
                  ["Změna ks" :delta-pcs :sum]
                  ["Poznámka" :note]]
          :rows txs])])))

(defn page-inventory []
  (let [edit? (re-frame/subscribe [:entity-edit? :inventory-tx])
        user (re-frame/subscribe [:auth-user])
        item (re-frame/subscribe [:entity-edit :inventory-tx])]
    (fn []
      [:div
       [:h3 "Pohyb skladu"]
       (if (and @edit? ((:-rights @user) :inventory-tx/save) (not (:id @item)))
         [form @item]
         [detail @item])])))

(pages/add-page :inventory-txs #'page-inventory-txs)
(pages/add-page :inventory-tx #'page-inventory)

(secretary/defroute "/sklad-pohyby" []
  (re-frame/dispatch [:set-current-page :inventory-txs]))

(secretary/defroute #"/sklad-pohyb/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :inventory-tx (util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :inventory-tx]))

(common/add-kw-url :inventory-tx "sklad-pohyb")
