(ns release-notice-board.utils
  (:import [goog.async Debouncer]))

(defn element-value
  "Gets the value of the targeted element"
  [e]
  (-> e .-target .-value))

(defn debounce 
  "Calls f only after some interval of inactivity to prevent spamming events
   
   From 
   https://martinklepsch.org/posts/simple-debouncing-in-clojurescript.html"
  [f interval]
  (let [dbnc (Debouncer. f interval)]
    ;; We use apply here to support functions of various arities
    (fn [& args] (.apply (.-fire dbnc) dbnc (to-array args)))))
