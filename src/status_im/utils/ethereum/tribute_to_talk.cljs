(ns status-im.utils.ethereum.tribute-to-talk
  (:require [status-im.utils.ethereum.core :as ethereum]
            [status-im.utils.web3-provider :as web3-provider]
            [status-im.utils.money :as money])
  (:require-macros [status-im.utils.slurp :refer [slurp]]))

(def web3 (:web3 @re-frame.db/app-db))
(def contract-address "0x13e6db69307f408bbdcd2871f9297cca9e78b549")
(def my-address (:public-key (:account/account @re-frame.db/app-db)))
(def ttt "0xca9e734f6f3f78efd1b87671bcdc7f17a6e2b185")

(defn set-required-tribute [web3 contract address amount permanent? cb]
  (ethereum/send-transaction web3
                             (ethereum/call-params contract
                                                   "setRequiredTribute(address,uint256,bool)"
                                                   ;; applies to everyone
                                                   (ethereum/normalized-address "0x0000000000000000000000000000000000000000")
                                                   (ethereum/int->hex amount)
                                                   (ethereum/boolean->hex permanent?))
                             (fn [_ transaction-id] (cb transaction-id))))

(defn get-required-fee [web3 contract address cb]
  (ethereum/call web3
                 (ethereum/call-params contract
                                       "getRequiredFee(address)"
                                       (ethereum/normalized-address address))
                 (fn [_ fee] (cb (ethereum/hex->bignumber fee)))))

(defn grantAudience [web3 contract approve? waive? secret time-limit requester-signature grantor-signature cb]
  (ethereum/call web3
                 (ethereum/call-params contract
                                       "grantAudience(bool,bool,bytes32,uint256,bytes,bytes)"
                                       (ethereum/boolean->hex approve?)
                                       (ethereum/boolean->hex waive?)
                                       secret
                                       (ethereum/int->hex time-limit)
                                       requester-signature
                                       grantor-signature)
                 cb))

(defn get-request-audience-hash [web3 contract grantor-address hashed-secret time-limit cb]
  (ethereum/call web3
                 (ethereum/call-params contract
                                       "getRequestAudienceHash(address,bytes32,uint256)"
                                       (ethereum/normalized-address grantor-address)
                                       hashed-secret
                                       (ethereum/int->hex time-limit))
                 cb))

(defn get-grant-audience-hash [web3 contract requester-signature-hash approve? waive? secret cb]
  (ethereum/call web3
                 (ethereum/call-params contract
                                       "getGrantAudienceHash(bytes32,bool,bool,bytes32)"
                                       requester-signature-hash
                                       (ethereum/boolean->hex approve?)
                                       (ethereum/boolean->hex waive?)
                                       secret)
                 cb))

(defn fee-catalog [web3 contract from-address to-address cb]
  (ethereum/call web3
                 (ethereum/call-params contract
                                       "feeCatalog(address,address)"
                                       (ethereum/normalized-address from-address)
                                       (ethereum/normalized-address to-address))
                 cb))

#_(set-required-tribute web3 contract-address my-address 200 true println)
#_(get-required-fee web3 contract-address ttt println)
