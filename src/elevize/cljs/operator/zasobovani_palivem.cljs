(ns elevize.cljs.operator.zasobovani-palivem
  (:require [elevize.cljc.util :as cljc.util]
            [elevize.cljs.comp.status-table :as status-table]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.sente :refer [server-call]]
            [elevize.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]
            [taoensso.timbre :as timbre]))

(re-frame/reg-event-db
 ::read-s-weights
 util/debug-mw
 (fn [db [_]]
   (server-call [:fuel-supply/get-s-weights]
                [::set-s-weights])
   db))

(re-frame/reg-event-db
 ::set-s-weights
 util/debug-mw
 (fn [db [_ s-weights]]
   (re-frame/dispatch [:elevize.cljs.device-states/update {"PP1" {"IO_HmotnostVstup1" (:s1 s-weights)
                                                                  "IO_HmotnostVstup2" (:s2 s-weights)}}])
   db))

(defn nasypka [& {:keys [x y width height fill class]}]
  [:svg {:x x :y y :width width :height height :view-box "0,0,50,20"}
   [:polygon {:points "0,0 15,20 35,20 50,0" :fill fill :class class}]])

(defn zasobnik [& {:keys [label x y width height level-min? level-max? filling? emptying? label weight] :or {width 100 height 200}}]
  (let [overflowing? (and level-max? filling?)]
    [:svg {:x x :y y}
     [:rect {:x 0 :y 0 :width width :height (* 0.2 height)
             :fill (if overflowing? "#FFC966" "gray")
             :class (when overflowing? "blinking")}]
     [:rect {:x 0 :y (* 0.2 height) :width width :height (* 0.5 height)
             :fill  (if (or level-max? (not level-min?)) "green" "gray")
             :class (when (and filling? (not level-min?) (not level-max?)) "blinking")}]
     [nasypka :x 0 :y (* 0.7 height) :width width :height (* 0.2 height)
      :fill (if level-min? "#FFC966" "green")
      :class (when (or emptying? (and filling? level-min?)) "blinking")]
     [:text {:x 10 :y (/ height 2) :fill "black" :style {:font-weight "bold" :font-size "300%"}} label]
     (when weight
       [:text {:x 3 :y (+ 40 (/ height 2)) :fill "black" :style {:font-weight "normal":font-size "250%"}}
        weight [:tspan {:font-weight "normal"} " kg"]])]))

(defn dopravnik* [& {:keys [kks x y width height moving? error?] :or {width 100 height 25}}]
  [:svg {:id kks :x x :y y :width width :height height}
   [:defs
    [:polyline.moving {:id (str kks "arrow")
                       :style {:fill "none" :stroke "black" :stroke-width 2} :points (str "10," (* 0.2 height) " 20," (* 0.5 height) " 10," (* 0.8 height))}]
    #_[:pattern {:id (str kks "arrow") :x 0 :y 0 :width 0.33 :height 1}
       [:polyline.moving {:style {:fill "none" :stroke "black" :stroke-width 2} :points (str "10," (* 0.2 height) " 20," (* 0.5 height) " 10," (* 0.8 height))}]]]
   [:rect {:fill (if error? "#FFC966" "gray") :width width :height height}]
   #_[:rect {:fill (str "url(#" kks "arrow)") :width width :height height}]
   (when moving?
     (doall
      (for [[idx x] (->> -30
                         (iterate (partial + 30))
                         (take-while #(< % width))
                         (map-indexed vector))]
        ^{:key idx}
        [:use {:xlink-href (str "#" kks "arrow") :x x :y 0}])))])

(defn dopravnik [& {:keys [kks cx cy width height backwards? rotate] :as params :or {width 100 height 25 rotate 0}}]
  (let [params (cond-> params
                 backwards?
                 (merge {:rotate (+ rotate 180)}))]
    [:g {:transform (str "rotate(" (or (:rotate params) 0) " " cx " " cy ")")}
     (into [dopravnik*] (flatten (seq (merge params {:x (- cx (/ width 2))
                                                     :y (- cy (/ height 2))}))))]))

