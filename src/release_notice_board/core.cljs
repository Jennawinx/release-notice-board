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
