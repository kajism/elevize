(ns elevize.endpoint.hiccup
  (:require [clojure.pprint :refer [pprint]]
            [hiccup.page :as hiccup]
            [ring.util.anti-forgery :as anti-forgery]
            [ring.util.response :as response]
            [elevize.config :as config]))

(defn hiccup-response
  [body]
  (-> (hiccup/html5 {:lang "cs"}
                    body)
      response/response
      (response/content-type "text/html")
      (response/charset "utf-8")))

(defn hiccup-pprint
  [data]
  [:pre (with-out-str (pprint data))])

(defn- hiccup-frame [body]
  (list
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:title "Elevize"]
    [:link {:rel "stylesheet" :href "assets/css/bootstrap.css"}]
    #_[:link {:rel "stylesheet" :href "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap.min.css" :crossorigin "anonymous"}]
    [:link {:rel "stylesheet" :href "assets/css/material-design-iconic-font.min.css"}]
    [:link {:rel "stylesheet" :href "assets/css/re-com.css"}]
    [:link {:rel "stylesheet" :href "css/site.css"}]
    #_[:link {:href "https://fonts.googleapis.com/css?family=Roboto:300,400,500,700,400italic"
              :rel "stylesheet" :type "text/css"}]
    [:link {:href "assets/fonts/roboto1.css" :rel "stylesheet" :type "text/css"}]
    #_[:link {:href "https://fonts.googleapis.com/css?family=Roboto+Condensed:400 ,300"
              :rel "stylesheet" :type "text/css"}]
    [:link {:href "assets/fonts/roboto2.css" :rel "stylesheet" :type "text/css"}]]
   [:body
    body
    #_[:script {:src "https://ajax.googleapis.com/ajax/libs/jquery/1.12.0/jquery.min.js"}]
    [:script {:src "assets/js/jquery.min.js"}]
    #_[:script {:src "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/js/bootstrap.min.js"}]
    [:script {:src "assets/js/bootstrap.min.js"}]
    (when config/orphan?
      [:style "body { background-color: #F5F5F5;}"])]))

(defn login-page
  ([] (login-page nil))
  ([msg]
   (hiccup-response
    (hiccup-frame
     [:div.container.login
      [:h3 "ELEVIZE"]
      [:p "Pro přihlášení zadejte své přihlašovací údaje"]
      (when msg
        [:div.alert.alert-danger msg])
      [:form.form-inline {:method "post"}
       [:div.form-group
        [:label {:for "user-name"} "Uživatelské jméno"]
        [:input#user-name.form-control {:name "user-name" :type "text"}]]
       [:div.form-group
        [:label {:for "heslo"} "Heslo"]
        [:input#heslo.form-control {:name "pwd" :type "password"}]]
       (anti-forgery/anti-forgery-field)
       [:button.btn.btn-default {:type "submit"} " Přihlásit"]]]))))

(defn cljs-landing-page [main-js-url]
  (hiccup-response
   (hiccup-frame
    [:div
     [:div#app "Načítám Elevizi ..."]
     (anti-forgery/anti-forgery-field)
     [:script {:src main-js-url}]
     [:audio#change-sound {:src "audio/beep.mp3"}]
     [:audio#connected-sound {:src "audio/connected.mp3"}]
     [:audio#disconnected-sound {:src "audio/disconnected.mp3"}]
     [:audio#alarm-sound {:src "audio/alarm.mp3"}]])))
