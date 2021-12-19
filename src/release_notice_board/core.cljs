(ns release-notice-board.core
  (:require
   [clojure.string :as string]

   [reagent.core :as r]
   [reagent.dom :as d]
   [re-frame.core :as rf]
   
   [release-notice-board.utils :as utils]
   [release-notice-board.events-n-handlers :as handlers]

   ;; Components
   [syn-antd.button :as button]
   [syn-antd.col    :as col]
   [syn-antd.input  :as input]
   [syn-antd.layout :as layout]
   [syn-antd.modal  :as modal]
   [syn-antd.row    :as row]
   [syn-antd.space  :as space]
   
   [syn-antd.icons.notification-filled :as notification-filled]
   [syn-antd.icons.notification-outlined :as notification-outlined]
   
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
          [:a {:href html_url :target :_blank} full_name]
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

(defn repo-item-toolbar [repo-full-name unread?]
  [space/space {:direction :vertical :align :center}
   [button/button {:shape    :circle
                   :size     :small
                   :on-click #(rf/dispatch [:repos/unwatch-repo repo-full-name])}
    "-"]
   (if unread?
     [notification-filled/notification-filled
      {:style    {:color :orange}
       :on-click #(rf/dispatch [:repos/mark-read repo-full-name])}]
     [notification-outlined/notification-outlined
      {:style    {:color :grey}
       :on-click #(rf/dispatch [:repos/mark-unread repo-full-name])}])])

(defn repo-item-summary
  [{repo-full-name :full_name
    :keys [description html_url language watchers tag_name published_at]}]
  [:div
   [:a {:href html_url :target :_blank} repo-full-name]
   [:p.repo__description description]
   [row/row {:class "repo__other-details"}
    [col/col {:xs 12 :lg 6} "language: " language]
    [col/col {:xs 12 :lg 6} "watchers: " watchers]
    [col/col {:xs 12 :lg 6} "latest-release: " tag_name]
    [col/col {:xs 12 :lg 6} "published: " published_at]]])

(defn watched-repo-item
  [{repo-full-name :full_name 
    :keys [last-seen] 
    :as   repo}]
  (let [unread? (not last-seen)]
    [:div.repo-list_list-item.repo-list_list-item--hoverable
     {:class    (if unread? "repo-list_list-item--unread" "")}
     [row/row {:wrap false}
      [col/col {:flex "30px"}
       [repo-item-toolbar repo-full-name unread?]]
      [col/col {:flex     "auto"
                :on-click #(rf/dispatch [:releases/open-latest-notes repo-full-name])}
       [repo-item-summary repo]]]]))

(defn watched-repo-section []
  (let [repos @(rf/subscribe [:repos/watched-sorted])]
    [:section.section
     [:h2.section__title "Watching"]
     [:div.repo-list
      (for [[full_name repo] repos]
        ^{:key full_name}
        [watched-repo-item repo])]]))

(defn testing-stuuf []
  [:div
   [:button {:on-click #(rf/dispatch [:repo-search/find "Octokit"])}
    "Search"]
   [:button {:on-click #(rf/dispatch [:releases/load-notes "SpinlockLabs/github.dart" 1])}
    "Releases"]])

(defn release-details []
  (let [{repo-full-name :full_name
         :keys [body tag_name published_at html_url]
         :or   {published_at "N/A"}}       @(rf/subscribe [:releases/current-latest])]
    
    [modal/modal {:class     "details-view"
                  :visible   (some? repo-full-name)
                  :width     "100%"
                  :footer    nil
                  :on-cancel #(rf/dispatch [:releases/close-latest-notes])}
     [:section.section
      [:a {:href html_url :target :_blank} repo-full-name]
      [:h2.section__title (str  "Release Notes " tag_name)]
      [:p.release__published-date "Published: " published_at]
      [:p.release__notes body]]]))

(defn home-page []
  [layout/layout
   [layout/layout-content {:style {:min-height :100vh}}
    [:div.page
     [:h1.page__heading "Dashboard"]
     [repo-search-section]
     [watched-repo-section]
     [release-details]
     [:br]
     [testing-stuuf]]]])

;; -------------------------
;; Initialize app

(defn mount-root []
  (d/render [home-page] (.getElementById js/document "app")))

(defn ^:export init! []
  (rf/dispatch-sync [:initialize-db])
  (mount-root))
