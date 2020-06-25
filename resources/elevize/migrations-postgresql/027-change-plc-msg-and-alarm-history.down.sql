DROP INDEX "plc-msg-history-created-idx";
DROP INDEX "alarm-history-timestamp-idx";

ALTER TABLE "plc-msg-history" DROP COLUMN "user-login";
ALTER TABLE "plc-msg-history" ALTER COLUMN "user-id" SET NOT NULL;
ALTER TABLE "alarm-history" DROP COLUMN "device-code";
