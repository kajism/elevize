(ns elevize.cljs.sente
  (:require [re-frame.core :as re-frame]
            [taoensso.sente :as sente]
            [taoensso.sente.packers.transit :as sente-transit]
            [taoensso.timbre :as timbre]))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
       "/chsk" {:type :auto
                :packer (sente-transit/get-transit-packer)})]

  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom
  )

;;;; Sente event handlers

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id ; Dispatch on event-id
  )

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  :default ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event]}]
  (timbre/debug (str ::unhandled-event) event))

(defmethod -event-msg-handler :chsk/state
  [{[old-state new-state] :?data :as ev-msg}]
  (if (:first-open? new-state)
    (do
      (timbre/debug "Channel socket successfully established!")
      (re-frame/dispatch [:elevize.cljs.core/init-app]))
    (timbre/debug (str ::channel-socket-state-change) new-state)))

(defmethod -event-msg-handler :chsk/recv
  [{[msg-id ?data :as msg] :?data :as ev-msg}]
  (case msg-id
    :elevize/alarm (re-frame/dispatch [:elevize.cljs.alarm-history/received ?data])
    :elevize/device-states (re-frame/dispatch [:elevize.cljs.device-states/update ?data])
    :elevize/entities-updated (re-frame/dispatch [:elevize.cljs.common/entities-updated ?data])
    (do
      (timbre/debug ::unknown-server-message msg))))

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (timbre/debug (str ::handshake) ?data)))

;; TODO Add your (defmethod -event-msg-handler <event-id> [ev-msg] <body>)s here...

;;;; Sente event router (our `event-msg-handler` loop)

(defonce router_ (atom nil))
(defn  stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! router_
          (sente/start-client-chsk-router! ch-chsk event-msg-handler)))

(def sente-timeout 6000)

(defn server-call
  ([req-msg resp-msg]
   (server-call req-msg resp-msg nil))
  ([req-msg resp-msg rollback-db]
   (chsk-send! req-msg
               sente-timeout
               (fn [reply]
                 (if (and (sente/cb-success? reply) (not (:error/msg reply)))
                   (when resp-msg
                     (re-frame/dispatch (conj resp-msg reply)))
                   (re-frame/dispatch [:set-msg :error
                                       (cond
                                         (= reply :chsk/timeout)
                                         "Server neodpovídá"
                                         (= reply :chsk/closed)
                                         "Spojení se serverem je přerušeno. Aktualizujte stránku."
                                         (some? (:error/msg reply))
                                         (:error/msg reply)
                                         :else
                                         (str reply))
                                       rollback-db]))))
   nil))
