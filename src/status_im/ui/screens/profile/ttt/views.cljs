(ns status-im.ui.screens.profile.ttt.views
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [status-im.ui.components.react :as react]
            [status-im.ui.components.status-bar.view :as status-bar]
            [status-im.ui.components.toolbar.view :as toolbar]
            [status-im.ui.components.toolbar.actions :as actions]
            [status-im.ui.components.colors :as colors]
            [status-im.react-native.js-dependencies :as js-dependencies]
            [status-im.react-native.resources :as resources]
            [status-im.ui.components.common.common :as components.common]
            [status-im.utils.money :as money]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [taoensso.timbre :as log]
            [status-im.ui.components.text-input.view :as text-input]
            [status-im.ui.components.icons.vector-icons :as icons]
            [status-im.ui.components.common.common :as components.common]
            [status-im.ui.components.common.styles :as components.common.styles]
            [clojure.string :as string]
            [status-im.utils.config :as config]
            [status-im.utils.utils :as utils]
            [status-im.ui.screens.profile.ttt.styles :as styles]
            [status-im.i18n :as i18n]
            [status-im.ui.components.styles :as common.styles]
            [status-im.utils.platform :as platform]))

(defn steps-numbers [editing?]
  {:intro                1
   :set-snt-amount       (if editing? 1 2)
   :personalized-message (if editing? 2 3)
   :finish               3})

(defn step-back [step editing?]
  (re-frame/dispatch
   (case step
     (:intro :edit)                [:navigate-back]
     (:learn-more :set-snt-amount) [:set-in [:my-profile/tribute-to-talk :step]
                                    (if editing? :edit :intro)]
     :personalized-message         [:set-in [:my-profile/tribute-to-talk :step] :set-snt-amount]
     :finish                       [:set-in [:my-profile/tribute-to-talk :step] :personalized-message])))

(defn intro []
  [react/view {:style styles/intro-container}
   (when-not platform/desktop?
     [components.common/image-contain {:container-style styles/intro-image}
      (:ttt-logo resources/ui)])
   [react/i18n-text {:style styles/intro-text
                     :key   :tribute-to-talk}]
   [react/i18n-text {:style styles/description-label
                     :key   :tribute-to-talk-desc}]
   [react/text {:style    (assoc styles/description-label :color colors/blue)
                :on-press #(re-frame/dispatch [:set-in [:my-profile/tribute-to-talk :step] :learn-more])}
    (i18n/label :t/learn-more)]
   [components.common/separator]
   [components.common/button {:button-style styles/intro-button
                              :on-press     #(re-frame/dispatch [:set-in [:my-profile/tribute-to-talk :step] :set-snt-amount])
                              :label        (i18n/label :t/get-started)}]])

(defview snt-asset-value []
  (letsubs [snt-amount [:get-in [:my-profile/tribute-to-talk :snt-amount]]
            prices [:prices]
            currency [:wallet/currency]]
    (let [fiat-price (if snt-amount
                       (money/fiat-amount-value snt-amount
                                                :SNT
                                                (-> currency :code keyword)
                                                prices)
                       "0")]
      [react/text {:style styles/snt-asset-value}
       (str "~" fiat-price " " (:code currency))])))

(defview snt-amount-label []
  (letsubs [snt-amount [:get-in [:my-profile/tribute-to-talk :snt-amount]]]
    (let [snt-amount (or snt-amount "0")]
      [react/view {:style styles/snt-amount-container}
       [react/text {:style styles/snt-amount-label
                    :number-of-lines 1
                    :ellipsize-mode :middle}
        [react/text {:style styles/snt-amount} snt-amount]
        " SNT"]
       [snt-asset-value]])))

(defview number-view [n]
  (letsubs [snt-amount [:get-in [:my-profile/tribute-to-talk :snt-amount]]]
    (let [snt-amount (or snt-amount "0")
          ;; Put some logic in place so that incorrect numbers can not
          ;; be entered
          new-snt-amount (if (= n :remove)
                           (let [len (count snt-amount)
                                 s (subs snt-amount 0 (dec len))]
                             (if (string/ends-with? s ".")
                               (subs s 0 (- len 2))
                               s))
                           (cond (and (string/includes? snt-amount ".") (= n "."))
                                 snt-amount
                                 (and (= snt-amount "0") (not= n ".")) (str n)
                                 :else (str snt-amount n)))
          new-snt-amount (if (string/blank? new-snt-amount) "0" new-snt-amount)]
      [react/touchable-highlight
       {:on-press #(re-frame/dispatch [:set-in [:my-profile/tribute-to-talk :snt-amount]
                                       new-snt-amount])}
       [react/view {:style styles/number-container}
        (if (= n :remove)
          [icons/icon :icons/remove {:color colors/blue}]
          [react/text {:style styles/number} n])]])))