(defn vizualizace [& {:keys [width height]}]
  (let [device-states @(re-frame/subscribe [:elevize.cljs.device-states/current])]
    [:svg {:width width :height height :view-box "0,0,1150,800"}
     [:rect {:id "background" :x 0 :y 0 :width 1200 :height 800 :fill "NavajoWhite"}]
     [zasobnik
      :label "Z1"
      :x 50 :y 50
      :level-min? (cljc.util/bool (get-in device-states ["ZP1" "IO_HladinaMin1"]))
      :level-max? (cljc.util/bool (get-in device-states ["ZP1" "IO_HladinaMax1"]))
      :filling? (cljc.util/bool (get-in device-states ["ZP1" "IO_DR12_ChodVzad"]))
      :emptying? (cljc.util/bool (get-in device-states ["TOS_P1" "IO_Snek1_Chod"]))]
     [zasobnik
      :label "Z2"
      :x 50 :y 600
      :level-min? (cljc.util/bool (get-in device-states ["ZP1" "IO_HladinaMin2"]))
      :level-max? (cljc.util/bool (get-in device-states ["ZP1" "IO_HladinaMax2"]))
      :filling? (cljc.util/bool (get-in device-states ["ZP1" "IO_DR12_ChodVpred"]))
      :emptying? (cljc.util/bool (get-in device-states ["TOS_P2" "IO_Snek1_Chod"]))]
     [dopravnik
      :kks "215N007-M1"
      :cx 345 :cy 400
      :width 270 :height 40
      :moving? (cljc.util/bool (get-in device-states ["ZP1" "IO_D12_Chod"]))
      :error? (cljc.util/bool (get-in device-states ["ZP1" "IO_D12_Error"]))]
     (let [vpred? (cljc.util/bool (get-in device-states ["ZP1" "IO_DR12_ChodVpred"]))
           vzad? (cljc.util/bool (get-in device-states ["ZP1" "IO_DR12_ChodVzad"]))]
       [dopravnik
        :kks "215N009-M01"
        :cx 180 :cy 350
        :width 550 :height 40
        :rotate 270
        :moving? (or vpred? vzad?)
        :backwards? vpred?
        :error? (cljc.util/bool (get-in device-states ["ZP1" "IO_DR12_Error"]))])

     [zasobnik
      :label "Z3"
      :x 1000 :y 50
      :level-min? (cljc.util/bool (get-in device-states ["ZP1" "IO_HladinaMin3"]))
      :level-max? (cljc.util/bool (get-in device-states ["ZP1" "IO_HladinaMax3"]))
      :filling? (cljc.util/bool (get-in device-states ["ZP1" "IO_DR34_ChodVzad"]))
      :emptying? (cljc.util/bool (get-in device-states ["TOS_P3" "IO_Snek1_Chod"]))]
     [zasobnik
      :label "Z4"
      :x 1000 :y 600
      :level-min? (cljc.util/bool (get-in device-states ["ZP1" "IO_HladinaMin4"]))
      :level-max? (cljc.util/bool (get-in device-states ["ZP1" "IO_HladinaMax4"]))
      :filling? (cljc.util/bool (get-in device-states ["ZP1" "IO_DR34_ChodVpred"]))
      :emptying? (cljc.util/bool (get-in device-states ["TOS_P4" "IO_Snek1_Chod"]))]
     [dopravnik
      :kks "215N008-M01"
      :cx 800 :cy 400
      :width 270 :height 40
      :moving? (cljc.util/bool (get-in device-states ["ZP1" "IO_D34_Chod"]))
      :error? (cljc.util/bool (get-in device-states ["ZP1" "IO_D34_Error"]))]
     (let [vpred? (cljc.util/bool (get-in device-states ["ZP1" "IO_DR34_ChodVpred"]))
           vzad? (cljc.util/bool (get-in device-states ["ZP1" "IO_DR34_ChodVzad"]))]
       [dopravnik
        :kks "215N010-M01"
        :cx 970 :cy 350
        :width 550 :height 40
        :rotate 270
        :moving? (or vpred? vzad?)
        :backwards? vpred?
        :error? (cljc.util/bool (get-in device-states ["ZP1" "IO_DR34_Error"]))])

     (let [vpred? (cljc.util/bool (get-in device-states ["ZP1" "IO_DR_ChodVpred"]))
           vzad? (cljc.util/bool (get-in device-states ["ZP1" "IO_DR_ChodVzad"]))]
       [dopravnik
        :label "Reverzační"
        :kks "215N006-M1"
        :cx 565 :cy 340
        :width 350 :height 50
        :moving? (or vpred? vzad?)
        :backwards? vzad?
        :error? (cljc.util/bool (get-in device-states ["ZP1" "IO_DR_Error"]))])

     [dopravnik
      :label "Korečkový"
      :kks "215N005-M1"
      :cx 570 :cy 565 :width 380 :height 60 :rotate 270
      :moving? (cljc.util/bool (get-in device-states ["ZP1" "IO_KDZasobovani_Chod"]))
      :error? (cljc.util/bool (get-in device-states ["ZP1" "IO_KDZasobovani_Error"]))]

     [zasobnik
      :label "S1"
      :weight (get-in device-states ["PP1" "IO_HmotnostVstup1"])
      :x 350 :y 470
      :width 150 :height 300
      :level-min? (cljc.util/bool (get-in device-states ["PP1" "IO_HladinaMinVstup1"]))
      :level-max? (cljc.util/bool (get-in device-states ["PP1" "IO_HladinaMaxVstup1"]))
      :emptying? (cljc.util/bool (get-in device-states ["ZP1" "IO_Soupe1_Otv"]))]
     [dopravnik
      :kks "Snek1"
      :cx 465 :cy 770
      :width 130 :height 40
      :moving? (> (or (get-in device-states ["ZP1" "IO_Snek1_FreqAct"]) 0) 0)
      :error? (cljc.util/bool (get-in device-states ["ZP1" "ChybaSnek1"]))]

     [zasobnik
      :label "S2"
      :weight (get-in device-states ["PP1" "IO_HmotnostVstup2"])
      :x 650 :y 470
      :width 150 :height 300
      :level-min? (cljc.util/bool (get-in device-states ["PP1" "IO_HladinaMinVstup2"]))
      :level-max? (cljc.util/bool (get-in device-states ["PP1" "IO_HladinaMaxVstup2"]))
      :emptying? (cljc.util/bool (get-in device-states ["ZP1" "IO_Soupe2_Otv"]))]
     [dopravnik
      :kks "Snek2"
      :cx 680 :cy 770
      :width 140 :height 40
      :moving? (cljc.util/bool (get-in device-states ["ZP1" "IO_Snek2_Chod"]))
      :backwards? true
      :error? (cljc.util/bool (get-in device-states ["ZP1" "ChybaSnek2"]))]]))

(defn page []
  [re-com/v-box :children
   [[:h3 "Zásobování palivem"]
    [vizualizace :width 1200 :height 800]
    [re-com/button
     :label "Načíst hmotnost paliva v silech"
     :on-click #(re-frame/dispatch [::read-s-weights])]]])

(pages/add-page ::page #'page)

(secretary/defroute "/zasobovani-palivem" []
  (re-frame/dispatch [:set-current-page ::page]))
