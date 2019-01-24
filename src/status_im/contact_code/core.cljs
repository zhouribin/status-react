(ns status-im.contact-code.core
  (:require
   [re-frame.core :as re-frame]
   [taoensso.timbre :as log]
   [clojure.string :as string]
   [status-im.native-module.core :as native-module]
   [status-im.utils.fx :as fx]))


(defn- parse-response [response-js]
  (-> response-js
      js/JSON.parse
      (js->clj :keywordize-keys true)))

(fx/defn load-fx [cofx chat-id]
  (when-not (get-in cofx [:db :contact-codes/contact-codes chat-id])
    {::load-contact-code chat-id}))

(defn handle-get-contact-code-response [chat-id raw-response]
  (let [{:keys [error code]} (parse-response raw-response)]
    (cond

     error
     (log/error "failed to load contact-code" chat-id error)

     (not (string/blank? code))
     (re-frame/dispatch [:contact-code.callback/contact-code-loaded chat-id code]))))


(re-frame/reg-fx
 ::load-contact-code
 (fn [chat-id]
   (native-module/get-contact-code
    (subs chat-id 2)
    (partial handle-get-contact-code-response chat-id))))


(fx/defn loaded [{:keys [db]} chat-id contact-code]
  {:db (assoc-in db [:contact-codes/contact-codes chat-id] contact-code)})