(defview number-row [elements]
  [react/view {:style {:flex-direction :row}}
   elements])

(defview number-pad []
  [react/view
   (->> (into (vec (range 1 10))
              ["." 0 :remove])
        (map (fn [n] ^{:key n} [number-view n]))
        (partition 3)
        (mapv (fn [elements]
                ^{:key elements} [number-row elements]))
        seq)])

(defview set-snt-amount []
  (letsubs [snt-amount [:get-in [:my-profile/tribute-to-talk :snt-amount]]]
    [react/view {:style styles/intro-container}
     [snt-amount-label]
     [number-pad]
     [react/i18n-text {:style styles/description-label
                       :key   :tribute-to-talk-set-snt-amount}]
     [components.common/separator]
     [components.common/button {:button-style styles/intro-button
                                :on-press     #(when (and (not (string/blank? snt-amount))
                                                          (not= snt-amount "0"))
                                                 (re-frame/dispatch [:set-in [:my-profile/tribute-to-talk :step] :personalized-message]))

                                :label        (i18n/label :t/continue)}]]))

(defview personalized-message []
  (letsubs [{:keys [message]} [:my-profile/tribute-to-talk]
            editing?          [:get :my-profile/editing?]]
    [react/view {:style styles/intro-container}
     [react/view
      [react/text {:style (assoc styles/description-label :color colors/black
                                 :text-align :left)}
       (i18n/label :t/personalized-message)
       [react/text {:style styles/description-label}
        (str " (" (i18n/label :t/optional) ")")]]
      [react/text-input (cond-> {:style styles/personalized-message-input
                                 :multiline true
                                 :on-change-text #(re-frame/dispatch [:set-in [:my-profile/tribute-to-talk :message] %1])
                                 :placeholder (i18n/label :t/tribute-to-talk-message-placeholder)}
                          (not (string/blank? message))
                          (assoc :default-value message))]
      [react/text {:style styles/description-label}
       (i18n/label :t/tribute-to-talk-you-can-leave-a-message)]]
     [components.common/separator]
     [components.common/button {:button-style styles/intro-button
                                :on-press     #(re-frame/dispatch [:set-in [:my-profile/tribute-to-talk :step] (if editing? :edit :finish)])

                                :label        (i18n/label :t/tribute-to-talk-sign-and-set-tribute)}]]))
(defview finish []
  (letsubs [amount [:get-in [:my-profile/tribute-to-talk :snt-amount]]]
    [react/view {:style (assoc styles/intro-container :margin-top 100)}
     [react/view {:style (styles/finish-circle colors/green-transparent-10 80)}
      [react/view {:style (styles/finish-circle colors/white 40)}
       [icons/icon :icons/check {:color colors/green
                                 :width 22
                                 :height 22}]]]
     [react/text {:style (assoc styles/finish-label
                                :margin-top 140)} (i18n/label :t/you-are-all-set)]
     [react/text {:style styles/description-label}
      (i18n/label :t/tribute-to-talk-finish-desc {:amount amount})]
     [components.common/button {:button-style styles/intro-button
                                :on-press     #(re-frame/dispatch [:navigate-back])
                                :label        (i18n/label :t/ok-got-it)}]]))
(defview ttt-enabled-note []
  [react/view {:style styles/enabled-note}
   [icons/icon :icons/info]
   [react/view {:style {:margin-left 11}}
    [react/text {:style styles/enabled-note-text}
     (i18n/label :t/tribute-to-talk-enabled)]
    [react/text {:style {:font-size 15}}
     (i18n/label :t/tribute-to-talk-add-friends)]]])

