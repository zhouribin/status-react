(ns status-im.ui.screens.hardwallet.login.views
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [status-im.ui.screens.hardwallet.pin.views :as pin.views]
            [status-im.ui.screens.hardwallet.connect.views :as connect.views]
            [status-im.ui.screens.hardwallet.components :as components]
            [status-im.ui.screens.hardwallet.login.styles :as styles]
            [status-im.ui.components.react :as react]
            [status-im.ui.components.styles :as components.styles]
            [status-im.ui.components.status-bar.view :as status-bar]
            [status-im.ui.components.toolbar.view :as toolbar]
            [status-im.ui.components.common.common :as components.common]
            [status-im.ui.screens.chat.photos :as photos]
            [status-im.i18n :as i18n]
            [status-im.ui.components.toolbar.actions :as toolbar.actions]
            [status-im.ui.components.react :as react.components]
            [re-frame.core :as re-frame]))

(defview hardwallet-login-pin []
  (letsubs [pin [:hardwallet/login-pin]
            status [:hardwallet/pin-status]
            error-label [:hardwallet/pin-error-label]]
    [react/keyboard-avoiding-view components.styles/flex
     [react/view styles/container
      [react/view styles/inner-container
       [components/maintain-card nil]
       [pin.views/pin-view {:pin               pin
                            :title-label       :t/enter-pin
                            :description-label :t/enter-pin-description
                            :step              :login
                            :status            status
                            :error-label       error-label}]]]]))

(defview hardwallet-login []
  (letsubs [{:keys [photo-path name processing]} [:get :accounts/login]
            nfc-enabled? [:hardwallet/nfc-enabled?]
            can-navigate-back? [:can-navigate-back?]]
    [react/keyboard-avoiding-view styles/container
     [status-bar/status-bar]
     [toolbar/toolbar
      nil
      (when can-navigate-back?
        [toolbar/nav-button toolbar.actions/default-back])
      [toolbar/content-title (i18n/label :t/sign-in-to-status)]]
     [components.common/separator]
     [react/view styles/login-view
      [react/view styles/login-badge-container
       [react/view styles/login-badge
        [photos/photo photo-path {:size styles/login-badge-image-size}]
        [react/view
         [react/text {:style         styles/login-badge-name
                      :numberOfLines 1}
          name]]]
       [react/view {:margin-top -50}
        (if nfc-enabled?
          [connect.views/nfc-enabled]
          [connect.views/nfc-disabled])]]]
     (when processing
       [react/view styles/processing-view
        [react.components/activity-indicator {:animating true}]
        [react/i18n-text {:style styles/sign-you-in :key :sign-you-in}]])
     (when-not processing
       [react/view {:style styles/bottom-button-container}
        (when-not can-navigate-back?
          [components.common/bottom-button
           {:label    (i18n/label :t/other-accounts)
            :on-press #(re-frame/dispatch [:navigate-to-clean :accounts])}])
        [react/view {:style {:flex 1}}]])]))