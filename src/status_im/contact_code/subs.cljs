(ns status-im.contact-code.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 :contact-codes/contact-codes
 (fn [db]
   (:contact-codes/contact-codes db)))

(re-frame/reg-sub
 :contact-codes/contact-code
 :<- [:contact-codes/contact-codes]
 (fn [contact-codes [_ public-key]]
   (get contact-codes public-key)))
