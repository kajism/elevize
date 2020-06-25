(ns elevize.cljs.user
  (:require [clojure.string :as str]
            [elevize.cljs.common :as common]
            [elevize.cljs.comp.data-table :refer [data-table]]
            [elevize.cljs.comp.buttons :as buttons]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.util :as util]
            [reagent.ratom :as ratom]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]))

(defn page-users []
  (let [users (re-frame/subscribe [:entities :user])
        user (re-frame/subscribe [:auth-user])
        table-state (re-frame/subscribe [:table-state :users])]
    (fn []
      [:div
       [:h3 "Uživatelé"]
       (if-not @users
         [re-com/throbber]
         [data-table
          :table-id :users
          :colls [[[re-com/h-box :gap "5px" :justify :end
                    :children
                    [(when ((:-rights @user) :user/save)
                       [re-com/md-icon-button
                        :md-icon-name "zmdi-plus-square"
                        :tooltip "Přidat"
                        :on-click #(set! js/window.location.hash "#/user/e")])
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Přenačíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :user])]]]
                   (fn [row]
                     (when (and (= (:id row) (:selected-row-id @table-state)))
                       [re-com/h-box
                        :gap "5px" :justify :end
                        :children
                        [#_[re-com/hyperlink-href
                            :href (str "#/user/" (:id row))
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-view-web"
                                    :tooltip "Detail"]]
                         (when ((:-rights @user) :user/save)
                           [re-com/hyperlink-href
                            :href (str "#/user/" (:id row) "e")
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]])
                         (when ((:-rights @user) :user/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :user (:id row)])])]]))
                   :none]
                  ["Jméno a příjmení" :title]
                  ["Email" :email]
                  ["Uživatelské jméno" :login]
                  ["Role" :roles]]
          :rows users])])))

(defn page-user []
  (let [user (re-frame/subscribe [:entity-edit :user])]
    (fn []
      [:div
       [:h3 "Uživatel"]
       [:form {:role "form"}
        [:div.form-group
         [:label "Jméno a příjmení"]
         [re-com/input-text :model (str (:title @user))
          :on-change #(re-frame/dispatch [:entity-change :user (:id @user) :title %])]]
        [:div.form-group
         [:label "Email"]
         [re-com/input-text :model (str (:email @user))
          :on-change #(re-frame/dispatch [:entity-change :user (:id @user) :email %])]]
        [:div.form-group
         [:label "Uživatelské jméno"]
         [re-com/input-text :model (str (:login @user))
          :on-change #(re-frame/dispatch [:entity-change :user (:id @user) :login %])]]
        [:div.form-group
         [:label "Heslo"]
         [re-com/input-text :model (str (:passwd @user))
          :on-change #(re-frame/dispatch [:entity-change :user (:id @user) :passwd %])]]
        [:div.form-group
         [:label "Role"]
         [re-com/input-text :model (str (:roles @user))
          :on-change #(re-frame/dispatch [:entity-change :user (:id @user) :roles %])]]]
       [re-com/h-box
        :gap "5px"
        :children
        [[re-com/button :label "Uložit" :class "btn-success" :on-click #(re-frame/dispatch [:entity-save :user])]
         (when (:id @user)
           [re-com/hyperlink-href :label [re-com/button :label "Nový"] :href (str "#/user/e")])
         [re-com/hyperlink-href :label [re-com/button :label "Seznam"] :href (str "#/users")]]]])))

(pages/add-page :user  #'page-user)
(pages/add-page :users  #'page-users)

(secretary/defroute "/users" []
  (re-frame/dispatch [:set-current-page :users]))

(secretary/defroute #"/user/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :user (util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :user]))

(common/add-kw-url :user "user")
