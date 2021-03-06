DROP TABLE IF EXISTS "alarm";
DROP TABLE IF EXISTS "alarm-info";
DROP TABLE IF EXISTS "log";
DROP TABLE IF EXISTS "log-type";

ALTER TABLE "subsystem" DROP COLUMN IF EXISTS "created";
ALTER TABLE "subsystem" DROP COLUMN IF EXISTS "modified";
ALTER TABLE "device" DROP COLUMN IF EXISTS "created";
ALTER TABLE "device" DROP COLUMN IF EXISTS "modified";
ALTER TABLE "variable" DROP COLUMN IF EXISTS "created";
ALTER TABLE "variable" DROP COLUMN IF EXISTS "modified";
ALTER TABLE "status-history" DROP COLUMN IF EXISTS "created";
ALTER TABLE "user" DROP COLUMN IF EXISTS "created";
ALTER TABLE "user" DROP COLUMN IF EXISTS "modified";
ALTER TABLE "alarm-history" DROP COLUMN IF EXISTS "created";
ALTER TABLE "alarm-history" DROP COLUMN IF EXISTS "modified";
ALTER TABLE "var-group" DROP COLUMN IF EXISTS "created";
ALTER TABLE "var-group" DROP COLUMN IF EXISTS "modified";
ALTER TABLE "var-group-member" DROP COLUMN IF EXISTS "created";
ALTER TABLE "var-group-member" DROP COLUMN IF EXISTS "modified";
ALTER TABLE "plc-msg-history" DROP COLUMN IF EXISTS "modified";
ALTER TABLE "enum-item" DROP COLUMN IF EXISTS "created";
ALTER TABLE "enum-item" DROP COLUMN IF EXISTS "modified";
