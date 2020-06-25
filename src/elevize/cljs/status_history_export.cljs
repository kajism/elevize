(ns elevize.cljs.status-history-export
  (:require [clojure.string :as str]
            [elevize.cljc.util :as cljc.util]
            [elevize.cljs.common :as common]
            [elevize.cljs.comp.buttons :as buttons]
            [elevize.cljs.comp.data-table :as data-table]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [secretary.core :as secretary]
            [cljs-time.core :as t]))

(defn page [item]
  (let [var-groups (re-frame/subscribe [:entities :var-group])
        fields (reagent/atom {:date-from (cljc.util/to-format (js/Date.) cljc.util/dMyyyy)
                              :time-from "00:00"
                              :time-to "00:00"})
        user (re-frame/subscribe [:auth-user])]
    (fn []
      (if-not (cljc.util/admin? @user)
        [re-com/throbber]
        [:div
         [:h3 "Export stavů do CSV"]
         [:div.form-group
          [re-com/label :label "Skupina proměnných"]
          [:div.dropdown
           [re-com/single-dropdown
            :choices (->> @var-groups
                          vals
                          (util/sort-by-locale :title))
            :placeholder "Vyberte skupinu proměnných pro export"
            :label-fn :title
            :model (:var-group-id @fields)
            :on-change (fn [x] (swap! fields #(assoc % :var-group-id x)))
            :filter-box? true
            :width "400px"]]
          [re-com/label :label "Od"]
          [re-com/h-box :gap "2px" :children
           [[re-com/input-text
             :model (str (:date-from @fields))
             :on-change (fn [x] (swap! fields #(assoc % :date-from (cljc.util/full-dMyyyy x))))
             :validation-regex #"^\d{0,2}$|^\d{0,2}\.\d{0,2}$|^\d{0,2}\.\d{0,2}\.\d{0,4}$"
             :width "100px"]
            [re-com/input-text :width "60px"
             :model (str (:time-from @fields))
             :on-change (fn [x] (swap! fields #(assoc % :time-from (cljc.util/full-HHmm x))))
             :validation-regex #"^(\d{0,2}):?(\d{1,2})?$"]]]
          [re-com/label :label "Do"]
          [re-com/h-box :gap "2px" :children
           [[re-com/input-text
             :model (str (:date-to @fields))
             :on-change (fn [x] (swap! fields #(assoc % :date-to (cljc.util/full-dMyyyy x))))
             :validation-regex #"^\d{0,2}$|^\d{0,2}\.\d{0,2}$|^\d{0,2}\.\d{0,2}\.\d{0,4}$"
             :width "100px"]
            [re-com/input-text :width "60px"
             :model (str (:time-to @fields))
             :on-change (fn [x] (swap! fields #(assoc % :time-to (cljc.util/full-HHmm x))))
             :validation-regex #"^(\d{0,2}):?(\d{1,2})?$"]]]
          (if (and (:var-group-id @fields)
                   (:date-from @fields)
                   (:date-to @fields))
            [:a {:href (str "/export-stavu?var-group-id=" (:var-group-id @fields)
                            "&from=" (cljc.util/date--edn-str (cljc.util/from-format (str (:date-from @fields) " " (:time-from @fields)) cljc.util/dMyyyyHmm))
                            "&to=" (cljc.util/date--edn-str (cljc.util/from-format (str (:date-to @fields) " " (:time-to @fields)) cljc.util/dMyyyyHmm)))
                 :target "_blank"} [re-com/button :label "Uložit CSV soubor"] ]
            [re-com/button :label "Uložit CSV soubor" :disabled? true]
            )]]))))

(pages/add-page ::page #'page)

(secretary/defroute "/export-stavu" []
  (re-frame/dispatch [:set-current-page ::page]))


