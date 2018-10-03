(ns status-im.ui.components.context-menu.view
  (:require [status-im.react-native.js-dependencies :as rn-dependencies]))

(def context-menu (.-default rn-dependencies/context-menu))

(defn show [{:keys [text]}]
  (.show rn-dependencies/context-menu text))