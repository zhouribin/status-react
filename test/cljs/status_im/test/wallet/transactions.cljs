(ns status-im.test.wallet.transactions
  (:require [cljs.test :refer-macros [deftest is testing async run-tests]]
            [cljs.core.async.impl.protocols :refer [closed?]]
            [status-im.utils.datetime :as time]
            [status-im.models.transactions :as transactions]))

(deftest have-unconfirmed-transactions
  (is (transactions/have-unconfirmed-transactions?
       [{:confirmations "0"}]))
  (is (transactions/have-unconfirmed-transactions?
       [{:confirmations "11"}]))
  (is (transactions/have-unconfirmed-transactions?
       [{:confirmations "200"}
        {:confirmations "0"}]))
  (is (not (transactions/have-unconfirmed-transactions?
            [{:confirmations "12"}]))))

(deftest chat-map->transaction-ids
  (is (= #{} (transactions/chat-map->transaction-ids {})))
  (is (= #{"a" "b" "c" "d"}
         (transactions/chat-map->transaction-ids
          {:a {:messages {1 {:content-type "command"
                             :content {:params {:tx-hash "a"}}}}}
           :b {:messages {1 {:content-type "command"
                             :content {:params {:tx-hash "b"}}}}}
           :c {:messages {1 {:content-type "command"
                             :content {:params {:tx-hash "c"}}}
                          2 {:content-type "command"
                             :content {:params {:tx-hash "d"}}}}}})))

  (is (= #{"a" "b" "c" "d" "e"}
         (transactions/chat-map->transaction-ids
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
         (transactions/chat-map->transaction-ids
          {:aa {:public? true
                :messages {1 {:content-type "command"
                              :content {:params {:tx-hash "a"}}}}}
           :bb {:messages {1 {:content-type "command"
                              :content {:params {:tx-hash "b"}}}}}
           :cc {:messages {1 {:content {:params {:tx-hash "c"}}}
                           2 {:content-type "command"}}}}))))

(deftest async-periodic-exec
  (testing "work-fn is executed and can be stopeed"
    (let [executor (atom nil)
          state (atom 0)]
      (reset! executor
              (transactions/async-periodic-exec
               (fn [done-fn]
                 (swap! state inc)
                 (done-fn))
               100
               500))
      (async test-done
             (js/setTimeout
              (fn []
                (is (> 6 @state 2))
                (transactions/async-periodic-stop! @executor)
                (let [st @state]
                  (js/setTimeout
                   #(do
                      (is (= st @state))
                      (is (closed? @executor))
                      (test-done))
                   500)))
              500)))))

(deftest async-periodic-exec-error-in-job
  (testing "error thrown in job is caught and loop continues"
    (let [executor (atom nil)
          state (atom 0)]
      (reset! executor
              (transactions/async-periodic-exec
               (fn [done-fn]
                 (swap! state inc)
                 (throw (ex-info "Throwing this on purpose in test" {})))
               100
               500))
      (async test-done
             (js/setTimeout
              (fn []
                (is (> 6 @state 2))
                (transactions/async-periodic-stop! @executor)
                (let [st @state]
                  (js/setTimeout
                   #(do
                      (is (= st @state))
                      (is (closed? @executor))
                      (test-done))
                   500)))
              500)))))

(deftest async-periodic-exec-error-in-callback
  (testing "errors thrown in callback job doesn't stall process, because timeout is reached"
    (let [executor (atom nil)
          state (atom 0)]
      (reset! executor
              (transactions/async-periodic-exec
               (fn [done-fn]
                 (swap! state inc)
                 (js/setTimeout
                  #(throw (ex-info "Throwing this on purpose in test" {}))
                  1))
               100
               500))
      (async test-done
             (js/setTimeout
              (fn []
                (is (= 1 @state))
                (transactions/async-periodic-stop! @executor)
                (let [st @state]
                  (js/setTimeout
                   #(do
                      (is (= st @state))
                      (is (closed? @executor))
                      (test-done))
                   500)))
              500)))))

(deftest async-periodic-exec-job-takes-longer
  (testing "job takes longer than expected, executor timeout but task side-effects are still applied"
    (let [executor (atom nil)
          state (atom 0)]
      (reset! executor
              (transactions/async-periodic-exec
               (fn [done-fn] (js/setTimeout #(swap! state inc) 100))
               100
               1))
      (async test-done
             (js/setTimeout
              (fn []
                (transactions/async-periodic-stop! @executor)
                (js/setTimeout
                 ;; tolerance is tight
                 #(do (is (< 3 @state))
                      (test-done))
                 500))
              500)))))

(deftest async-periodic-exec-stop-early
  (testing "stopping early prevents any executions"
    (let [executor (atom nil)
          state (atom 0)]
      (reset! executor
              (transactions/async-periodic-exec
               (fn [done-fn]
                 (swap! state inc)
                 (done-fn))
               100
               100))
      (async test-done
             (js/setTimeout
              (fn []
                (is (zero? @state))
                (transactions/async-periodic-stop! @executor)
                (let [st @state]
                  (js/setTimeout
                   (fn []
                     (is (zero? @state))
                     (test-done))
                   500)))
              50)))))

#_(run-tests)

