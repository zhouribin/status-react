(ns status-im.models.transactions
  (:require [clojure.set :as set]
            [cljs.core.async :as async]
            [clojure.string :as string]
            [status-im.utils.async :as async-util]
            [status-im.utils.ethereum.core :as ethereum]
            [status-im.utils.ethereum.tokens :as tokens]
            [status-im.constants :as constants]
            [status-im.utils.transactions :as transactions]
            [status-im.native-module.core :as status]
            [status-im.utils.ethereum.tokens :as tokens]
            [status-im.utils.http :as http]
            [status-im.utils.types :as types]
            [taoensso.timbre :as log]
            [status-im.utils.fx :as fx]
            [re-frame.core :as re-frame]
            [re-frame.db])
  (:require-macros
   [cljs.core.async.macros :refer [go-loop go]]))

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
  "Space bounded memoize.

  Takes a key-function that decides the key in the cache for the
  memoized value. Takes a value function that will extract the value
  that will invalidate the cache if it changes.  And finally the
  function to memoize.

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

;; ----------------------------------------------------------------------------
;; transactions from eth-node
;; ----------------------------------------------------------------------------

(defn- parse-json [s]
  (try
    (let [res (-> s
                  js/JSON.parse
                  (js->clj :keywordize-keys true))]
      (if (= (:error res) "")
        {:result true}
        res))
    (catch :default e
      {:error (.-message e)})))

(defn- add-padding [address]
  (when address
    (str "0x000000000000000000000000" (subs address 2))))

(defn- remove-padding [topic]
  (if topic
    (str "0x" (subs topic 26))))

(defn- parse-transaction-entries [current-block-number block-info chain direction transfers]
  (into {}
        (keep identity
              (for [transfer transfers]
                (if-let [token (->> transfer :address (tokens/address->token chain))]
                  (when-not (:nft? token)
                    [(:transactionHash transfer)
                     {:block         (-> block-info :number str)
                      :hash          (:transactionHash transfer)
                      :symbol        (:symbol token)
                      :from          (-> transfer :topics second remove-padding)
                      :to            (-> transfer :topics last remove-padding)
                      :value         (-> transfer :data ethereum/hex->bignumber)
                      :type          direction

                      :confirmations (str (- current-block-number (-> transfer :blockNumber ethereum/hex->int)))

                      :gas-price     nil
                      :nonce         nil
                      :data          nil

                      :gas-limit     nil
                      :timestamp     (-> block-info :timestamp (* 1000) str)

                      :gas-used      nil

                      ;; NOTE(goranjovic) - metadata on the type of token: contains name, symbol, decimas, address.
                      :token         token

                      ;; NOTE(goranjovic) - if an event has been emitted, we can say there was no error
                      :error?        false

                      ;; NOTE(goranjovic) - just a flag we need when we merge this entry with the existing entry in
                      ;; the app, e.g. transaction info with gas details, or a previous transfer entry with old
                      ;; confirmations count.
                      :transfer      true}]))))))

(defn add-block-info [web3 current-block-number chain direction result success-fn]
  (let [transfers-by-block (group-by :blockNumber result)]
    (doseq [[block-number transfers] transfers-by-block]
      (ethereum/get-block-info web3 (ethereum/hex->int block-number)
                               (fn [block-info]
                                 (success-fn (parse-transaction-entries current-block-number
                                                                        block-info
                                                                        chain
                                                                        direction
                                                                        transfers)))))))

(defn- response-handler [web3 current-block-number chain direction error-fn success-fn]
  (fn handle-response
    ([response]
     (let [{:keys [error result]} (parse-json response)]
       (handle-response error result)))
    ([error result]
     (if error
       (error-fn error)
       (add-block-info web3 current-block-number chain direction result success-fn)))))

;;
;; Here we are querying event logs for Transfer events.
;;
;; The parameters are as follows:
;; - address - token smart contract address
;; - fromBlock - we need to specify it, since default is latest
;; - topics[0] - hash code of the Transfer event signature
;; - topics[1] - address of token sender with leading zeroes padding up to 32 bytes
;; - topics[2] - address of token sender with leading zeroes padding up to 32 bytes
;;

