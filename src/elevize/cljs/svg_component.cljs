(ns elevize.cljs.svg-component)

(defn some-svg-component []
  [:svg {:width 200 :height 200}
   [:path {:d "M25 0 L25 150 L75 200 L125 200 L175 150 L175 0 Z "
           :onClick #(js/alert "Já jsem zásobník!")
           :fill "grey"}]])

(defn svg-composition []
  [:svg {:width 200 :height 200}
   [:svg {:width 100 :height 100 :view-box "0 0 200 200"}
    [some-svg-component]]
   [:svg {:width 100 :height 100 :x 100 :view-box "0 0 200 200"}
    [some-svg-component]]
   [:svg {:width 100 :height 100 :x 100 :y 100 :view-box "0 0 200 200"}
    [some-svg-component]]])

(defn svg-symbols []
  [:svg {:width 250 :height 200}
   [:defs
    [:g {:id "kolecka"}
     [:circle {:r "10" :fill "green"}]
     [:circle {:cx "15" :r "10" :fill "purple"}]]
    [:rect {:id "modry-ctverecek" :x 10 :y 10 :width 20 :height 20 :fill "blue"}]]
   [:rect {:id "cerveny-ctverecek" :x 10 :y 10 :width 20 :height 20 :fill "red"}]
   [:symbol {:id "zluty-ctverecek" :view-box "0 0 30 30"}
    [:rect {:x 5 :y 5 :width 20 :height 20 :fill "yellow"}]]
   [:use {:x 50 :y 50 :xlink-href "#zluty-ctverecek"}]
   [:use {:x 100 :y 100 :xlink-href "#kolecka"}]
   [:use {:x 100 :y 10 :xlink-href "#kolecka"}]
   [:use {:x 150 :y 150 :xlink-href "#cerveny-ctverecek"}]
   [:use {:x 180 :y 150 :xlink-href "#modry-ctverecek"}]]
  )
