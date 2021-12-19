(ns release-notice-board.core
  (:require
   [clojure.string :as string]

   [reagent.core :as r]
   [reagent.dom :as d]
   [re-frame.core :as rf]
   
   [release-notice-board.utils :as utils]
   [release-notice-board.events-n-handlers :as handlers]

   ;; Components
   [syn-antd.layout :as layout]
   [syn-antd.input  :as input]
   [syn-antd.row    :as row]
   [syn-antd.col    :as col]
   [syn-antd.button :as button]
   [syn-antd.space  :as space]
   
   [syn-antd.icons.notification-filled :as notification-filled]
   
   ))

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
    [:div.repo-list
     (for [{:keys [full_name forks description html_url language score watchers] 
            :as   repo} repos]
       ^{:key full_name}
       [:div.repo-list_list-item
        [row/row {:wrap false}
         [col/col {:flex "30px"}
          [button/button {:shape    :circle
                          :size     :small
                          :on-click #(rf/dispatch [:repos/watch-repo repo])}
           "+"]]
         [col/col {:flex "auto"}
          [:a {:href html_url} full_name]
          [:p.repo__description description]
          [row/row {:class "repo__other-details"}
           [col/col {:xs 12 :lg 6} "language: " language]
           [col/col {:xs 12 :lg 6} "watchers: " watchers]
           [col/col {:xs 12 :lg 6} "forks: " forks]
           [col/col {:xs 12 :lg 6} "score: " score]]]]])]))

(defn repo-search-section []
  [:section.section
   [:h2.section__title "Find Repos"]
   [repo-searchbar]
   [repo-suggestions]])


(defn repo-watching-section []
  (let [repos @(rf/subscribe [:repos/watched])]
    [:section.section
     [:h2.section__title "Watching"]
     [:div.repo-list
      (for [[full_name {:keys [description html_url language watchers tag_name published_at]
                        :as   repo}] repos]
        (let [unread? true]
          ^{:key full_name}
          [:div.repo-list_list-item.repo-list_list-item--hoverable 
           {:class    (if unread? "repo-list_list-item--unread" "")}
           [row/row {:wrap false}
            [col/col {:flex "30px"}
             [space/space {:direction :vertical
                           :align     :center}
              [button/button {:shape    :circle
                              :size     :small
                              :on-click #(rf/dispatch [:repos/unwatch-repo full_name])}
               "-"]
              (when unread?
                [notification-filled/notification-filled {:style {:color :orange}}])]]
            [col/col {:flex "auto"}
             [:a {:href html_url} full_name]
             [:p.repo__description description]
             [row/row {:class "repo__other-details"}
              [col/col {:xs 12 :lg 6} "language: " language]
              [col/col {:xs 12 :lg 6} "watchers: " watchers]
              [col/col {:xs 12 :lg 6} "latest-release: " tag_name]
              [col/col {:xs 12 :lg 6} "published: " published_at]]]]]))]]))

(defn testing-stuuf []
  [:div
   [:button {:on-click #(rf/dispatch [:repo-search/find "Octokit"])}
    "Search"]
   [:button {:on-click #(rf/dispatch [:releases/load-notes "SpinlockLabs/github.dart" 1])}
    "Releases"]])


(defn home-page []
  [layout/layout
   [layout/layout-content {:style {:min-height :100vh}}
    [:div.page
     [:h1.page__heading "Dashboard"]
     [repo-search-section]
     [repo-watching-section]
     [:br]
     [testing-stuuf]]]])

;; -------------------------
;; Initialize app

(defn mount-root []
  (d/render [home-page] (.getElementById js/document "app")))

(defn ^:export init! []
  (rf/dispatch-sync [:initialize-db])
  (mount-root))
