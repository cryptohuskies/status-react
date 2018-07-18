(ns status-im.test.browser.events
  (:require [cljs.test :refer-macros [deftest is testing]]
            [day8.re-frame.test :refer-macros [run-test-sync]]
            [status-im.ui.screens.events :as events]
            status-im.ui.screens.db
            status-im.ui.screens.subs
            [re-frame.core :as re-frame]
            [status-im.models.browser :as model]))

(defn test-fixtures []

  (re-frame/reg-fx ::events/init-store #())

  (re-frame/reg-fx :browse #())
  (re-frame/reg-fx :data-store/tx #())

  (re-frame/reg-cofx
   :data-store/all-browsers
   (fn [coeffects _]
     (assoc coeffects :all-stored-browsers [])))

  (re-frame/reg-cofx
   :data-store/all-dapp-permissions
   (fn [coeffects _]
     (assoc coeffects :all-dapp-permissions [])))

  (re-frame/reg-fx :send-to-bridge-fx #())

  (re-frame/reg-fx
   :show-dapp-permission-confirmation-fx
   (fn [[permission {:keys [dapp-name permissions-data index] :as params}]]
     (if (and (= dapp-name "test.com") (#{0 1} index))
       (re-frame/dispatch [:next-dapp-permission params permission permissions-data])
       (re-frame/dispatch [:next-dapp-permission params])))))

(deftest browser-events

  (run-test-sync

   (test-fixtures)

   (re-frame/dispatch [:initialize-db])
   (re-frame/dispatch [:initialize-browsers])

   (let [browsers  (re-frame/subscribe [:browsers])
         dapp1-url "test.com"
         dapp2-url "http://test2.com"]

     (testing "open and remove dapps"

       (is (zero? (count @browsers)))

       (re-frame/dispatch [:open-dapp-in-browser {:name        "Test Dapp"
                                                  :dapp-url    dapp1-url
                                                  :description "Test description"}])

       (is (= 1 (count @browsers)))

       (re-frame/dispatch [:open-url-in-browser dapp2-url])

       (is (= 2 (count @browsers)))

       (let [browser1 (first (vals @browsers))
             browser2 (second (vals @browsers))]
         (is (and (:dapp? browser1)
                  (not (:dapp? browser2))))
         (is (and (zero? (:history-index browser1))
                  (zero? (:history-index browser2))))
         (is (and (= [(str "http://" dapp1-url) (:history browser1)])
                  (= [dapp2-url] (:history browser2)))))

       (re-frame/dispatch [:remove-browser "Test Dapp"])

       (is (= 1 (count @browsers))))

     (testing "navigate dapp"

       (re-frame/dispatch [:open-browser (first (vals @browsers))])

       (let [browser    (re-frame/subscribe [:get-current-browser])
             dapp2-url2 (str dapp2-url "/nav2")
             dapp2-url3 (str dapp2-url "/nav3")]

         (is (zero? (:history-index @browser)))
         (is (= [dapp2-url] (:history @browser)))

         (is (and (not (model/can-go-back? @browser))
                  (not (model/can-go-forward? @browser))))

         (re-frame/dispatch [:browser-nav-back])
         (re-frame/dispatch [:browser-nav-forward])

         (re-frame/dispatch [:update-browser-on-nav-change @browser dapp2-url2 false])

         (is (= 1 (:history-index @browser)))
         (is (= [dapp2-url dapp2-url2] (:history @browser)))

         (is (and (model/can-go-back? @browser)
                  (not (model/can-go-forward? @browser))))

         (re-frame/dispatch [:browser-nav-back @browser])

         (is (zero? (:history-index @browser)))
         (is (= [dapp2-url dapp2-url2] (:history @browser)))

         (is (and (not (model/can-go-back? @browser))
                  (model/can-go-forward? @browser)))

         (re-frame/dispatch [:update-browser-on-nav-change @browser dapp2-url3 false])

         (is (= 1 (:history-index @browser)))
         (is (= [dapp2-url dapp2-url3] (:history @browser)))

         (re-frame/dispatch [:browser-nav-back @browser])

         (is (zero? (:history-index @browser)))
         (is (= [dapp2-url dapp2-url3] (:history @browser)))

         (re-frame/dispatch [:browser-nav-forward @browser])

         (is (= 1 (:history-index @browser)))
         (is (= [dapp2-url dapp2-url3] (:history @browser))))))

   (re-frame/dispatch [:initialize-dapp-permissions])

   (let [dapps-permissions (re-frame/subscribe [:get :dapps/permissions])
         dapp-name         "test.com"]

     (testing "dapps permissions"

       (is (zero? (count @dapps-permissions)))

       (re-frame/dispatch [:on-bridge-message {:type        "status-api-request"
                                               :host        dapp-name
                                               :permissions ["FAKE_PERMISSION"]}
                           nil nil])

       (is (= {:dapp        dapp-name
               :permissions []}
              (get @dapps-permissions dapp-name)))

       (re-frame/dispatch [:on-bridge-message {:type        "status-api-request"
                                               :host        dapp-name
                                               :permissions ["CONTACT_CODE"]}
                           nil nil])

       (is (= 1 (count @dapps-permissions)))

       (is (= {:dapp        dapp-name
               :permissions ["CONTACT_CODE"]}
              (get @dapps-permissions dapp-name)))

       (re-frame/dispatch [:on-bridge-message {:type        "status-api-request"
                                               :host        dapp-name
                                               :permissions ["CONTACT_CODE" "FAKE_PERMISSION"]}
                           nil nil])

       (is (= 1 (count @dapps-permissions)))

       (is (= {:dapp        dapp-name
               :permissions ["CONTACT_CODE"]}
              (get @dapps-permissions dapp-name)))

       (re-frame/dispatch [:on-bridge-message {:type        "status-api-request"
                                               :host        dapp-name
                                               :permissions ["FAKE_PERMISSION"]}
                           nil nil])

       (is (= 1 (count @dapps-permissions)))

       (is (= {:dapp        dapp-name
               :permissions ["CONTACT_CODE"]}
              (get @dapps-permissions dapp-name)))))))
