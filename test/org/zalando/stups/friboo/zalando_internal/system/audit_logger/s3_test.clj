(ns org.zalando.stups.friboo.zalando-internal.system.audit-logger.s3-test
  (:require [midje.sweet :refer :all]
            [clojure.test :refer [deftest]]
            [amazonica.aws.s3 :as s3]
            [clj-time.format :as tf]
            [org.zalando.stups.friboo.zalando-internal.test-utils :refer :all]
            [org.zalando.stups.friboo.zalando-internal.utils :as utils]
            [org.zalando.stups.friboo.zalando-internal.system.audit-logger :as logger]
            [org.zalando.stups.friboo.zalando-internal.system.audit-logger.s3 :as s3-logger]))

(deftest test-s3-logger

  (facts "when :s3-bucket is not set, does nothing, but writes a log warning"
    (with-comp [logger-comp (s3-logger/map->S3 {:configuration {}})]
      (fact "does not make S3 API calls"
        (deref (logger/log logger-comp {})) => anything
        (provided
          (clojure.tools.logging/log* anything :warn anything
                                      ":s3-bucket is not set, not sending Audit Event: [\"{}\"]") => nil
          (s3/put-object anything) => anything :times 0))))

  (facts "when :s3-bucket is set, works"
    (with-comp [logger-comp (s3-logger/map->S3 {:configuration {:s3-bucket "foo-bar"}})]
      (fact "log function calls S3 API"
        (deref (logger/log logger-comp {})) => .result.
        (provided
          (tf/unparse anything anything) => "/path/to/file/"
          (utils/digest "{}") => "sha256"
          (s3/put-object (just {:bucket-name  "foo-bar"
                                :key          "/path/to/file/sha256"
                                :metadata     {:content-length 2
                                               :content-type   "application/json"}
                                :input-stream anything})) => .result.))
      (fact "log logs to stdout if S3 API call fails"
        (deref (logger/log logger-comp {})) => nil
        (provided
          (utils/digest "{}") => "sha256"
          ; this is what friboo.log/error ultimately expands to
          (clojure.tools.logging/log* irrelevant :error irrelevant "Could not write audit event: [\"{}\"]") => nil :times 1
          (s3/put-object anything) =throws=> (new Exception "400 Bad Request")))))

  )
