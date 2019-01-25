(ns status-im.accounts.update.publisher
  (:require [re-frame.core :as re-frame]
            [re-frame.db]
            [status-im.constants :as constants]
            [status-im.accounts.update.core :as accounts]
            [status-im.pairing.core :as pairing]
            [status-im.utils.async :as async-util]
            [status-im.data-store.accounts :as accounts-store]
            [status-im.utils.datetime :as datetime]
            [status-im.transport.message.protocol :as protocol]
            [status-im.transport.message.contact :as message.contact]
            [status-im.transport.shh :as shh]
            [status-im.utils.fx :as fx]))

(defonce polling-executor (atom nil))
(def sync-interval-ms 120000)
(def sync-timeout-ms  20000)
;; Publish updates every 48 hours
(def publish-updates-interval (* 24 60 60 1000))

(defn publish-update [web3 done-fn]
  (let [db @re-frame.db/app-db
        my-public-key (get-in db [:account/account :public-key])
        peers-count (:peers-count db)
        now (datetime/timestamp)
        last-updated (get-in
                      db
                      [:account/account :last-updated])]
    (when (and (pos? peers-count)
               (pos? last-updated)
               (< publish-updates-interval
                  (- now last-updated)))
      (let [public-keys  (accounts/contact-public-keys {:db db})
            payload      (accounts/account-update-message {:db db})
            sync-message (pairing/sync-installation-account-message {:db db})]
        (doseq [pk public-keys]
          (shh/send-direct-message!
           web3
           {:pubKey pk
            :sig my-public-key
            :chat constants/contact-discovery
            :payload payload}
           [:accounts.update.callback/published]
           [:accounts.update.callback/failed-to-publish]
           1))
        (shh/send-direct-message!
         web3
         {:pubKey my-public-key
          :sig my-public-key
          :chat constants/contact-discovery
          :payload sync-message}
         [:accounts.update.callback/published]
         [:accounts.update.callback/failed-to-publish]
         1)))
    (done-fn)))

(defn- start-publisher! [web3]
  (when @polling-executor
    (async-util/async-periodic-stop! @polling-executor))
  (reset! polling-executor
          (async-util/async-periodic-exec
           (partial publish-update web3)
           sync-interval-ms
           sync-timeout-ms)))

(re-frame/reg-fx
 ::start-publisher
 #(start-publisher! %))

(re-frame/reg-fx
 ::stop-publisher
 #(when @polling-executor
    (async-util/async-periodic-stop! @polling-executor)))

(fx/defn start-fx [{:keys [web3]}]
  {::start-publisher web3})

(fx/defn stop-fx [cofx]
  {::stop-publisher []})
