(ns elevize.cljs.common
  (:require [elevize.cljs.sente :refer [server-call]]
            [elevize.cljs.util :as util]
            [reagent.ratom :as ratom]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]))

(defonce kw->url (atom {}))

(defn add-kw-url [kw url]
  (swap! kw->url assoc kw url))

;;---- Subscriptions--------------------------------------------
(re-frame/reg-sub-raw
 :entities
 (fn [db [_ kw]]
   (let [out (ratom/reaction (get @db kw))]
     (when (nil? @out)
       (re-frame/dispatch [:entities-load kw true]))
     out)))

(re-frame/reg-sub-raw
 :entity
 (fn [db [_ kw id]]
   (let [ents (re-frame/subscribe [:entities kw])]
     (when-not id
       (timbre/error (str ::missing-entity-id)))
     (ratom/reaction (get @ents id)))))

(re-frame/reg-sub-raw
 :entities-dynamic
 (fn [db [_] [kw]]
   (re-frame/subscribe [:entities kw])))

(re-frame/reg-sub-raw
 :entities-by-kws
 (fn [db [_ kws]]
   (ratom/reaction
    (into {} (map (fn [kw]
                    [kw (re-frame/subscribe [:entities kw])]) kws)))))

(re-frame/reg-sub-raw
 :entities-by-right
 (fn [db [_ kw] [user]]
   (if ((:-rights user) (keyword (name kw) "select"))
     (re-frame/subscribe [:entities kw])
     (atom nil))))

(re-frame/reg-sub-raw
 :ebs-entities
 (fn [db [_ kw ebs-tree-id]]
   (let [user (re-frame/subscribe [:auth-user])
         out (re-frame/subscribe [:entities-by-right kw] [user])]
     (ratom/reaction
      (filter
       (fn [[k v]] (= ebs-tree-id (-> v :ebs/code-ref :id)))
       @out)))))

(re-frame/reg-sub-raw
 :entity-edit
 (fn [db [_ kw]]
   (let [id (ratom/reaction (get-in @db [:entity-edit kw :id]))
         ents (re-frame/subscribe [:entities kw])]
     (ratom/reaction (get @ents @id)))))

(re-frame/reg-sub-raw
 :entity-edit-dynamic
 (fn [db [_] [kw]]
   (re-frame/subscribe [:entity-edit kw])))

(re-frame/reg-sub-raw
 :entity-edit?
 (fn [db [_ kw]]
   (ratom/reaction (get-in @db [:entity-edit kw :edit?]))))

(re-frame/reg-sub-raw
 :entity-edit?-dynamic
 (fn [db [_] [kw]]
   (re-frame/subscribe [:entity-edit? kw])))

;;---- Handlers -----------------------------------------------
(re-frame/reg-event-db
 :entities-load
 util/debug-mw
 (fn [db [_ kw missing-only?]]
   (if (and missing-only? (get db kw))
     db
     (do
       (server-call [(keyword (name kw) "select") {}]
                    [:entities-set kw])
       (assoc db kw {})))))

(re-frame/reg-event-db
 :entities-set
 util/debug-mw
 (fn [db [_ kw v]]
   (assoc db kw (into {} (map (juxt :id identity)
                              v)))))

(re-frame/reg-event-db
 :entity-set-edit
 util/debug-mw
 (fn [db [_ kw id edit?]]
   (assoc-in db [:entity-edit kw] {:id id
                                   :edit? (boolean edit?)})))

(re-frame/reg-event-db
 :entity-new
 util/debug-mw
 (fn [db [_ kw new-ent]]
   (if new-ent
     (assoc-in db [kw nil] new-ent)
     (update db kw dissoc nil))))

(re-frame/reg-event-db
 :entity-change
 util/debug-mw
 (fn [db [_ kw id attr val]]
   (if (fn? val)
     (update-in db [kw id attr] val)
     (assoc-in db [kw id attr] val))))

(re-frame/reg-event-db
 :entity-save
 util/debug-mw
 (fn [db [_ kw]]
   (let [ent (get-in db [kw (get-in db [:entity-edit kw :id])])]
     (server-call [(keyword (name kw) "save") (-> ent
                                                  util/dissoc-temp-keys)]
                  [:entity-saved kw]))
   db))

(re-frame/reg-event-db
 :entity-saved
 util/debug-mw
 (fn [db [_ kw new-ent-id]]
   (re-frame/dispatch [:set-msg :info "Záznam byl uložen"])
   (set! js/window.location.hash (str "#/" (get @kw->url kw) "/" new-ent-id "e"))
   ;; (assoc-in [kw (:id new-ent)] new-ent) ;; db is upudated by client-broadcast
   (update db kw #(dissoc % nil))))

(re-frame/reg-event-db
 :entity-delete
 util/debug-mw
 (fn [db [_ kw id]]
   (when id
     (server-call [(keyword (name kw) "delete") id] nil db))
   (update db kw #(dissoc % id))))

(re-frame/reg-event-db
 ::entities-updated
 util/debug-mw
 (fn [db [_ new-ents]]
   ;;merge new ents & remove keys/ids with nil values (removed entities)
   (reduce (fn [out [ent-kw new-ents]]
             (let [remove-ids (->> new-ents
                                   (keep (fn [[k v]]
                                           (when-not v
                                             k))))]
               (update out ent-kw
                       (fn [ents]
                         (apply dissoc
                                (->> new-ents
                                     (remove (fn [[k v]] (nil? v)))
                                     (into {})
                                     (merge ents))
                                remove-ids)))))
           db
           new-ents)))

(re-frame/reg-sub
 ::path-value
 (fn [db [_ path]]
   (assert (seqable? path) "Path should be a vector!")
   (get-in db path)))

(re-frame/reg-event-db
 ::set-path-value
 ;;util/debug-mw
 (fn [db [_ path value]]
   (assert (seqable? path) "Path should be a vector!")
   (if (and (fn? value)
            (not (keyword? value)))
     (update-in db path value)
     (assoc-in db path value))))

(re-frame/reg-sub-raw
 ::entities-by
 (fn [db [_ ent-kw by-kw]]
   (let [ents (re-frame/subscribe [:entities ent-kw])]
     (ratom/reaction
      (some->> @ents
               (vals)
               (map (juxt by-kw identity))
               (into {}))))))
