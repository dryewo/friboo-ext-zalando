(ns org.zalando.stups.friboo.zalando-internal.system.audit-logger.s3
  (:require [amazonica.aws.s3 :as s3]
            [com.stuartsierra.component :as component]
            [cheshire.core :as json]
            [clj-time.core :as time]
            [clj-time.format :as time-format]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.zalando-internal.utils :as utils]
            [org.zalando.stups.friboo.zalando-internal.system.audit-logger :refer [AuditLogger]]
            [clojure.string :as str])
  (:import (java.io ByteArrayInputStream)))

(defn log-impl [{:keys [configuration]} event]
  (let [s3-bucket (:s3-bucket configuration)
        body   (json/encode event)]
    (if (str/blank? s3-bucket)
      (log/warn ":s3-bucket is not set, not sending Audit Event: %s" body)
      (let [id            (utils/digest body)
            format-string (or (:s3-bucket-key configuration) "yyyy/MM/dd/")
            formatter     (time-format/formatter format-string time/utc)
            key           (time-format/unparse formatter (time/now))
            input-stream  (new ByteArrayInputStream (.getBytes body "UTF-8"))]
        (future
          (try
            (s3/put-object {:bucket-name  s3-bucket
                            :key          (utils/conpath key id)
                            :metadata     {:content-length (count body)
                                           :content-type   "application/json"}
                            :input-stream input-stream})
            (log/info "Wrote audit event with id %s" id)
            (catch Exception e
              (log/error e "Could not write audit event: %s" body))))))))

(defn start-component [{:as this :keys [configuration]}]
  (log/info "Starting S3 audit logger")
  (when-not (:s3-bucket configuration)
    (log/warn ":s3-bucket is not set, will not send Audit Events."))
  this)

(defrecord S3 [;; Initial params - set in the constructor
               configuration]
  component/Lifecycle
  (start [this]
    (start-component this))
  (stop [this]
    (log/info "Shutting down S3 audit logger")
    this)

  AuditLogger
  (log [this event]
    (log-impl this event)))
