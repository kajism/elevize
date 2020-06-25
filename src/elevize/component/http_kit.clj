(ns elevize.component.http-kit
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :as http-kit]
            [taoensso.timbre :as timbre]))

(defrecord HttpKitServer [app]
  component/Lifecycle
  (start [component]
    (if (:server component)
      component
      (let [options (dissoc component :app)
            handler (atom (delay (:handler app)))
            server (try
                     (http-kit/run-server (fn [req] (@@handler req)) options)
                     (catch Exception e
                       (timbre/error e "Nepodarilo se spustit HTTP server" options)))]
        (assoc component
               :server  server
               :handler handler))))
  (stop [component]
    (if-let [server (:server component)]
      (do (server)
          (dissoc component :server :handler))
      component)))

(defn http-kit-server
  ([options]
   (map->HttpKitServer options)))
