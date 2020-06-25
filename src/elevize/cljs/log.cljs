(ns elevize.cljs.log
  (:require [clojure.string :as str]
            [elevize.cljs.common :as common]
            [elevize.cljs.comp.buttons :as buttons]
            [elevize.cljs.comp.data-table :as data-table]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]))

(defn table [items user]
  [:div
   (when ((:-rights user) :log/save)
     [:div
      [re-com/hyperlink-href :label [re-com/button :label "Nový"] :href (str "#/log/e")]
      [:br]
      [:br]])
   (if-not @items
     [re-com/throbber]
     [data-table/data-table
      :table-id :logs
      :colls [["Kdy" :created]
              ["Název" :title]
              [[re-com/md-icon-button
                :md-icon-name "zmdi-refresh"
                :tooltip "Načíst ze serveru"
                :on-click #(re-frame/dispatch [:entities-load :log])]
               (fn [row]
                 [re-com/h-box
                  :gap "5px"
                  :children
                  [[re-com/hyperlink-href
                    :href (str "#/log/" (:id row))
                    :label [re-com/md-icon-button
                            :md-icon-name "zmdi-view-web"
                            :tooltip "Detail"]]
                   (when ((:-rights user) :log/save)
                     [re-com/hyperlink-href
                      :href (str "#/log/" (:id row) "e")
                      :label [re-com/md-icon-button
                              :md-icon-name "zmdi-edit"
                              :tooltip "Editovat"]])
                   (when ((:-rights user) :log/delete)
                     [buttons/delete-button #(re-frame/dispatch [:entity-delete :log (:id row)])])]])
               :none]]
      :rows items
      :order-by 0])])

(defn detail [item user]
  [:div
   [:label "Id"]
   [:p (str (:id item))]
   [:label "Název"]
   [:p (str (:title item))]
   [:label "Text"]
   [:p (str (:text item))]
   [:label "Text 2"]
   [:p (str (:text2 item))]
   [re-com/hyperlink-href :label [re-com/button :label "Seznam"] :href (str "#/logs")]])

(defn page-logs []
  (let [items (re-frame/subscribe [:entities :log])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Logy"]
       (if-not @items
         [re-com/throbber]
         [table items @user])])))

(defn page-log []
  (let [edit? (re-frame/subscribe [:entity-edit? :variable])
        item (re-frame/subscribe [:entity-edit :log])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Log"]
       [detail @item @user]])))


(pages/add-page :logs #'page-logs)
(pages/add-page :log #'page-log)

(secretary/defroute "/logs" []
  (re-frame/dispatch [:set-current-page :logs]))

(secretary/defroute #"/log/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :log (util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :log]))

(common/add-kw-url :log "log")
