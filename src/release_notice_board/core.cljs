(ns release-notice-board.core
  (:require
   [clojure.string :as string]

   [reagent.core :as r]
   [reagent.dom :as d]
   [re-frame.core :as rf]
   
   [release-notice-board.utils :as utils]
   [release-notice-board.events-n-handlers :as handlers]

   ;; Components
   [syn-antd.input  :as input]
   [syn-antd.button :as button]
   [syn-antd.space :as space]))

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
     (for [{:keys [full_name forks description html_url language score watchers] 
            :as   repo} repos]
       ^{:key full_name}
       [:div
        [space/space
         [button/button {:shape    :circle 
                         :size     :small
                         :on-click #(rf/dispatch [:repos/watch-repo repo])} 
          "+"]
         [:a {:href html_url} full_name]]])]))

(defn repo-search-section []
  [:div
   [:h2 "Find Repos"]
   [repo-searchbar]
   [repo-suggestions]])


(defn repo-list []
  (let [repos @(rf/subscribe [:repos/watched])]
    [:div
     [:h2 "Watching"]
     (for [[full_name {:keys [forks description html_url language score watchers]
                       :as   repo}] repos]
       ^{:key full_name}
       [:div
        [space/space
         [button/button {:shape    :circle
                         :size     :small
                         :on-click #(rf/dispatch [:repos/unwatch-repo full_name])}
          "-"]
         [:a {:href html_url} full_name]]])]))

(defn testing-stuuf []
  [:div
   [:button {:on-click #(rf/dispatch [:repo-search/find "Octokit"])}
    "Search"]
   [:button {:on-click #(rf/dispatch [:releases/load-notes "SpinlockLabs/github.dart" 1])}
    "Releases"]])


(defn home-page []
  [:div
   [:h1 "Dashboard"]
   [repo-list]
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
