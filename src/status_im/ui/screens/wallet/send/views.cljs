(ns status-im.ui.screens.wallet.send.views
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [status-im.ui.screens.wallet.send.views.recipient :as recipient]
            [status-im.ui.screens.wallet.send.views.amount :as amount]
            [status-im.ui.screens.wallet.send.views.overview :as overview]
            [status-im.utils.ethereum.tokens :as tokens]
            [status-im.utils.ethereum.core :as ethereum]
            [reagent.core :as reagent]))

(defview choose-amount-token []
  (letsubs [{:keys [transaction modal? contact native-currency]} [:get-screen-params :wallet-choose-amount]
            balance       [:balance]
            prices        [:prices]
            network       [:account/network]
            all-tokens    [:wallet/all-tokens]
            fiat-currency [:wallet/currency]]
    [amount/render-choose-amount {:balance               balance
                                  :network         network
                                  :all-tokens      all-tokens
                                  :modal?          modal?
                                  :prices          prices
                                  :native-currency native-currency
                                  :fiat-currency   fiat-currency
                                  :contact         contact
                                  :transaction     transaction}]))

(defview transaction-overview []
  (letsubs [{:keys [transaction flow contact]} [:get-screen-params :wallet-txn-overview]
            prices                             [:prices]
            network                            [:account/network]
            all-tokens                         [:wallet/all-tokens]
            fiat-currency                      [:wallet/currency]]
    (let [chain           (ethereum/network->chain-keyword network)
          native-currency (tokens/native-currency chain)
          token           (tokens/asset-for all-tokens
                                            (ethereum/network->chain-keyword network) (:symbol transaction))]
      [overview/render-transaction-overview {:transaction transaction
                                             :flow               flow
                                             :contact            contact
                                             :prices             prices
                                             :network            network
                                             :token              token
                                             :native-currency    native-currency
                                             :fiat-currency      fiat-currency
                                             :all-tokens         all-tokens
                                             :chain              chain}])))

;; MAIN SEND TRANSACTION VIEW
(defn- send-transaction-view [_opts]
  (reagent/create-class
   {:reagent-render (fn [opts] [recipient/render-choose-recipient opts])}))

;; SEND TRANSACTION FROM WALLET (CHAT)
(defview send-transaction []
  (letsubs [transaction    [:wallet.send/transaction]
            network        [:account/network]
            network-status [:network-status]
            all-tokens     [:wallet/all-tokens]
            contacts       [:contacts/all-added-people-contacts]]
    [send-transaction-view {:modal?         false
                            :transaction    (dissoc transaction :gas :gas-price)
                            :network        network
                            :all-tokens     all-tokens
                            :contacts       contacts
                            :network-status network-status}]))

;; SEND TRANSACTION FROM DAPP
(defview send-transaction-modal []
  (letsubs [{:keys [transaction flow contact]} [:get-screen-params :wallet-send-transaction-modal]
            prices                             [:prices]
            network                            [:account/network]
            all-tokens                         [:wallet/all-tokens]
            fiat-currency                      [:wallet/currency]]
    (let [chain           (ethereum/network->chain-keyword network)
          native-currency (tokens/native-currency chain)
          token           (tokens/asset-for all-tokens
                                            (ethereum/network->chain-keyword network) (:symbol transaction))]
      [overview/render-transaction-overview {:transaction transaction
                                             :flow               flow
                                             :contact            contact
                                             :prices             prices
                                             :network            network
                                             :token              token
                                             :native-currency    native-currency
                                             :fiat-currency      fiat-currency
                                             :all-tokens         all-tokens
                                             :chain              chain}])))
