(ns status-im.test.wallet.transactions
  (:require [cljs.test :refer-macros [deftest is testing]]
            [status-im.utils.datetime :as time]
            [status-im.models.transactions :as wallet.transactions]))

(deftest have-unconfirmed-transactions
  (is (wallet.transactions/have-unconfirmed-transactions?
       [{:confirmations "0"}]))
  (is (wallet.transactions/have-unconfirmed-transactions?
       [{:confirmations "11"}]))
  (is (wallet.transactions/have-unconfirmed-transactions?
       [{:confirmations "200"}
        {:confirmations "0"}]))
  (is (not (wallet.transactions/have-unconfirmed-transactions?
            [{:confirmations "12"}]))))

(deftest chat-map->transaction-ids
  (is (= #{} (wallet.transactions/chat-map->transaction-ids {})))
  (is (= #{"a" "b" "c" "d"}
         (wallet.transactions/chat-map->transaction-ids
          {:a {:messages {1 {:content-type "command"
                             :content {:params {:tx-hash "a"}}}}}
           :b {:messages {1 {:content-type "command"
                             :content {:params {:tx-hash "b"}}}}}
           :c {:messages {1 {:content-type "command"
                             :content {:params {:tx-hash "c"}}}
                          2 {:content-type "command"
                             :content {:params {:tx-hash "d"}}}}}})))

  (is (= #{"a" "b" "c" "d" "e"}
         (wallet.transactions/chat-map->transaction-ids
          {:aa {:messages {1 {:content-type "command"
                              :content {:params {:tx-hash "a"}}}}}
           :bb {:messages {1 {:content-type "command"
                              :content {:params {:tx-hash "b"}}}}}
           :cc {:messages {1 {:content-type "command"
                              :content {:params {:tx-hash "c"}}}
                           2 {:content-type "command"
                              :content {:params {:tx-hash "d"}}}
                           3 {:content-type "command"
                              :content {:params {:tx-hash "e"}}}}}})))
  (is (= #{"b"}
         (wallet.transactions/chat-map->transaction-ids
          {:aa {:public? true
                :messages {1 {:content-type "command"
                              :content {:params {:tx-hash "a"}}}}}
           :bb {:messages {1 {:content-type "command"
                              :content {:params {:tx-hash "b"}}}}}
           :cc {:messages {1 {:content {:params {:tx-hash "c"}}}
                           2 {:content-type "command"}}}}))))
