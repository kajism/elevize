(ns elevize.system
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [cognitect.transit :as tran]
            [com.stuartsierra.component :as component]
            [duct.component.endpoint :refer [endpoint-component]]
            [duct.component.handler :refer [handler-component]]
            [duct.component.hikaricp :refer [hikaricp]]
            [duct.component.ragtime :refer [ragtime]]
            [duct.middleware.not-found :refer [wrap-not-found]]
            [duct.middleware.route-aliases :refer [wrap-route-aliases]]
            #_[elevize.component.alarm-listener :refer [alarm-listener]]
            [elevize.component.http-kit :refer [http-kit-server]]
            [elevize.component.nrepl-server :refer [nrepl-server]]
            [elevize.component.plc :refer [plc]]
            [elevize.component.sente :refer [new-channel-socket-server]]
            [elevize.component.scheduler :refer [scheduler]]
            [elevize.endpoint.main :refer [main-endpoint]]
            [environ.core :refer [env]]
            [meta-merge.core :refer [meta-merge]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.util.response :as response]
            [taoensso.sente.server-adapters.http-kit
             :refer
             [sente-web-server-adapter]]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.3rd-party.rotor :as rotor]
            [clojure.string :as str]))

(def api-pattern #"/chsk")

(defn wrap-exceptions [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (timbre/error {:a ::exception-caught-in-http-stack :ex-info (ex-info (.getMessage e) {:request request} e)})
        (if (re-find api-pattern (:uri request))
          {:status 500
           :headers {"Content-Type" "text/plain;charset=utf-8"}
           :body {:error/msg (.getMessage e)}}
          (throw e))))))

(def access-denied-response {:status 403
                             :headers {"Content-Type" "text/plain;charset=utf-8"}
                             :body "Přístup odmítnut. Aktualizujte stránku a přihlašte se."})

(def login-redirect (response/redirect "/login"))

(defn wrap-auth [handler]
  (fn [request]
    (if (or (get-in request [:session :user])
            (contains? #{"/login" "/api/device-states"} (:uri request)))
      (handler request)
      (if (re-find api-pattern (:uri request))
        access-denied-response
        login-redirect))))

(def base-config
  {:app {:middleware [[wrap-auth]
                      [wrap-exceptions]
                      [wrap-not-found :not-found]
                      [wrap-webjars]
                      [wrap-defaults :defaults]
                      [wrap-route-aliases :aliases]]
         :not-found  (io/resource "elevize/errors/404.html")
         :defaults (meta-merge site-defaults (cond-> {:static {:resources "elevize/public"}
                                                      :security {:anti-forgery false}
                                                      :proxy true}
                                               (:dev env)
                                               (assoc :session {:store (cookie/cookie-store {:key "ElevizeCookiSalt"})})))
         :aliases    {}}
   :ragtime {:resource-path "elevize/migrations-postgresql"}})

(defn new-system [config]
  (timbre/set-config!
   {:level     :debug #_(if (:dev env) :debug :info)
    :appenders {;;:println (taoensso.timbre.appenders.core/println-appender)
                :rotor #_(rotor/rotor-appender
                        {:path "log/elevize.log"
                         :max-size (* 10 1024 1024)
                         :backlog 10})
                (assoc (rotor/rotor-appender
                        {:path "log/elevize.log"
                         :max-size (* 10 1024 1024)
                         :backlog 100})
                       :output-fn (fn [data]
                                    (let [level (-> data :level name str/upper-case)]
                                      ;;(pprint/pprint data)
                                      (pr-str (merge {:l (if (< (count level) 5) (str level " ") level)
                                                      :t (:instant data)}
                                                     {:err (:?err data)}
                                                     (cond
                                                       (and (map? (first (:vargs data))) (= 1 (count (:vargs data))))
                                                       (first (:vargs data))
                                                       :else
                                                       {:vargs (:vargs data)}))))))}})
  (timbre/info {:a ::installing-default-ex-handler})
  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (timbre/error {:a ::uncaught-exception} :e ex :thread (.getName thread)))))
  (let [config (meta-merge base-config config)]
    (-> (component/system-map
         :nrepl (nrepl-server (:nrepl-port config))
         :app  (handler-component (:app config))
         :http (http-kit-server (:http config))
         :sente (new-channel-socket-server sente-web-server-adapter)
         :db   (hikaricp (:db config))
         :ragtime (ragtime (:ragtime config))
         :main (endpoint-component main-endpoint)
         :plc (plc (-> (:plc config)))
         :scheduler (scheduler)
         ;;:alarm-listener (alarm-listener (:plc config))
         )
        (component/system-using
         {:http [:app]
          :app  [:main]
          :ragtime [:db]
          :main [:db :sente]
          :sente [:db :plc]
          :plc [:db]
          :scheduler [:db :plc]
          ;;:alarm-listener [:db :sente]
          }))))
