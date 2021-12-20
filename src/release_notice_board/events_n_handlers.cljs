(ns release-notice-board.events-n-handlers
  (:require
   [clojure.string :as string]

   [ajax.core :as ajax]
   [re-frame.core :as rf]
   [day8.re-frame.http-fx]
   
   [syn-antd.message :as message]
   [cljs.reader :as reader]))

;; ---- Definitions ----

(def default-db
  {;; Session Related
   :current-user        nil
   
   ;; Searching Related
   :repos-search-status :loading
   :repos-suggested     []

   ;; Repos % Release Related 
   :repos-watching      {}
   :current-repo        nil})

(def repo-fields
  #{:full_name :forks :description :html_url :language :stargazers_count :watchers})

(def repo-fields-release
  #{:published_at :tag_name :body})

;; ---- Handlers for Initialization ----

(rf/reg-event-fx
 :initialize-db
 [(rf/inject-cofx :local-storage/get-user)]
 (fn [{:keys [local-store]}_]
   {:db (assoc default-db :current-user (:current-user local-store))}))

(rf/reg-event-fx
 :initialize-dashboard 
 [(rf/inject-cofx :local-storage/get-data)]
 (fn [{:keys [db local-store]} _]
   {:db (assoc db :repos-watching (:repos-watching local-store))
    ;; NOTE: Not sure if this is the best way to check for updates...
    :fx (map
         (fn [repo-full-name]
           [:dispatch [:repo/reload-repo-details repo-full-name]])
         (keys (:repos-watching local-store)))}))

;; ---- Handlers for Errors ----

(rf/reg-fx
 :show-message
 (fn [[type message]]
   (case type
     :error   (message/error-ant-message message)
     :success (message/success-ant-message message)
     :warn    (message/warn-ant-message message)
     :info    (message/info-ant-message message))))

;; ---- Handlers for Local Storage ----

(rf/reg-cofx
 :local-storage/get-user
 (fn [cofx _]
   (assoc cofx :local-store {:current-user (.getItem js/localStorage :current-user)})))

(rf/reg-cofx
 :local-storage/get-data
 (fn [{:keys [db] :as cofx} _]
   (assoc cofx
          :local-store
          {:repos-watching (some->> (:current-user db)
                                    (.getItem js/localStorage)
                                    (reader/read-string))})))

(rf/reg-event-fx
 :local-storage/save-user
 (fn [_ [_ username]]
   (.setItem js/localStorage :current-user username)
   {}))

(rf/reg-event-fx
 :local-storage/save-data
 (fn [{:keys [db]} _]
   (.setItem js/localStorage (:current-user db) (pr-str (:repos-watching db)))
   {}))

;; ---- Handlers for User Session ----

(rf/reg-sub
 :session/user
 (fn [db]
   (get db :current-user)))

(rf/reg-event-fx
 :session/change-user
 (fn [{:keys [db]} [_ username]]
   {:db (assoc db :current-user username)
    :fx [[:dispatch [:local-storage/save-user username]]]}))

(rf/reg-event-fx
 :session/logout-user
 (fn [{:keys [db]} _]
   {:db (assoc db :current-user nil)
    :fx [[:dispatch [:local-storage/save-user nil]]]}))

;; ---- Handlers for searching ----

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

(rf/reg-event-fx
 :repo-search/find-failed
 (fn [{:keys [db]} _]
   {:db           (assoc db :repos-search-status :failed)
    :show-message [:error "Search could not be completed. API probably needs a second to breathe."]}))


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


;; ---- Handlers for repo watching ----

(rf/reg-event-fx
 :repo/reload-repo-details-succeeded
 (fn [{:keys [db]} [_ repo-full-name repo-details]]
   {:db (update-in db
                   [:repos-watching repo-full-name]
                   merge
                   (select-keys repo-details repo-fields))
    :fx [[:dispatch [:releases/load-latest repo-full-name]]]}))

(rf/reg-event-fx
 :repo/reload-repo-details-failed
 (fn [_ _]
   {}))

(rf/reg-event-fx
 :repo/reload-repo-details
 (fn [{:keys [db]} [_ repo-full-name]]
   {:http-xhrio {:method          :get
                 :uri             (str "https://api.github.com/repos/" repo-full-name)
                 :format          (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:repo/reload-repo-details-succeeded repo-full-name]
                 :on-failure      [:repo/reload-repo-details-failed repo-full-name]}}))

(rf/reg-sub
 :repo/watched
 (fn [db _]
   (get db :repos-watching)))

(rf/reg-sub
 :repo/watched-sorted
 :<- [:repo/watched]
 (fn [repos [_ & filter]]
   (sort-by (comp string/lower-case :full_name second) repos)))

(rf/reg-event-fx
 :repo/watch-repo
 (fn [{:keys [db]} [_ {repo-full-name :full_name :as repo}]]
   {:db  (assoc-in db [:repos-watching repo-full-name] repo)
    :fx  [[:dispatch [:local-storage/save-data]]
          [:dispatch [:releases/load-latest repo-full-name]]]}))

(rf/reg-event-fx
 :repo/unwatch-repo
 (fn [{:keys [db]} [_ repo-full-name]]
   {:db  (update db :repos-watching dissoc repo-full-name)
    :fx  [[:dispatch [:local-storage/save-data]]]}))

;; ---- Handlers for releases ----

(rf/reg-sub
 :releases/read?
 :<- [:repo/watched]
 (fn [repos [_ repo-full-name]]
   (let [{:keys [published_at last-seen]} (get repos repo-full-name)]
     ;; NOTE: Very lazy way of seeing whether the user has read the release
     ;;       This should probably work assuming that the user doesn't time
     (= published_at last-seen))))

(rf/reg-event-fx
 :releases/mark-read
 (fn [{:keys [db]} [_ repo-full-name]]
   (let [published_at (get-in db [:repos-watching repo-full-name :published_at])]
     {:db  (assoc-in db [:repos-watching repo-full-name :last-seen] published_at)
      :fx  [[:dispatch [:local-storage/save-data]]]})))

(rf/reg-event-fx
 :releases/mark-unread
 (fn [{:keys [db]} [_ repo-full-name]]
   {:db  (assoc-in db [:repos-watching repo-full-name :last-seen] nil)
    :fx  [[:dispatch [:local-storage/save-data]]]}))

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
   {:show-message [:error (str "Could not get new release data for " repo-full-name)]}))

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
 :releases/current-latest-release
 :<- [:repo/watched]
 :<- [:releases/current-repo]
 (fn [[repos repo-full-name] _]
   ;; NOTE: maybe it wasn't a good idea to write 
   ;;       release notes into the same map as the watched repos
   (get repos repo-full-name)))


(rf/reg-event-fx
 :releases/open-latest-notes
 (fn [{:keys [db]} [_ repo-full-name]]
   {:db  (assoc db :current-repo repo-full-name)
    :fx  [[:dispatch [:releases/mark-read repo-full-name]]]}))

(rf/reg-event-fx
 :releases/close-latest-notes
 (fn [{:keys [db]} _]
   {:db  (assoc db :current-repo nil)}))
