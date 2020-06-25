(ns elevize.cljs.var-group
  (:require [clojure.string :as str]
            [elevize.cljs.common :as common]
            [elevize.cljs.comp.buttons :as buttons]
            [elevize.cljs.comp.data-table :as data-table]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]))

(defn form [item]
  [:div
   [:div.form-group
    [:label "Název"]
    [re-com/input-text
     :model (str (:title item))
     :on-change #(re-frame/dispatch [:entity-change :var-group (:id item) :title %])
     :width "400px"]]
   [re-com/h-box
    :gap "5px"
    :children
    [[re-com/button :label "Uložit" :class "btn-success" :on-click #(re-frame/dispatch [:entity-save :var-group])]
     #_(when (:id item)
       [re-com/hyperlink-href :label [re-com/button :label "Nová"] :href (str "#/var-group/e")])
     [re-com/hyperlink-href :label [re-com/button :label "Seznam"] :href (str "#/var-groups")]]]])

(defn detail [item user]
  [:div
   [:label "Název"]
   [:p (str (:title item)) [:br]]
   [re-com/hyperlink-href :label [re-com/button :label "Seznam"] :href (str "#/var-groups")]])

(defn table []
  (let [items (re-frame/subscribe [:entities :var-group])
        user (re-frame/subscribe [:auth-user])
        table-state (re-frame/subscribe [:table-state :var-groups])]
    (fn []
      [:div
       [:h3 "Skupiny proměnných"]
       (if-not @items
         [re-com/throbber]
         [data-table/data-table
          :table-id :var-groups
          :colls [[[re-com/h-box :gap "5px" :justify :end
                    :children
                    [(when ((:-rights @user) :var-group/save)
                       [re-com/md-icon-button
                        :md-icon-name "zmdi-plus-square"
                        :tooltip "Přidat"
                        :on-click #(set! js/window.location.hash "#/var-group/e")])
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Přenačíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :var-group])]]]
                   (fn [row]
                     (when (and (= (:id row) (:selected-row-id @table-state)))
                       [re-com/h-box
                        :gap "5px" :justify :end
                        :children
                        [(when ((:-rights @user) :var-group/save)
                           [re-com/hyperlink-href
                            :href (str "#/var-group/" (:id row) "e")
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]])
                         (when ((:-rights @user) :var-group/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :var-group (:id row)])])]]))
                   :none]
                  ["Název" :title]
                  ["Počet proměnných" #(count (:member-ids %))]]
          :rows items])])))

(defn item []
  (let [edit? (re-frame/subscribe [:entity-edit? :var-group])
        item (re-frame/subscribe [:entity-edit :var-group])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Skupina proměnných"]
       (if (and @edit? ((:-rights @user) :var-group/save))
         [form @item]
         [detail @item @user])])))

(pages/add-page ::table #'table)
(pages/add-page ::item #'item)

(secretary/defroute "/var-groups" []
  (re-frame/dispatch [:set-current-page ::table]))

(secretary/defroute #"/var-group/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :var-group (util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page ::item]))

(common/add-kw-url :var-group "var-group")
