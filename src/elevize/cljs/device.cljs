(ns elevize.cljs.device
  (:require [clojure.string :as str]
            [elevize.cljs.common :as common]
            [elevize.cljs.comp.buttons :as buttons]
            [elevize.cljs.comp.data-table :as data-table]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]))

(defn form [item subsystems]
  [:div
   [:div.form-group
    [:label "Subsystém"]
    [:div.dropdown
     [re-com/single-dropdown
      :choices (util/sort-by-locale :title (vals subsystems))
      :label-fn :title
      :model (:subsystem-id item)
      :on-change #(re-frame/dispatch [:entity-change :device (:id item) :subsystem-id %])
      :placeholder "Vybrat subsystem"
      :width "400px"]]]
   [:div.form-group
    [:label "Kód"]
    [re-com/input-text
     :class "form-control"
     :model (str (:code item))
     :on-change #(re-frame/dispatch [:entity-change :device (:id item) :code %])
     :width "400px"]]
   [:div.form-group
    [:label "Název"]
    [re-com/input-text
     :class "form-control"
     :model (str (:title item))
     :on-change #(re-frame/dispatch [:entity-change :device (:id item) :title %])
     :width "400px"]]
   [:div.form-group
    [:label "Hlavička čtení proměnných"]
    [re-com/input-textarea
     :model (str (:var-header item))
     :on-change #(re-frame/dispatch [:entity-change :device (:id item) :var-header %])
     :width "400px"
     :rows 15]]
   [re-com/h-box
    :gap "5px"
    :children
    [[re-com/button :label "Uložit" :class "btn-success" :on-click #(re-frame/dispatch [:entity-save :device])]
     (when (:id item)
       [re-com/hyperlink-href :label [re-com/button :label "Nový"] :href (str "#/device/e")])
     [re-com/hyperlink-href :label [re-com/button :label "Seznam"] :href (str "#/devices")]]]])

(defn detail [item user subsystems]
  [:div
   [:label "Kód subsystému"]
   [:p (:code (get subsystems (:subsystem-id item)))]
   [:label "Kód zařízení"]
   [:p (str (:code item))]
   [:label "Název"]
   [:p (str (:title item)) [:br]]
   [:label "Číslo"]
   [:p (str (:device-num item)) [:br]]
   [:label "Hlavička čtení proměnných"]
   [:pre (str (:var-header item))]
   [re-com/hyperlink-href :label [re-com/button :label "Seznam"] :href (str "#/devices")]])

(defn page-devices []
  (let [items (re-frame/subscribe [:entities :device])
        user (re-frame/subscribe [:auth-user])
        ;;subsystems (re-frame/subscribe [:entities :subsystem])
        table-state (re-frame/subscribe [:table-state :devices])]
    (fn []
      [:div
       [:h3 "Zařízení"]
       (if-not @items
         [re-com/throbber]
         [data-table/data-table
          :table-id :devices
          :colls [[[re-com/h-box :gap "5px" :justify :end
                    :children
                    [(when ((:-rights @user) :device/save)
                       [re-com/md-icon-button
                        :md-icon-name "zmdi-plus-square"
                        :tooltip "Přidat"
                        :on-click #(set! js/window.location.hash "#/device/e")])
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Přenačíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :device])]]]
                   (fn [row]
                     (when (and (= (:id row) (:selected-row-id @table-state)))
                       [re-com/h-box
                        :gap "5px" :justify :end
                        :children
                        [[re-com/hyperlink-href
                          :href (str "#/device/" (:id row))
                          :label [re-com/md-icon-button
                                  :md-icon-name "zmdi-view-web"
                                  :tooltip "Detail"]]
                         (when ((:-rights @user) :device/save)
                           [re-com/hyperlink-href
                            :href (str "#/device/" (:id row) "e")
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]])
                         (when ((:-rights @user) :device/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :device (:id row)])])]]))
                   :none]
                  #_["Kód subsystému" #(some-> % :subsystem-id (@subsystems) :code)]
                  ["Kód zařízení" :code]
                  ["Název" :title]]
          :rows items
          :order-by 2])])))

(defn page-device []
  (let [edit? (re-frame/subscribe [:entity-edit? :device])
        item (re-frame/subscribe [:entity-edit :device])
        user (re-frame/subscribe [:auth-user])
        subsystems (re-frame/subscribe [:entities :subsystem])]
    (fn []
      [:div
       [:h3 "Zařízení"]
       (if-not  @subsystems
         [re-com/throbber]
         (if (and @edit? ((:-rights @user) :device/save))
           [form @item @subsystems]
           [detail @item @user @subsystems]))])))

(pages/add-page :devices #'page-devices)
(pages/add-page :device #'page-device)

(secretary/defroute "/devices" []
  (re-frame/dispatch [:set-current-page :devices]))

(secretary/defroute #"/device/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :device (util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :device]))

(common/add-kw-url :device "device")
