(ns release-notice-board.events-n-handlers
  (:require
   [ajax.core :as ajax]
   [re-frame.core :as rf]
   [day8.re-frame.http-fx]
   
   [syn-antd.message :as message]))

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

(def repo-fields
  #{:full_name :forks :description :html_url :language :score :watchers})

(rf/reg-event-fx
 :initialize-db
 (fn [_ _]
   {:db default-db}))

(rf/reg-fx 
 :show-message 
 (fn [[type message]]
   (case type 
     :error   (message/error-ant-message message)
     :success (message/success-ant-message message)
     :warn    (message/warn-ant-message message)
     :info    (message/info-ant-message message))))

(rf/reg-sub
 :repo-search/suggestions
 (fn [db]
   (get db :repos-suggested)))

(rf/reg-sub
 :repo-search/status
 (fn [db]
   (get db :repos-search-status)))

(rf/reg-event-db
 :repo-search/update-suggestions
 (fn [db [_ suggestions]]
   (assoc db :repos-suggested (map #(select-keys % repo-fields) suggestions))))

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





(rf/reg-sub
 :repos/watched
 (fn [db [_ & filters]]
   (->> (get db :repos-watching)
        (sort-by (comp second :full_name)))))

(rf/reg-event-fx
 :repos/pull-info
 (fn [{:keys [db]} [_ query]]
   #_{:db         (assoc db :repos-search-status :loading)
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
   {:db  (assoc-in db [:repos-watching (:full_name repo)] repo)}))

(rf/reg-event-fx
 :repos/unwatch-repo
 (fn [{:keys [db]} [_ repo]]
   {:db  (update db :repos-watching dissoc (:full_name repo))}))






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