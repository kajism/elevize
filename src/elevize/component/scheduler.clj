(ns elevize.component.scheduler
  (:require [com.stuartsierra.component :as component]
            [elevize.config :as config]
            [elevize.db.service :as service]
            [elevize.db.runtime-diary :as runtime-diary]
            [taoensso.timbre :as timbre]
            [twarc.core :as twarc])
  (:import java.util.TimeZone
           java.util.Date))

(twarc/defjob save-full-device-state [scheduler db-spec device-state]
  (timbre/info {:a ::save-full-device-state})
  (try
    (service/save-device-state db-spec @device-state)
    (catch Exception e
      (timbre/error {:a ::save-full-device-state-error :device-state @device-state :ex-info (ex-info (.getMessage e) {} e)}))))

(twarc/defjob sync-device-states-history [scheduler db-spec]
  (timbre/info {:a ::sync-device-states-history})
  (try
    (service/sync-device-states-history db-spec)
    (catch Exception e
      (timbre/error {:a ::sync-device-states-history-error :ex-info (ex-info (.getMessage e) {} e)}))))

(twarc/defjob calc-runtime-diary [scheduler db-spec]
  (timbre/info {:a ::triggering-calc-runtime-diary})
  (try
    (let [yesterday (-> (Date.)
                        (.getTime)
                        (- (* 24 60 60 1000))
                        (Date.))]
      (runtime-diary/calculate-runtime-diary db-spec yesterday))
    (catch Exception e
      (timbre/error {:a ::calculate-runtime-diary-error :ex-info (ex-info (.getMessage e) {} e)}))))

(defrecord Scheduler [db plc twarc-scheduler quartz-props]
  component/Lifecycle
  (start [component]
    (let [sched (-> (twarc/make-scheduler quartz-props)
                    (twarc/start))
          db-spec (:spec db)]

      (timbre/info {:a ::scheduling-periodic-tasks})
      ;; cron expression: sec min hour day-of-mon mon day-of-week ?year
      (save-full-device-state sched
                              [db-spec (:device-states plc)]
                              :trigger {:cron {:expression "0 45 11,23 * * ?" ;; at 11:45 and 23:45
                                               :misfire-handling :fire-and-process
                                               :time-zone (TimeZone/getTimeZone "Europe/Prague")}})
      (when config/orphan?
        (sync-device-states-history sched
                                    [db-spec]
                                    :trigger {:cron {:expression "20 * * * * ?" ;; every minute at 20. second
                                                     :misfire-handling :do-nothing}})
        (calc-runtime-diary sched
                            [db-spec]
                            :trigger {:cron {:expression "0 15 0 * * ?"
                                             :misfire-handling :fire-and-process
                                             ;; funguje to dobre az po UTC pulnoci, takze nechci Europe/Prague zone
                                             }}))

      (assoc component :twarc-scheduler sched)))
  (stop [component]
    (when twarc-scheduler
      (timbre/info {:a ::stopping-scheduler})
      (twarc/stop twarc-scheduler))
    (assoc component :twarc-scheduler nil)))

(defn scheduler []
  (map->Scheduler {:quartz-props
                   {:threadPool.class "org.quartz.simpl.SimpleThreadPool"
                    :threadPool.threadCount 1
                    :plugin.triggHistory.class "org.quartz.plugins.history.LoggingTriggerHistoryPlugin"
                    :plugin.jobHistory.class "org.quartz.plugins.history.LoggingJobHistoryPlugin"}}))
