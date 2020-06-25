(ns elevize.cljs.import-xlsx
  (:require [elevize.cljs.util :as util]
            [elevize.cljs.sente :refer [server-call]]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.comp.buttons :as buttons]
            [elevize.cljs.comp.data-table :refer [ data-table]]
            [clojure.string :as str]))

(re-frame/reg-sub
 ::status
 (fn [db [_]]
   (::status db)))

(re-frame/reg-event-db
 ::download-and-import-xlsx
 util/debug-mw
 (fn [db [_]]
   (server-call [:import-xlsx/download-and-import]
                [::download-and-import-xlsx-finished])
   (assoc db ::status "Probíhá...")))

(re-frame/reg-event-db
 ::download-and-import-xlsx-finished
 util/debug-mw
 (fn [db [_ msg]]
   (assoc db ::status msg)))

(re-frame/reg-event-db
 ::rollback-last
 util/debug-mw
 (fn [db [_ id]]
   (server-call [:import-xlsx/rollback-last id]
                [::download-and-import-xlsx-finished])
   (assoc db ::status "Probíhá...")))

(re-frame/reg-sub
 ::reception-errors
 (fn [db [_]]
   (let [errors @(re-frame/subscribe [:entities :reception-error])]
     (->> (vals errors)
          (remove (comp str/blank? str :errors))))))

(defn page-import-xlsx []
  (let [user (re-frame/subscribe [:auth-user])
        imports (re-frame/subscribe [:entities :import-xlsx])
        reception-errors (re-frame/subscribe [::reception-errors])]
    (fn []
      (if-not ((:-rights @user) :import-xlsx/download-and-import)
        [re-com/throbber]
        [re-com/v-box :gap "5px" :children
         [[:h3 "Import XLSX"]
          [re-com/button :label "Načíst konfiguraci z Google Drive" :class "btn-danger" :on-click #(re-frame/dispatch [::download-and-import-xlsx])]
          (when-let [status @(re-frame/subscribe [::status])]
            [re-com/alert-box :alert-type :info :heading "Stav" :body status])
          [:h3 "Chyby při příjmu dat"]
          [data-table
           :table-id :reception-errors
           :colls [[[re-com/md-icon-button
                     :md-icon-name "zmdi-refresh"
                     :tooltip "Přenačíst ze serveru"
                     :on-click #(re-frame/dispatch [:entities-load :reception-error])]
                    #(-> "")
                    :none]
                   ["Zařízení" :id]
                   ["Chyby" :errors]]
           :rows reception-errors]
          [:h3 "Historie importů"]
          (when (and ((:-rights @user) :import-xlsx/rollback-last) (> (count @imports) 1))
            [:div [re-com/button :label "Zrušit poslední import"
                   :on-click #(re-frame/dispatch [::rollback-last (apply max (keys @imports))])]
             " (vrátit zpět na předposlední import)"])
          [data-table
           :table-id :import-xlsxs
           :colls [[[re-com/md-icon-button
                     :md-icon-name "zmdi-refresh"
                     :tooltip "Přenačíst ze serveru"
                     :on-click #(re-frame/dispatch [:entities-load :import-xlsx])]
                    #(-> "")
                   :none]
                   ["ID" :id]
                   ["Datum" :date]
                   ["Provedl" :user-login]]
           :rows imports
           :desc? true]]]))))

(pages/add-page :import-xlsx #'page-import-xlsx)

(secretary/defroute "/import-xlsx" []
  (re-frame/dispatch [:set-current-page :import-xlsx]))
