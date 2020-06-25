(ns elevize.cljs.enum-item
  (:require [clojure.string :as str]
            [elevize.cljs.common :as common]
            [elevize.cljs.comp.buttons :as buttons]
            [elevize.cljs.comp.data-table :as data-table]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]
            [reagent.ratom :as ratom]))

(re-frame/reg-sub
 ::by-group&order
 (fn [_]
   (let [items @(re-frame/subscribe [:entities :enum-item])]
     (->> (vals items)
          (group-by :group-name)
          (map (fn [[gn xs]]
                 [gn (->> xs (map (juxt :order-pos identity)) (into {}))]))
          (into {})))))

(defn form [item]
  [:div
   [:div.form-group
    [:label "Název skupiny"]
    [re-com/input-text
     :model (str (:group-name item))
     :on-change #(re-frame/dispatch [:entity-change :enum-item (:id item) :group-name %])
     :width "400px"]
    [:label "Název"]
    [re-com/input-text
     :model (str (:name item))
     :on-change #(re-frame/dispatch [:entity-change :enum-item (:id item) :name %])
     :width "400px"]
    [:label "Pořadí"]
    [re-com/input-text
     :model (str (:order-pos item))
     :on-change #(re-frame/dispatch [:entity-change :enum-item (:id item) :order-pos (util/parse-int %)])
     :width "400px"]
    [:label "Popis"]
    [re-com/input-text
     :model (str (:label item))
     :on-change #(re-frame/dispatch [:entity-change :enum-item (:id item) :label %])
     :width "400px"]]
   [re-com/h-box
    :gap "5px"
    :children
    [[re-com/button :label "Uložit" :class "btn-success" :on-click #(re-frame/dispatch [:entity-save :enum-item])]
     #_(when (:id item)
       [re-com/hyperlink-href :label [re-com/button :label "Nová"] :href (str "#/enum-item/e")])
     [re-com/hyperlink-href :label [re-com/button :label "Seznam"] :href (str "#/enum-items")]]]])

(defn detail [item user]
  [:div
   [:label "Název skupiny"]
   [:p (str (:group-name item)) [:br]]
   [:label "Název"]
   [:p (str (:name item)) [:br]]
   [:label "Pořadí"]
   [:p (str (:order-pos item)) [:br]]
   [:label "Popis"]
   [:p (str (:label item)) [:br]]
   [re-com/hyperlink-href :label [re-com/button :label "Seznam"] :href (str "#/enum-items")]])

(defn page-enum-items []
  (let [items (re-frame/subscribe [:entities :enum-item])
        user (re-frame/subscribe [:auth-user])
        table-state (re-frame/subscribe [:table-state :enum-items])]
    (fn []
      [:div
       [:h3 "Výčtové položky"]
       (if-not @items
         [re-com/throbber]
         [data-table/data-table
          :table-id :enum-items
          :colls [[[re-com/h-box :gap "5px" :justify :end
                    :children
                    [(when ((:-rights @user) :enum-item/save)
                       [re-com/md-icon-button
                        :md-icon-name "zmdi-plus-square"
                        :tooltip "Přidat"
                        :on-click #(set! js/window.location.hash "#/enum-item/e")])
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Přenačíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :enum-item])]]]
                   (fn [row]
                     (when (and (= (:id row) (:selected-row-id @table-state)))
                       [re-com/h-box
                        :gap "5px" :justify :end
                        :children
                        [(when ((:-rights @user) :enum-item/save)
                           [re-com/hyperlink-href
                            :href (str "#/enum-item/" (:id row) "e")
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]])
                         (when ((:-rights @user) :enum-item/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :enum-item (:id row)])])]]))
                   :none]
                  ["Název skupiny" :group-name]
                  ["Název" :name]
                  ["Pořadí" :order-pos]
                  ["Popis" :label]]
          :rows items])])))

(defn page-enum-item []
  (let [edit? (re-frame/subscribe [:entity-edit? :enum-item])
        item (re-frame/subscribe [:entity-edit :enum-item])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Výčtová položka"]
       (if (and @edit? ((:-rights @user) :enum-item/save))
         [form @item]
         [detail @item @user])])))

(pages/add-page :enum-items #'page-enum-items)
(pages/add-page :enum-item #'page-enum-item)

(secretary/defroute "/enum-items" []
  (re-frame/dispatch [:set-current-page :enum-items]))

(secretary/defroute #"/enum-item/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :enum-item (util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :enum-item]))

(common/add-kw-url :enum-item "enum-item")
