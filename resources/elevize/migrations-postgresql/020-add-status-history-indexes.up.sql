CREATE INDEX "status-history-timestamp-idx" ON "status-history" ("timestamp");
CREATE INDEX "status-history-var-id-timestamp-idx" ON "status-history" ("variable-id", "timestamp");
