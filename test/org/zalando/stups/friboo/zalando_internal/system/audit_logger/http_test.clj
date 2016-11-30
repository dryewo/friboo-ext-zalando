(ns org.zalando.stups.friboo.zalando-internal.system.audit-logger.http-test
  (:require [midje.sweet :refer :all]
            [clojure.test :refer [deftest]]
            [clj-http.client :as http]
            [org.zalando.stups.friboo.zalando-internal.test-utils :refer :all]
            [org.zalando.stups.friboo.zalando-internal.system.oauth2 :as oauth2]
            [org.zalando.stups.friboo.zalando-internal.utils :as utils]
            [org.zalando.stups.friboo.zalando-internal.system.audit-logger :as logger]
            [org.zalando.stups.friboo.zalando-internal.system.audit-logger.http :as http-logger]))

(deftest test-http-logger

  (facts "when :api-url is not set, does nothing, but writes a log warning"
    (with-comp [logger-comp (http-logger/map->HTTP {:configuration {}})]
      (fact "does not make HTTP requests"
        (logger/log logger-comp {}) => anything
        (provided
          (clojure.tools.logging/log* anything :warn anything ":api-url is not set, not sending Audit Event: [\"{}\"]") => nil
          (oauth2/access-token anything anything) => anything :times 0
          (http/put anything) => anything :times 0))))

  (facts "when :api-url is set, works"
    (with-comp [logger-comp (http-logger/map->HTTP {:configuration {:api-url "http://foo.bar"}
                                                    :tokens        .tokens.})]
      (fact "log function calls clj-http with provided url"
        (deref (logger/log logger-comp {})) => nil
        (provided
          (utils/digest "{}") => "sha256"
          (oauth2/access-token :http-audit-logger .tokens.) => .token.
          (http/put "http://foo.bar/sha256" (contains {:body         "{}"
                                                       :oauth-token  .token.
                                                       :content-type :json})) => nil))
      (fact "log logs to stdout if http call fails"
        (deref (logger/log logger-comp {})) => nil
        (provided
          (utils/digest "{}") => "sha256"
          (oauth2/access-token :http-audit-logger .tokens.) => .token.
          ; this is what friboo.log/error ultimately expands to
          (clojure.tools.logging/log* irrelevant :error irrelevant "Could not write audit event: [\"{}\"]") => nil :times 1
          (http/put "http://foo.bar/sha256" anything) =throws=> (new Exception "400 Bad Request")))))

  )
