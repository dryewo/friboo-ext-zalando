(ns org.zalando.stups.friboo.zalando-internal.system.audit-logger.http
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.zalando-internal.utils :as utils]
            [org.zalando.stups.friboo.zalando-internal.system.oauth2 :as oauth2]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.zalando-internal.system.audit-logger :refer [AuditLogger]]
            [clojure.string :as str]))

(defn log-impl [{:keys [configuration tokens]} event]
  (let [api-url (:api-url configuration)
        body    (json/encode event)]
    (if (str/blank? api-url)
      (do (log/warn ":api-url is not set, not sending Audit Event: %s" body)
          (delay))
      (let [token-name   (or (:token-name configuration) :http-audit-logger)
            id           (utils/digest body)
            url          (utils/conpath api-url id)
            access-token (oauth2/access-token token-name tokens)]
        (future
          (try
            (let [result (http/put url {:body         body
                                        :oauth-token  access-token
                                        :content-type :json})]
              (log/info "Wrote audit event with id %s" id)
              result)
            (catch Exception e
              ; log to console as fallback
              (log/error e "Could not write audit event: %s" body))))))))

(defn start-component [{:as this :keys [configuration]}]
  (log/info "Starting HTTP audit logger")
  (when-not (:api-url configuration)
    (log/warn ":api-url is not set, will not send Audit Events."))
  this)

(defrecord HTTP [;; Initial params - set in the constructor
                 configuration
                 ;; Dependencies - injected by the Component framework
                 tokens]
  component/Lifecycle
  (start [this]
    (start-component this))
  (stop [this]
    (log/info "Shutting down HTTP audit logger")
    this)

  AuditLogger
  (log [this event]
    (log-impl this event)))
