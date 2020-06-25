(ns elevize.cljs.dygraph
  (:require [cljs.pprint :as pprint]
            [cljsjs.dygraph]
            [clojure.string :as str]
            [elevize.cljc.util :as cljc.util]
            [elevize.cljs.util :as util]
            [goog.object :as gobj]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [reagent.ratom :as ratom]
            [taoensso.timbre :as timbre]))

(def dygraphs (atom {}))

(defn- highlight-callback [event x points row-idx series-name]
  #_(timbre/debug "row no" row-idx " time " (js/Date. x))
  (doseq [[id g] @dygraphs
          :let [idx (.findClosestRow g (.toDomXCoord g x))]
          :when idx]
    (try
      (.setSelection g idx)
      (catch js/Error e
        (timbre/debug e))))
  (re-frame/dispatch [:elevize.cljs.common/set-path-value [::selected-x] x]))

(defn- unhighlight-callback [event]
  (re-frame/dispatch [:elevize.cljs.common/set-path-value [::selected-x] nil])
  (doseq [[id g] @dygraphs]
    (.clearSelection g)))

;; http://dygraphs.com/gallery/#g/dynamic-update
(defn data-plotter-inner [init-opts id]
  (let [graph (atom nil)
        opts {;;:drawPoints false
              :stepPlot true
              :pointSize 2
              :legend "follow"
              :showLabelsOnHighlight false
              ;;:series { "SVO1/IO_TeplotaVstup2" {:axis "y2"}}
              ;; :showRoller true
              ;; :valueRange [0.0 1.2]
              }
        dy-file (clj->js (cljc.util/find-variables-history (or (:history init-opts) []) (:var-paths init-opts)))
        update (fn [comp]
                 (when-let [props (reagent/props comp)]
                   (let [last-ms (if (zero? (.-length dy-file))
                                   0
                                   (-> dy-file
                                       (aget (dec (.-length dy-file)))
                                       (aget 0)))]
                     (when-not (:paused? props)
                       (doseq [h (cljc.util/find-variables-history
                                  (->> (:history props)
                                       (reverse)
                                       (take-while #(> (get-in % ["EB1" "Cas"]) last-ms))
                                       (reverse))
                                  (:var-paths props))]
                         #_(timbre/debug "pushing" h)
                         (.push dy-file (clj->js h)))
                       (when (> (.-length dy-file) (count (:history props)))
                         (.splice dy-file 0 (- (.-length dy-file) (count (:history props)))))
                       (let [opts (clj->js (dissoc props :history :var-paths :paused?))]
                         (gobj/set opts "file" dy-file)
                         (.updateOptions @graph opts))))))]
    (reagent/create-class
     {:reagent-render (fn [data]
                        [:div {:style (select-keys data [:width :height])}])
      :component-did-mount (fn [comp]
                             (let [data (reagent/props comp)
                                   g (js/Dygraph. (reagent/dom-node comp)
                                                  #js []
                                                  (clj->js (assoc opts :labels (:labels data))))]
                               (reset! graph g)
                               (swap! dygraphs assoc id g))
                             (update comp))
      :component-did-update update
      :component-will-unmount #(do
                                 (.destroy @graph)
                                 (swap! dygraphs dissoc id))
      :display-name "data-plotter-inner"})))

(defn- time-window [last-minutes last-millis]
  (when (and last-minutes last-millis)
    [(- last-millis (* last-minutes 60000)) last-millis]))

(re-frame/reg-sub-raw
 ::vars-to-dygraph
 (fn [db [_ var-paths]]
   (let [history (re-frame/subscribe [:elevize.cljs.device-states/history])]
     (ratom/reaction
      {:labels (into ["Čas"] (map (fn [[device-code var-name]]
                                    (str device-code "/" var-name)) var-paths))
       :history @history
       :var-paths var-paths}))))

(defn variables-state-plotter [var-paths &{:keys [width height last-minutes show-controls?] :as opts}]
  (let [last-minutes (reagent/atom (or last-minutes 1))
        paused? (reagent/atom false)
        orphan? (:orphan? @(re-frame/subscribe [:elevize.cljs.common/path-value [:elevize.cljs.core/version-info]]))]
    (fn [var-paths]
      (let [plotter-data @(re-frame/subscribe [::vars-to-dygraph var-paths])
            current-date-window (time-window @last-minutes (some-> plotter-data :history (peek) (get "EB1") (get "Cas") (.getTime)))]
        (when (some-> plotter-data :history (peek))
          [:div
           [data-plotter-inner
            (merge plotter-data
                   (-> opts
                       (dissoc :show-controls? :last-minutes)
                       (assoc :paused? @paused?))
                   (when current-date-window
                     {:dateWindow current-date-window})
                   (when orphan?
                     {:highlightCallback highlight-callback
                      :unhighlightCallback unhighlight-callback}))
            var-paths]
           (when-not (false? show-controls?)
             [re-com/h-box :gap "5px" :align :center :children
              [[re-com/md-icon-button
                :md-icon-name (if @paused? "zmdi-pause" "zmdi-play")
                :tooltip (if @paused? "Spustit zobrazování" "Pozastavit zobrazování")
                :tooltip-position :below-right
                :on-click #(swap! paused? not)]
               [re-com/label :label "Posledních"]
               [re-com/input-text
                :model (str @last-minutes)
                :on-change #(do
                              (reset! last-minutes (util/parse-float %)))
                :width "50px"]
               [re-com/label :label "minut"]
               [re-com/slider
                :model (or @last-minutes 0)
                :max 5
                :on-change #(do
                              (reset! last-minutes (if (zero? %) nil %)))
                :width "200px"]]])])))))
