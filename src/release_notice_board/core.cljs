(ns release-notice-board.core
  (:require

   [ajax.core :as ajax]
   [clojure.string :as string]

   [reagent.core :as r]
   [reagent.dom :as d]

   ;; Move
   [re-frame.core :as rf]
   [day8.re-frame.http-fx]
   
   [syn-antd.input  :as input]
   [syn-antd.button :as button]
   [syn-antd.space :as space]
   [syn-antd.icons.plus-circle-filled :as plus-circle-filled]
   [release-notice-board.utils :as utils]))

(def default-db
  {:current-user        nil
   ;; Homepage
   :repos-search-status :loading
   :repos-suggested     []

   ;; Repos 
   :repos-watching      {}
   
   ;; Release Details
   :current-repo        nil
   :release-notes       {}})

(rf/reg-event-fx
 :initialize-db
 (fn [_ _]
   {:db default-db}))


(rf/reg-sub 
 :repo-search/suggestions 
 (fn [db]
   (get db :repos-suggested)))

(rf/reg-event-db
 :repo-search/update-suggestions
 (fn [db [_ suggestions]]
   (assoc db :repos-suggested suggestions)))

(rf/reg-event-db
 :repo-search/clear-suggestions
 (fn [db _]
   (assoc db :repos-suggested [])))

(rf/reg-event-fx
 :repo-search/find-succeeded
 (fn [{:keys [db]} [_ {:keys [items]}]]
   {:db (assoc db :repos-search-status :finished)
    :fx [[:dispatch [:repo-search/update-suggestions items]]]}))

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
 :repos/watch-repo
 (fn [{:keys [db]} [_ repo]]
   #_{:db         (assoc db :repos-search-status :loading)}))








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

(defn repo-searchbar []
  [input/input
   {:on-change
    (utils/debounce
      (fn [e]
        (let [value (utils/element-value e)]
          (if (string/blank? value)
            (rf/dispatch [:repo-search/clear-suggestions])
            (rf/dispatch [:repo-search/find value]))))
     500)}])

(defn repo-suggestions []
  (let [repos @(rf/subscribe [:repo-search/suggestions])]
    [:div
     (for [{:keys [full_name forks description html_url language score watchers]} repos]
       ^{:key full_name}
       [:div
        [space/space
         [button/button {:shape :circle :size :small} "+"]
         [:a {:href html_url} full_name]]])]))

(defn repo-search-section []
  [:div
   [:p "Add new repos"]
   [repo-searchbar]
   [repo-suggestions]])

(defn testing-stuuf []
  [:div
   [:button {:on-click #(rf/dispatch [:repo-search/find "Octokit"])}
    "Search"]
   [:button {:on-click #(rf/dispatch [:releases/load-notes "SpinlockLabs/github.dart" 1])}
    "Releases"]])


(defn home-page []
  [:div
   [:h2 "Dashboard"]
   [repo-search-section]
   [:br]
   [:hr]
   [testing-stuuf]])

;; -------------------------
;; Initialize app

(defn mount-root []
  (d/render [home-page] (.getElementById js/document "app")))

(defn ^:export init! []
  (rf/dispatch-sync [:initialize-db])
  (mount-root))
