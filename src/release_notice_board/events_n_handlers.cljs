(ns release-notice-board.events-n-handlers
  (:require
   [clojure.string :as string]

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
   :current-repo        nil})

(def repo-fields
  #{:full_name :forks :description :html_url :language :score :watchers})

(def repo-fields-release
  #{:published_at :tag_name :body})

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
 :session/user
 (fn [db]
   (get db :current-user)))

(rf/reg-event-fx
 :session/change-user
 (fn [{:keys [db]} [_ username]]
   {:db (assoc db :current-user username)}))

(rf/reg-event-fx
 :session/logout-user
 (fn [{:keys [db]} _]
   {:db (assoc db :current-user nil)}))


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
   (assoc db :repos-suggested (->> suggestions
                                   (map #(select-keys % repo-fields))
                                   (sort-by (comp string/lower-case :full_name))))))

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




;; (rf/reg-event-fx
;;  :releases/load-notes-succeeded
;;  (fn [{:keys [db]} [_ {:keys [items]}]]
;;    {}))

;; (rf/reg-event-fx
;;  :releases/load-notes-failed
;;  (fn [db _]
;;    {}))

;; (rf/reg-event-fx
;;  :releases/load-notes
;;  (fn [{:keys [db]} [_ repo-full-name page]]
;;    {:db         (assoc db :repos-search-status :loading)
;;     :http-xhrio {:method          :get
;;                  :uri             (str "https://api.github.com/repos/" repo-full-name "/releases")
;;                  :params          {:per_page 10
;;                                    :page     page}
;;                  :format          (ajax/json-request-format)
;;                  :response-format (ajax/json-response-format {:keywords? true})
;;                  :on-success      [:repo-search/find-succeeded]
;;                  :on-failure      [:repo-search/find-failed]}}))



(rf/reg-event-fx
 :releases/load-latest-succeeded
 (fn [{:keys [db]} [_ repo-full-name release-notes]]
   {:db (update-in db
                   [:repos-watching repo-full-name]
                   merge
                   (select-keys (first release-notes) repo-fields-release))}))

(rf/reg-event-fx
 :releases/load-latest-failed
 (fn [db [_ repo-full-name]]
   {:show-message [:error (str "Could not file release data for " repo-full-name)]}))

(rf/reg-event-fx
 :releases/load-latest
 (fn [{:keys [db]} [_ repo-full-name]]
   {:db         (assoc db :repos-search-status :loading)
    :http-xhrio {:method          :get
                 :uri             (str "https://api.github.com/repos/" repo-full-name "/releases")
                 :params          {:per_page 1}
                 :format          (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:releases/load-latest-succeeded repo-full-name]
                 :on-failure      [:releases/load-latest-failed repo-full-name]}}))

(rf/reg-sub
 :releases/current-repo
 (fn [db]
   (get db :current-repo)))

(rf/reg-sub
 :releases/current-latest
 :<-[:repos/watched]
 :<-[:releases/current-repo]
 (fn [[repos repo-full-name] _]
   ;; NOTE: maybe it wasn't a good idea to write 
   ;;       release notes into the same map as the watched repos
   (get repos repo-full-name)))

(rf/reg-event-fx
 :releases/open-latest-notes
 (fn [{:keys [db]} [_ repo-full-name]]
   {:db  (assoc db :current-repo repo-full-name)
    :fx  [[:dispatch [:repos/mark-read repo-full-name]]]}))

(rf/reg-event-fx
 :releases/close-latest-notes
 (fn [{:keys [db]} _]
   {:db  (assoc db :current-repo nil)}))


(rf/reg-sub
 :repos/watched
 (fn [db [_ & filters]]
   (get db :repos-watching)))

(rf/reg-sub
 :repos/watched-sorted
 :<- [:repos/watched]
 (fn [repos [_ & filter]]
   (sort-by (comp string/lower-case :full_name second) repos)))

#_(rf/reg-event-fx
 :repos/load-latest-info
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
 (fn [{:keys [db]} [_ {repo-full-name :full_name :as repo}]]
   {:db  (assoc-in db [:repos-watching repo-full-name] repo)
    :fx  [[:dispatch [:releases/load-latest repo-full-name]]]}))

(rf/reg-event-fx
 :repos/unwatch-repo
 (fn [{:keys [db]} [_ repo-full-name]]
   {:db  (update db :repos-watching dissoc repo-full-name)}))

(rf/reg-sub
 :repos/read?
 :<- [:repos/watched]
 (fn [repos [_ repo-full-name]]
   (let [{:keys [published_at last-seen]} (get repos repo-full-name)]
     ;; NOTE: Very lazy way of seeing whether the user has read the release
     ;;       This should probably work assuming that the user doesn't time
     (= published_at last-seen))))

(rf/reg-event-fx
 :repos/mark-read 
 (fn [{:keys [db]} [_ repo-full-name]]
   (let [published_at (get-in db [:repos-watching repo-full-name :published_at])]
     {:db  (assoc-in db [:repos-watching repo-full-name :last-seen] published_at)})))

(rf/reg-event-fx
 :repos/mark-unread
 (fn [{:keys [db]} [_ repo-full-name]]
   {:db  (assoc-in db [:repos-watching repo-full-name :last-seen] nil)}))

