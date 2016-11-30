(ns org.zalando.stups.friboo.zalando-internal.system.audit-logger)

(defprotocol AuditLogger
  (log [_ event]))
