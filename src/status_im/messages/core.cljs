(ns status-im.messages.core
  (:require [status-im.accounts.db :as accounts.db]
            [status-im.utils.fx :as fx]
            [status-im.data-store.messages :as data-store]))

(fx/defn load-messages
  [{:keys [db smb-get-stored-messages]
    :as cofx}]
  (let [messages (smb-get-stored-messages)]
    {:db (assoc db
                :messages
                messages)}))

(fx/defn message-seen
  [{:keys [db] :as cofx} message-id]
  {:db (assoc-in db [:messages message-id :seen] true)})

(fx/defn change-current-message
  [{:keys [db] :as cofx} next-message]
  (let [{:keys [messages current-message-id]} db
        roots (keys (remove #((comp :parent val) %) messages))
        current-message-id (or current-message-id (first roots))
        {:keys [parent children] :as message} (get messages current-message-id)
        siblings  (or (:children (get messages parent))
                      roots)
        [previous-siblings [_ & next-siblings]] (split-with #(not= % current-message-id) (into #{} siblings))]
    (fx/merge cofx
              (case next-message
                :next (when (not-empty next-siblings)
                        {:db (assoc db :current-message-id (first next-siblings))})
                :previous (when (not-empty previous-siblings)
                            {:db (assoc db :current-message-id (last previous-siblings))})
                :parent (when parent
                          {:db (assoc db :current-message-id parent)})
                :child (when (not-empty children)
                         {:db (assoc db :current-message-id (first children))}))
              (load-messages))))
