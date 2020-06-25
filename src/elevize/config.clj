(ns elevize.config
  (:require [environ.core :refer [env]]))

(def defaults
  ^:displace {:http {:port 3000}})

(def environ
  {:http {:port (or (some-> env :port Integer.) 3001)}
   :nrepl-port (or (some-> env :nrepl-port Integer.) 7771)
   :db   {:uri  (or (:database-url env) "jdbc:postgresql://localhost/elevize?user=elevize&password=elepwd")}
   :plc  {:host (or (:plc-host env) "192.168.33.222")
          :port (or (some-> env :plc-cmd-port Integer.) 6666)
          ;;:all-port (or (some-> env :plc-get-all-port Integer.) 6667)
          ;;:alarm-port (or (some-> env :alarm-port Integer.) 11000)
          :multicast-group (or (:multicast-addr env) "239.0.1.2")
          :multicast-port (or (some-> env :multicast-port Integer.) 6666)
          :poll-ms (or (some-> env :plc-poll-ms Integer.) 2000)}})

(def orphan? (= (get-in environ [:http :port]) 3003))