(defview edit []
  (letsubs [{:keys [snt-amount message]} [:my-profile/tribute-to-talk]]
    [react/view {:style (assoc styles/intro-container
                               :margin-horizontal 16)}
     [react/view {:style {:flex 1}}
      [react/view {:style styles/edit-screen-top-row}
       [react/view {:style {:flex-direction :row
                            :align-items :center}}
        [react/view {:style (styles/icon-view colors/blue)}
         [icons/icon :icons/logo {:color colors/white :width 20 :height 20}]]
        [react/view {:style {:margin-left 16}}
         [react/text {:style styles/current-snt-amount}
          snt-amount [react/text {:style (assoc styles/current-snt-amount :color colors/gray)} " SNT"]]
         [snt-asset-value]]]
       [react/text {:on-press #(do
                                 (re-frame/dispatch [:set-in [:my-profile/tribute-to-talk :step] :set-snt-amount])
                                 (re-frame/dispatch [:set :my-profile/editing? true]))
                    :style styles/edit-label}
        (i18n/label :t/edit)]]
      [react/text-input {:style styles/edit-view-message
                         :multiline true
                         :editable false
                         :default-value message}]
      [react/text {:style {:font-size 15 :color colors/gray :margin-top 16}}
       (i18n/label :t/tribute-to-talk-you-require-snt)]]

     [react/view
      [react/touchable-highlight {:on-press #(do
                                               (re-frame/dispatch [:set-in [:my-profile/tribute-to-talk :snt-amount] nil])
                                               (re-frame/dispatch [:navigate-back]))
                                  :style styles/remove-view}
       [react/view {:style {:flex-direction :row}}
        [react/view {:style (styles/icon-view (colors/alpha colors/red 0.1))}
         [icons/icon :icons/power {:color colors/red}]]
        [react/text {:style styles/remove-text}
         (i18n/label :t/remove)]]]
      [react/text {:style styles/remove-note}
       (i18n/label :t/tribute-to-talk-removing-note)]]

     [ttt-enabled-note]]))

(defview chat-sample []
  [react/view {:style styles/learn-more-section}
   [react/view {:style {:flex-direction :row}}
    [react/view {:style {:background-color colors/blue
                         :width 16 :height 16
                         :border-radius 8}}
     [icons/icon :icons/tribute-to-talk {:color colors/white}]]
    [react/text {:style {:color colors/gray :font-size 13 :margin-left 4}}
     (i18n/label :t/tribute-to-talk)]]
   [react/view {:style styles/chat-sample-bubble}
    [react/text {:style {:font-size 15 :color colors/black}}
     (i18n/label :t/tribute-to-talk-sample-text)]]
   [react/view {:style (assoc styles/chat-sample-bubble :width 141)}
    [react/text {:style {:font-size 22 :color colors/black}} "1000"
     [react/text {:style {:font-size 22 :color colors/gray}} " SNT"]]
    [react/text {:style {:font-size 12 :color colors/black}}
     "~3.48"
     [react/text {:style {:font-size 12 :color colors/gray}} " USD"]]
    [react/text {:style {:font-size 15 :color colors/blue}}
     (i18n/label :t/pay-to-chat)]]])

(defview learn-more []
  [react/view {:style (assoc styles/intro-container :align-items :flex-start)}
   [components.common/image-contain {:container-style (assoc styles/intro-image :width 100 :height 100)}
    (:ttt-logo resources/ui)]

   [react/text {:style styles/intro-text}
    (i18n/label :t/tribute-to-talk)]
   [react/text {:style styles/learn-more-text}
    (i18n/label :t/tribute-to-talk-learn-more-1)]
   [chat-sample]
   [react/text {:style styles/learn-more-text}
    (i18n/label :t/tribute-to-talk-learn-more-2)]
   [react/view {:style (assoc styles/learn-more-section
                              :flex-direction :row
                              :align-item :flex-stretch
                              :padding-horizontal 16
                              :padding-vertical 12)}
    [react/view {:style (styles/icon-view colors/blue-light)}
     [icons/icon :icons/add-contact {:color colors/blue :width 20 :height 20}]]

    [react/text {:style (assoc styles/learn-more-text :color colors/blue
                               :margin-left 16)}
     (i18n/label :t/add-to-contacts)]]
   [react/text {:style styles/learn-more-text}
    (i18n/label :t/tribute-to-talk-learn-more-3)]])

(defview tribute-to-talk []
  (letsubs [current-account [:account/account]
            {:keys [step]} [:my-profile/tribute-to-talk]
            editing? [:get :my-profile/editing?]]
    [react/keyboard-avoiding-view {:style styles/ttt-container}
     [status-bar/status-bar]
     [toolbar/toolbar
      nil
      (when-not (= :finish step)
        (toolbar/nav-button (actions/back #(step-back step editing?))))
      [react/view
       [react/text {:style styles/tribute-to-talk}
        (i18n/label :t/tribute-to-talk)]
       (when-not (#{:edit :learn-more} step)
         [react/text {:style styles/step-n}
          (i18n/label :t/step-i-of-n {:step ((steps-numbers editing?) step)
                                      :number (if editing? 2 3)})])]]
     [components.common/separator]
     (case step
       :intro [intro]
       :set-snt-amount [set-snt-amount]
       :edit [edit]
       :learn-more [learn-more]
       :personalized-message [personalized-message]
       :finish [finish])]))
