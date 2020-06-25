(ns elevize.endpoint.main
  (:require [cognitect.transit :as transit]
            [compojure.coercions :refer [as-int]]
            [compojure.core :refer :all]
            [elevize.cljc.util :as cljc.util]
            [elevize.db.import-xlsx :as import-xlsx]
            [elevize.db.service :as service]
            [elevize.endpoint.hiccup :as hiccup]
            [environ.core :refer [env]]
            [ring.util.response :as response]
            [taoensso.timbre :as timbre])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           java.nio.charset.StandardCharsets
           java.util.Date))

(defn main-js-url []
  (str "/main.js-v" (:version (service/app-version-info))))

(defn transit-response [data]
  (->
   (response/response
    (let [out (ByteArrayOutputStream.)
          wrt (transit/writer out :json)]
      (transit/write wrt data)
      (ByteArrayInputStream. (.toByteArray out))))
   (response/content-type "application/transit+json")))

(defn main-endpoint [{{db-spec :spec} :db
                      {:keys [ajax-post-fn ajax-get-or-ws-handshake-fn]} :sente}]
  (routes
   (context "" {{user :user} :session}
     (GET "/" []
       (hiccup/cljs-landing-page (main-js-url)))
     (GET (main-js-url) []
       (-> (if (:dev env)
             (response/file-response "js/main.js" {:root "target/figwheel/elevize/public"})
             (response/resource-response "elevize/public/js/main.js"))
           (response/content-type "application/javascript")
           (response/header "ETag" (str "Version-" (:version (service/app-version-info))))))
     (GET "/login" [] (hiccup/login-page))
     (POST "/login" [user-name pwd :as req]
       (try
         (if-let [user (service/login db-spec user-name pwd)]
           (-> (response/redirect "/" :see-other)
               (assoc-in [:session :user] (select-keys user [:id :login :title :-rights :roles])))
           (hiccup/login-page "Neplatné uživatelské jméno nebo heslo."))))
     (GET "/logout" []
       (-> (response/redirect "/" :see-other)
           (assoc :session {})))
     (GET "/export-stavu" [var-group-id :<< as-int from to]
       (-> (response/response
            (service/device-states-to-csv db-spec var-group-id
                                          (cljc.util/edn-str--date from)
                                          (cljc.util/edn-str--date to)))
           (response/content-type "text/csv")
           (response/charset "utf8")
           (response/header "Content-Disposition" (str "inline; filename=export_stavu_" from "-" to ".csv"))))

     (GET  "/chsk" req (ajax-get-or-ws-handshake-fn req))
     (POST "/chsk" req (ajax-post-fn req)))

   (context "/api" {{user :user} :session}
     (POST "/device-states" [from-ms :<< as-int to-ms :<< as-int]
       (timbre/info {:a ::device-states-history-request :from-ms from-ms :to-ms to-ms})
       (let [from (Date. from-ms)
             to (Date. to-ms)
             device-states (service/select-device-states db-spec from to false)
             plc-msg-history (service/select-plc-msg-history db-spec from to)
             alarm-history (service/select-alarm-history db-spec from to)]
         (timbre/info {:a ::device-states-history-result :from from :to to :result-count (count device-states)
                       :plc-msgs-count (count plc-msg-history) :alarm-count (count alarm-history)})
         (transit-response {:last-config-import-date (import-xlsx/last-config-import-date)
                            :device-states device-states
                            :plc-msg-history (map #(dissoc % :id :user-id) plc-msg-history)
                            :alarm-history (map #(dissoc % :id :device-id) alarm-history)})))
     #_(POST "/variables" req
         (timbre/debug {:a ::variables-request :req req})
         (transit-response (service/select-variables db-spec))))))