(defn get-token-transfer-logs
  ;; NOTE(goranjovic): here we use direct JSON-RPC calls to get event logs because of web3 event issues with infura
  ;; we still use web3 to get other data, such as block info
  [web3 current-block-number chain contracts direction address cb]
  (let [[from to] (if (= :inbound direction)
                    [nil (ethereum/normalized-address address)]
                    [(ethereum/normalized-address address) nil])
        args {:jsonrpc "2.0"
              :id      2
              :method  constants/web3-get-logs
              :params  [{:address   (map string/lower-case contracts)
                         :fromBlock "0x0"
                         :topics    [constants/event-transfer-hash
                                     (add-padding from)
                                     (add-padding to)]}]}
        payload (.stringify js/JSON (clj->js args))]
    (status/call-private-rpc payload
                             (response-handler web3 current-block-number chain direction ethereum/handle-error cb))))

(defn get-token-transactions
  [web3 chain contracts direction address cb]
  (ethereum/get-block-number web3
                             #(get-token-transfer-logs web3 % chain contracts direction address cb)))

;; --------------------------------------------------------------------------
;; etherscan tranasctions
;; --------------------------------------------------------------------------

(def etherscan-supported?
  #{:testnet :mainnet :rinkeby})

(defn- get-network-subdomain [chain]
  (case chain
    (:testnet) "ropsten"
    (:mainnet) nil
    (:rinkeby) "rinkeby"))

(defn get-transaction-details-url [chain hash]
  (when (etherscan-supported? chain)
    (let [network-subdomain (get-network-subdomain chain)]
      (str "https://" (when network-subdomain (str network-subdomain ".")) "etherscan.io/tx/" hash))))

(def etherscan-api-key "DMSI4UAAKUBVGCDMVP3H2STAMSAUV7BYFI")

(defn- get-api-network-subdomain [chain]
  (case chain
    (:testnet) "api-ropsten"
    (:mainnet) "api"
    (:rinkeby) "api-rinkeby"))

(defn get-transaction-url [chain account]
  (let [network-subdomain (get-api-network-subdomain chain)]
    (str "https://" network-subdomain ".etherscan.io/api?module=account&action=txlist&address=0x"
         account "&startblock=0&endblock=99999999&sort=desc&apikey=" etherscan-api-key "?q=json")))

(defn- format-transaction [account {:keys [value timeStamp blockNumber hash from to gas gasPrice gasUsed nonce confirmations input isError]}]
  (let [inbound? (= (str "0x" account) to)
        error?   (= "1" isError)]
    {:value         value
     ;; timestamp is in seconds, we convert it in ms
     :timestamp     (str timeStamp "000")
     :symbol        :ETH
     :type          (cond error?   :failed
                          inbound? :inbound
                          :else    :outbound)
     :block         blockNumber
     :hash          hash
     :from          from
     :to            to
     :gas-limit     gas
     :gas-price     gasPrice
     :gas-used      gasUsed
     :nonce         nonce
     :confirmations confirmations
     :data          input}))

(defn- format-transactions-response [response account]
  (->> response
       types/json->clj
       :result
       (reduce (fn [transactions {:keys [hash] :as transaction}]
                 (assoc transactions hash (format-transaction account transaction)))
               {})))

(defn- etherscan-transactions [chain account on-success on-error]
  (if (etherscan-supported? chain)
    (let [url (get-transaction-url chain account)]
      (log/debug "HTTP GET" url)
      (http/get url
                #(on-success (format-transactions-response % account))
                on-error))
    (log/info "Etherscan not supported for " chain)))

(defn get-transactions [{:keys [web3 chain account-address token-addresses success-fn error-fn]}]
  (etherscan-transactions chain
                          account-address
                          success-fn
                          error-fn)
  (doseq [direction [:inbound :outbound]]
    (get-token-transactions web3
                            chain
                            token-addresses
                            direction
                            account-address
                            success-fn)))

;; ---------------------------------------------------------------------------
;; Periodic background job 
;; ---------------------------------------------------------------------------

(defn- async-periodic-run! [async-periodic-chan]
  (async/put! async-periodic-chan true))

(defn- async-periodic-stop! [async-periodic-chan]
  (async/close! async-periodic-chan))

(defn- async-periodic-exec
  "Periodically execute an function.
  Takes a work-fn of one argument `finished-fn -> any` this function
  is passed a finished-fn that must be called to signal that the work
  being performed in the work-fn is finished.

  The work-fn can be forced to run immediately "
  [work-fn timeout-ms]
  {:pre [(integer? timeout-ms)]}
  (let [do-now-chan (async/chan (async/sliding-buffer 1))]
    (go-loop []
      (let [timeout (async-util/timeout timeout-ms)
            finished-chan (async/promise-chan)
            [v ch] (async/alts! [do-now-chan timeout])]
        (when-not (and (= ch do-now-chan) (nil? v))
          (work-fn #(async/put! finished-chan true))
          (async/<! finished-chan)
          (recur))))
    do-now-chan))

(defn new-sync [fetch-data-fn success-fn error-fn]
  (let [{:keys [app-state
                network-status
                network
                :account/account
                wallet web3 chats]}
        (fetch-data-fn)
        chat-transaction-ids (chat-map->transaction-ids chats)
        transaction-map (:transactions wallet)
        transaction-ids (set (keys transaction-map))
        account-address (:address account)
        chain (ethereum/network->chain-keyword (get-in account [:networks network]))]
    (if (and (not= network-status :offline)
             (= app-state "active")
             (or (have-unconfirmed-transactions? (vals transaction-map))
                 (not-empty (set/difference chat-transaction-ids transaction-ids))))
      (when-not (= :custom chain)
        (let [all-tokens (tokens/tokens-for chain)
              token-addresses (map :address all-tokens)]
          (log/debug "Syncing transactions data..")
          (get-transactions
           {:account-address account-address
            :token-addresses token-addresses
            :chain           chain
            :web3            web3
            :success-fn      (fn [transactions]
                               (success-fn transactions account-address))
            :error-fn        error-fn}))))))

;; -----------------------------------------------------------------------------
;; helper functions to ensure transactions are owned by a certain address
;; -----------------------------------------------------------------------------

(letfn [(combine-entries [transaction token-transfer]
          (merge transaction (select-keys token-transfer [:symbol :from :to :value :type :token :transfer])))
        (update-confirmations [tx1 tx2]
          ;; TODO why is :confimations a string?
                              (assoc tx1 :confirmations (max (int (:confirmations tx1))
                                                             (int (:confirmations tx2)))))
        (tx-and-transfer? [tx1 tx2]
                          (and (not (:transfer tx1)) (:transfer tx2)))
        (both-transfer?
         [tx1 tx2]
         (and (:transfer tx1) (:transfer tx2)))]
  (defn- dedupe-transactions [tx1 tx2]
    (cond (tx-and-transfer? tx1 tx2) (combine-entries tx1 tx2)
          (tx-and-transfer? tx2 tx1) (combine-entries tx2 tx1)
          (both-transfer? tx1 tx2)   (update-confirmations tx1 tx2)
          :else tx2)))

(defn- own-transaction? [address [_ {:keys [type to from]}]]
  (let [normalized (ethereum/normalized-address address)]
    (or (and (= :inbound type) (= normalized (ethereum/normalized-address to)))
        (and (= :outbound type) (= normalized (ethereum/normalized-address from)))
        (and (= :failed type) (= normalized (ethereum/normalized-address from))))))

(defonce polling-executor (atom nil))

(defn sync-now! []
  (when @polling-executor
    (async-periodic-run! @polling-executor)))

(defn new-start-sync! []
  (when @polling-executor
    (async-periodic-stop! @polling-executor))
  (reset! polling-executor
          (async-periodic-exec
           (fn [done-fn]
             (new-sync
              (fn [] @re-frame.db/app-db)
              (fn [transactions account-address]
                (let [own-transactions
                      (into {} (filter #(own-transaction? account-address %) transactions))]
                  (swap! re-frame.db/app-db
                         update-in [:wallet :transactions]
                         #(merge-with dedupe-transactions % own-transactions))
                  (done-fn)))
              (fn [http-error]
                (log/debug "Unable to get transactions: " http-error)
                (done-fn))))
           sync-interval-ms))
  (sync-now!))

(re-frame/reg-fx ::sync-transactions-now #(sync-now!))
(re-frame/reg-fx ::start-sync-transactions #(new-start-sync!))
(fx/defn start-sync [cofx]
  {::start-sync-transactions nil})
