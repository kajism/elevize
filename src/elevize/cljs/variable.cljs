(ns elevize.cljs.variable
  (:require [clojure.string :as str]
            [elevize.cljc.enums :as enums]
            [elevize.cljs.common :as common]
            [elevize.cljs.comp.buttons :as buttons]
            [elevize.cljs.comp.data-table :as data-table]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]))

(defn form [item devices]
  [:div
   [:form {:role "form"}
    [:div.form-group
     [:label "Zařízení"]
     [:div.dropdown
      [re-com/single-dropdown
       :choices (util/sort-by-locale :title (vals devices))
       :label-fn :title
       :model (:device-id item)
       :on-change #(re-frame/dispatch [:entity-change :variable (:id item) :device-id %])
       :placeholder "Vybrat subsytém"
       :width "400px"]]]
    [:div.form-group
     [:label "Jméno"]
     [re-com/input-text
      :class "form-control"
      :model (str (:name item))
      :on-change #(re-frame/dispatch [:entity-change :variable (:id item) :name %])
      :width "400px"]]
    [:div.form-group
     [:label "Jméno pro zápis (je prázdné, když je proměnná jen pro čtení)"]
     [re-com/input-text
      :class "form-control"
      :model (str (:set-name item))
      :on-change #(re-frame/dispatch [:entity-change :variable (:id item) :set-name %])
      :width "400px"]]
    [:div.form-group
     [:label "KKS"]
     [re-com/input-text
      :class "form-control"
      :model (str (:kks item))
      :on-change #(re-frame/dispatch [:entity-change :variable (:id item) :kks %])
      :width "400px"]]
    [:div.form-group
     [:label "Datový typ"]
     [:div.dropdown
      [re-com/single-dropdown
       :choices (mapv #(hash-map :id % :label %) enums/data-types)
       :model (:data-type item)
       :on-change #(re-frame/dispatch [:entity-change :variable (:id item) :data-type %])
       :placeholder "Vybrat datový typ"
       :width "400px"]]]
    [:div.form-group
     [:label "Komentář"]
     [re-com/input-textarea
      :class "form-control"
      :model (str (:comment item))
      :on-change #(re-frame/dispatch [:entity-change :variable (:id item) :comment %])
      :width "400px"]]]
   [re-com/h-box
    :gap "5px"
    :children
    [[re-com/button :label "Uložit" :class "btn-success" :on-click #(re-frame/dispatch [:entity-save :variable])]
     (when (:id item)
       [re-com/hyperlink-href :label [re-com/button :label "Nový"] :href (str "#/variable/e")])
     [re-com/hyperlink-href :label [re-com/button :label "Seznam"] :href (str "#/variables")]]]])

(defn detail [item devices user]
  [:div
   [:label "Zařízení"]
   [:p (str (some-> item :device-id devices :title))]
   [:label "Jméno"]
   [:p (str (:name item))]
   [:label "Jméno pro zápis (je prázdné, když je proměnná jen pro čtení)"]
   [:p (str (:set-name item))]
   [:label "KKS"]
   [:p (str (:kks item)) [:br]]
   [:label "Datový typ"]
   [:p (str (:data-type item)) [:br]]
   [:label "Komentář"]
   [:p (str (:comment item)) [:br]]
   [re-com/hyperlink-href :label [re-com/button :label "Seznam"] :href (str "#/variables")]])

(defn page-variables []
  (let [items (re-frame/subscribe [:entities :variable])
        devices (re-frame/subscribe [:entities :device])
        user (re-frame/subscribe [:auth-user])
        table-state (re-frame/subscribe [:table-state :variables])]
    (fn []
      [:div
       [:h3 "Proměnné"]
       (if-not (and @items @devices)
         [re-com/throbber]
         [data-table/data-table
          :table-id :variables
          :colls [[[re-com/h-box :gap "5px" :justify :end
                    :children
                    [(when ((:-rights @user) :variable/save)
                       [re-com/md-icon-button
                        :md-icon-name "zmdi-plus-square"
                        :tooltip "Přidat"
                        :on-click #(set! js/window.location.hash "#/variable/e")])
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Přenačíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :variable])]]]
                   (fn [row]
                     (when (and (= (:id row) (:selected-row-id @table-state)))
                       [re-com/h-box
                        :gap "5px" :justify :end
                        :children
                        [#_[re-com/hyperlink-href
                            :href (str "#/variable/" (:id row))
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-view-web"
                                    :tooltip "Detail"]]
                         (when ((:-rights @user) :variable/save)
                           [re-com/hyperlink-href
                            :href (str "#/variable/" (:id row) "e")
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]])
                         (when ((:-rights @user) :variable/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :variable (:id row)])])]]))
                   :none]
                  ["Zařízení" #(some-> % :device-id (@devices) :code)]
                  ["Jméno" :name]
                  ["Jméno pro zápis" :set-name]
                  ["Datový typ" :data-type]
                  ["KKS" :kks]
                  ["Komentář" :comment]]
          :rows items])])))

(defn page-variable []
  (let [edit? (re-frame/subscribe [:entity-edit? :variable])
        item (re-frame/subscribe [:entity-edit :variable])
        devices (re-frame/subscribe [:entities :device])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Proměnná"]
       (if-not @devices
         [re-com/throbber]
         (if (and @edit? ((:-rights @user) :variable/save))
           [form @item @devices]
           [detail @item @devices @user]))])))

(pages/add-page :variables #'page-variables)
(pages/add-page :variable #'page-variable)

(secretary/defroute "/variables" []
  (re-frame/dispatch [:set-current-page :variables]))

(secretary/defroute #"/variable/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :variable (util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :variable]))

(common/add-kw-url :variable "variable")
