(ns release-notice-board.core
  (:require

   [ajax.core :as ajax]

   [reagent.core :as r]
   [reagent.dom :as d]

   ;; Move
   [re-frame.core :as rf]
   [day8.re-frame.http-fx]))

(def default-db
  {:current-user        nil
   ;; Homepage
   :repos-search-status :loading
   :repos-suggested     {}
   :repos-watching      {}
   ;; Release Details
   :current-repo        nil
   :release-notes       {}})

(rf/reg-event-fx
 :initialize-db
 (fn [_ _]
   {:db default-db}))





(rf/reg-event-db
 :repo-search/update-suggestions
 (fn [db [_ suggestions]]
   (assoc db :repos-suggested suggestions)))

(rf/reg-event-fx
 :repo-search/find-succeeded
 (fn [{:keys [db]} [_ {:keys [items]}]]
   {:db (assoc db :repos-search-status :finished)
    :fx [[:dispatch [:repo-search/update-suggestions {}]]]}))

(rf/reg-event-db
 :repo-search/find-failed
 (fn [db _]
   (assoc db :repos-search-status :failed)))

(rf/reg-event-fx
 :repo-search/find
 (fn [{:keys [db]} [_ query]]
   {:db         (assoc db :repos-search-status :loading)
    :http-xhrio {:method          :get
                 :uri             "https://api.github.com/search/repositories"
                 :params          {:q        query
                                   :per_page 10}
                 :format          (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:repo-search/find-succeeded]
                 :on-failure      [:repo-search/find-failed]}}))







(rf/reg-event-fx
 :releases/load-notes-succeeded
 (fn [{:keys [db]} [_ {:keys [items]}]]
   {}))

(rf/reg-event-fx
 :releases/load-notes-failed
 (fn [db _]
   {}))

(rf/reg-event-fx
 :releases/load-notes
 (fn [{:keys [db]} [_ repo-full-name page]]
   {:db         (assoc db :repos-search-status :loading)
    :http-xhrio {:method          :get
                 :uri             (str "https://api.github.com/repos/" repo-full-name "/releases")
                 :params          {:per_page 10
                                   :page     page}
                 :format          (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true}) 
                 :on-success      [:repo-search/find-succeeded]
                 :on-failure      [:repo-search/find-failed]}}))

;; -------------------------
;; Views

(defn home-page []
  [:div
   [:h2 "Welcome to Reagent!"]
   [:button {:on-click #(rf/dispatch [:repo-search/find "Octokit"])}
    "Search"]
   [:button {:on-click #(rf/dispatch [:releases/load-notes "SpinlockLabs/github.dart" 1])}
    "Releases"]])

;; -------------------------
;; Initialize app

(defn mount-root []
  (d/render [home-page] (.getElementById js/document "app")))

(defn ^:export init! []
  (rf/dispatch-sync [:initialize-db])
  (mount-root))
