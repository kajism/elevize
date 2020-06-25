(ns cljs.user
  (:require [devtools.core :as devtools]
            [elevize.cljs.core]
            [figwheel.client :as figwheel]))

(devtools/install!)

(js/console.info "Starting in development mode")

(enable-console-print!)

(figwheel/start {:websocket-url (str "ws://" (.-hostname js/location) ":3450/figwheel-ws")})
