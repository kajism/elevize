CREATE INDEX "plc-msg-history-created-idx" ON "plc-msg-history" ("created");
CREATE INDEX "alarm-history-timestamp-idx" ON "alarm-history" ("timestamp");

ALTER TABLE "plc-msg-history" ADD COLUMN "user-login" VARCHAR(128) NULL;
ALTER TABLE "plc-msg-history" ALTER COLUMN "user-id" DROP NOT NULL;
ALTER TABLE "alarm-history" ADD COLUMN "device-code" VARCHAR(32) NULL;
