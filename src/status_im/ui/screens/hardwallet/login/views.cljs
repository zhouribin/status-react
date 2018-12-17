(ns status-im.ui.screens.hardwallet.login.views
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [status-im.ui.screens.hardwallet.pin.views :as pin.views]
            [status-im.ui.screens.hardwallet.components :as components]
            [status-im.ui.screens.hardwallet.login.styles :as styles]
            [status-im.ui.components.react :as react]
            [status-im.ui.components.styles :as components.styles]))

(defview hardwallet-login []
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