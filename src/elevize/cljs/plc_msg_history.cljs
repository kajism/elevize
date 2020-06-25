(ns elevize.cljs.plc-msg-history
  (:require [clojure.string :as str]
            [elevize.cljc.util :as cljc.util]
            [elevize.cljs.common :as common]
            [elevize.cljs.comp.buttons :as buttons]
            [elevize.cljs.comp.data-table :as data-table]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]
            [taoensso.timbre :as timbre]))

(defn page-plc-msg-history []
  (let [items (re-frame/subscribe [:entities :plc-msg-history])]
    (fn []
      [:div
       [:h3 "Historie komunikace s řídící jednotkou"]
       (if-not @items
         [re-com/throbber]
         [data-table/data-table
          :table-id :plc-msg-history
          :colls [[[re-com/md-icon-button
                    :md-icon-name "zmdi-refresh"
                    :tooltip "Načíst ze serveru"
                    :on-click #(re-frame/dispatch [:entities-load :plc-msg-history])]
                   (fn [row] "")
                   :none]
                  {:header "Datum"
                   :val-fn :created
                   :td-comp (fn [& {:keys [value row row-state]}]
                              [:td
                               (cljc.util/to-format value cljc.util/ddMMyyyyHHmmss)])}
                  ["Uživatel" :user-login]
                  ["Příkaz" :req]
                  ["Odpověď" :resp]]
          :rows items
          :desc? true])])))

(pages/add-page :plc-msg-history #'page-plc-msg-history)

(secretary/defroute "/komunikace" []
  (re-frame/dispatch [:set-current-page :plc-msg-history]))

(common/add-kw-url :plc-msg-history "plc-msg-history")
