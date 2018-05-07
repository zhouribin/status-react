(ns status-im.utils.ethereum.tribute-to-talk
  (:require [status-im.utils.ethereum.core :as ethereum]
            [status-im.utils.web3-provider :as web3-provider]
            [status-im.utils.money :as money])
  (:require-macros [status-im.utils.slurp :refer [slurp]]))

(def ttt-abi (.-abi (.parse js/JSON (slurp "./src/status_im/utils/ethereum/tribute_to_talk.json"))))

(def ttt-contract-address "0x13e6db69307f408bbdcd2871f9297cca9e78b549")

(def my-address "0x1b43868a6bce7779be4c75fe6d4d80ddb10a7fd0")

(def ttt "0xca9e734f6f3f78efd1b87671bcdc7f17a6e2b185")

(def ttt-contract (.at (.contract (.-eth (:web3 @re-frame.db/app-db))
                                  ttt-abi)
                       ttt-contract-address))

(defn set-required-tribute [amount permanent? cb]
  (.setRequiredTribute ttt-contract "0x0000000000000000000000000000000000000000" amount permanent?
                       (fn [_ transaction-id] (cb transaction-id))))

(defn get-required-fee [contact-address cb]
  (.getRequiredFee ttt-contract contact-address
                   (fn [_ fee] (cb fee))))

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

#_(set-required-tribute 200 true println)

#_(get-required-fee ttt println)
