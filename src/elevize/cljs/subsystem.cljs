(ns elevize.cljs.subsystem
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
    [:label "Kód"]
    [re-com/input-text
     :model (str (:code item))
     :on-change #(re-frame/dispatch [:entity-change :subsystem (:id item) :code %])
     :width "400px"]]
   [:div.form-group
    [:label "Název"]
    [re-com/input-text
     :model (str (:title item))
     :on-change #(re-frame/dispatch [:entity-change :subsystem (:id item) :title %])
     :width "400px"]]
   [re-com/h-box
    :gap "5px"
    :children
    [[re-com/button :label "Uložit" :class "btn-success" :on-click #(re-frame/dispatch [:entity-save :subsystem])]
     (when (:id item)
       [re-com/hyperlink-href :label [re-com/button :label "Nový"] :href (str "#/subsystem/e")])
     [re-com/hyperlink-href :label [re-com/button :label "Seznam"] :href (str "#/subsystems")]]]])

(defn detail [item user]
  [:div
   [:label "Kód"]
   [:p (str (:code item))]
   [:label "Název"]
   [:p (str (:title item)) [:br]]
   [re-com/hyperlink-href :label [re-com/button :label "Seznam"] :href (str "#/subsystems")]])

(defn page-subsystems []
  (let [items (re-frame/subscribe [:entities :subsystem])
        user (re-frame/subscribe [:auth-user])
        table-state (re-frame/subscribe [:table-state :subsystems])]
    (fn []
      [:div
       [:h3 "Subsystémy"]
       (if-not @items
         [re-com/throbber]
         [data-table/data-table
          :table-id :subsystems
          :colls [[[re-com/h-box :gap "5px" :justify :end
                    :children
                    [(when ((:-rights @user) :subsystem/save)
                       [re-com/md-icon-button
                        :md-icon-name "zmdi-plus-square"
                        :tooltip "Přidat"
                        :on-click #(set! js/window.location.hash "#/subsystem/e")])
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Přenačíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :subsystem])]]]
                   (fn [row]
                     (when (and (= (:id row) (:selected-row-id @table-state)))
                       [re-com/h-box
                        :gap "5px" :justify :end
                        :children
                        [#_[re-com/hyperlink-href
                            :href (str "#/subsystem/" (:id row))
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-view-web"
                                    :tooltip "Detail"]]
                         (when ((:-rights @user) :subsystem/save)
                           [re-com/hyperlink-href
                            :href (str "#/subsystem/" (:id row) "e")
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]])
                         (when ((:-rights @user) :subsystem/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :subsystem (:id row)])])]]))
                   :none]
                  ["Kód" :code]
                  ["Název" :title]]
          :rows items])])))

(defn page-subsystem []
  (let [edit? (re-frame/subscribe [:entity-edit? :subsystem])
        item (re-frame/subscribe [:entity-edit :subsystem])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Subsystem"]
       (if (and @edit? ((:-rights @user) :subsystem/save))
         [form @item]
         [detail @item @user])])))

(pages/add-page :subsystems #'page-subsystems)
(pages/add-page :subsystem #'page-subsystem)

(secretary/defroute "/subsystems" []
  (re-frame/dispatch [:set-current-page :subsystems]))

(secretary/defroute #"/subsystem/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :subsystem (util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :subsystem]))

(common/add-kw-url :subsystem "subsystem")
