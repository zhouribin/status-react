(ns status-im.data-store.contacts
  (:require [goog.object :as object]
            [re-frame.core :as re-frame]
            [status-im.data-store.realm.core :as core]
            [clojure.set :as clojure.set]))

(defn- normalize-contact [contact]
  (-> contact
      (update :tags #(into #{} %))))

(re-frame/reg-cofx
 :data-store/get-all-contacts
 (fn [coeffects _]
   (assoc coeffects :all-contacts (map normalize-contact
                                       (-> @core/account-realm
                                           (core/get-all :contact)
                                           (core/all-clj :contact))))))

(defn save-contact-tx
  "Returns tx function for saving contact"
  [{:keys [public-key] :as contact}]
  (fn [realm]
    (core/create realm
                 :contact
                 contact
                 true)))

(defn save-contacts-tx
  "Returns tx function for saving contacts"
  [contacts]
  (fn [realm]
    (doseq [contact contacts]
      ((save-contact-tx contact) realm))))

(defn- get-contact-by-id [public-key realm]
  (core/single (core/get-by-field realm :contact :public-key public-key)))

(defn get-blocked-user-data
  [public-key]
  {:messages (core/all-clj (core/get-by-field @core/account-realm
                                              :message :from public-key)
                           :message)
   :chat (core/single-clj (core/get-by-field @core/account-realm
                                             :chat :chat-id public-key)
                          :chat)})

(re-frame/reg-cofx
 :data-store/get-blocked-user-data
 (fn [cofx _]
   (assoc cofx :get-blocked-user-data get-blocked-user-data)))

(defn block-user-tx
  "Returns tx function for deleting user messages"
  [{:keys [public-key] :as contact}]
  (fn [realm]
    (core/create realm :contact contact true)
    (let [chat (core/get-by-field realm :chat :chat-id public-key)
          messages (core/get-by-field realm :message :from public-key)
          statuses (core/get-by-field realm :user-status :public-key public-key)]
      (when chat
        (core/delete realm chat))
      (core/delete realm messages)
      (core/delete realm statuses))))

(defn delete-contact-tx
  "Returns tx function for deleting contact"
  [public-key]
  (fn [realm]
    (core/delete realm (get-contact-by-id public-key realm))))

(defn add-contact-tag-tx
  "Returns tx function for adding chat contacts"
  [public-key tag]
  (fn [realm]
    (let [contact       (get-contact-by-id public-key realm)
          existing-tags (object/get contact "tags")]
      (aset contact "tags"
            (clj->js (into #{} (concat tag
                                       (core/list->clj existing-tags))))))))

(defn remove-contact-tag-tx
  "Returns tx function for removing chat contacts"
  [public-key tag]
  (fn [realm]
    (let [contact       (get-contact-by-id public-key realm)
          existing-tags (object/get contact "tags")]
      (aset contact "tags"
            (clj->js (remove (into #{} tag)
                             (core/list->clj existing-tags)))))))
