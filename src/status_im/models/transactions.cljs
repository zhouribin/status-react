(ns status-im.models.transactions
  (:require [clojure.set :as set]
            [status-im.utils.datetime :as time]
            [status-im.utils.ethereum.core :as ethereum]
            [status-im.utils.ethereum.tokens :as tokens]
            [status-im.utils.semaphores :as semaphores]
            [taoensso.timbre :as log]
            [status-im.utils.fx :as fx]))

(def sync-interval-ms 15000)
(def confirmations-count-threshold 12)

;; TODO is it a good idea for :confirmations to be a string?
;; Seq[transaction] -> truthy
(defn- have-unconfirmed-transactions?
  "Detects if some of the transactions have less than 12 confirmations"
  [transactions]
  {:pre [(every? string? (map :confirmations transactions))]}
  (->> transactions
       (map :confirmations)
       (map int)
       (some #(< % confirmations-count-threshold))))

;; this could be a util but ensure that its needed elsewhere before moving
(defn- keyed-memoize
  "Takes a key-function that decides the name of the cached entry.
  Takes a value function that will invalidate the cache if it changes.
  And finally the function to memoize.

  Memoize that doesn't grow bigger than the number of keys."
  [key-fn val-fn f]
  (let [val-store (atom {})
        res-store (atom {})]
    (fn [arg]
      (let [k (key-fn arg)
            v (val-fn arg)]
        (if (not= (get @val-store k) v)
          (let [res (f arg)]
            #_(prn "storing!!!!" res)
            (swap! val-store assoc k v)
            (swap! res-store assoc k res)
            res)
          (get @res-store k))))))

;; Map[id, chat] -> Set[transaction-id]
(let [chat-map-entry->transaction-ids
      (keyed-memoize key (comp :messages val)
                     (fn [[_ chat]]
                       (->> (:messages chat)
                            vals
                            (filter #(= "command" (:content-type %)))
                            (keep #(get-in % [:content :params :tx-hash])))))]
  (defn- chat-map->transaction-ids [chat-map]
    {:pre [(every? :messages (vals chat-map))]
     :post [(set? %)]}
    (->> chat-map
         (remove (comp :public? val))
         (mapcat chat-map-entry->transaction-ids)
         set)))

(fx/defn schedule-sync [cofx]
  {:utils/dispatch-later [{:ms       sync-interval-ms
                           :dispatch [:sync-wallet-transactions]}]})

(fx/defn run-update [{{:keys [network network-status web3] :as db} :db}]
  (when (not= network-status :offline)
    (let [network (get-in db [:account/account :networks network])
          chain (ethereum/network->chain-keyword network)]
      (when-not (= :custom chain)
        (let [all-tokens (tokens/tokens-for chain)
              token-addresses (map :address all-tokens)]
          (log/debug "Syncing transactions data..")
          {:get-transactions {:account-id      (get-in db [:account/account :address])
                              :token-addresses token-addresses
                              :chain           chain
                              :web3            web3
                              :success-event   :update-transactions-success
                              :error-event     :update-transactions-fail}
           :db               (-> db
                                 (update-in [:wallet :errors] dissoc :transactions-update)
                                 (assoc-in [:wallet :transactions-loading?] true)
                                 (assoc-in [:wallet :transactions-last-updated-at] (time/timestamp)))})))))

(defn- time-to-sync? [cofx]
  (let [last-updated-at (get-in cofx [:db :wallet :transactions-last-updated-at])]
    (or (nil? last-updated-at)
        (< sync-interval-ms
           (- (time/timestamp) last-updated-at)))))

(fx/defn sync
  "Fetch updated data for any unconfirmed transactions or incoming chat transactions missing in wallet
  and schedule new recurring sync request"
  [{:keys [db] :as cofx}]
  (if (:account/account db)
    (let [in-progress? (get-in db [:wallet :transactions-loading?])
          {:keys [app-state network-status wallet chats]} db
          chat-transaction-ids (chat-map->transaction-ids chats)
          transaction-map (:transactions wallet)
          transaction-ids (set (keys transaction-map))]
      (assert (set? chat-transaction-ids))
      (if (and (not= network-status :offline)
               (= app-state "active")
               (not in-progress?)
               (time-to-sync? cofx)
               (or (have-unconfirmed-transactions? (vals transaction-map))
                   (not-empty (set/difference chat-transaction-ids transaction-ids))))
        (fx/merge cofx
                  (run-update)
                  (schedule-sync))
        (schedule-sync cofx)))
    (semaphores/free cofx :sync-wallet-transactions?)))

(fx/defn start-sync [cofx]
  (when-not (semaphores/locked? cofx :sync-wallet-transactions?)
    (fx/merge cofx
              (semaphores/lock :sync-wallet-transactions?)
              (sync))))
