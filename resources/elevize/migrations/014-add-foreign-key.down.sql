ALTER TABLE "variable" DROP CONSTRAINT IF EXISTS "fk-variable2device";
ALTER TABLE "variable" DROP CONSTRAINT IF EXISTS "variable-unique-name-device";
ALTER TABLE "variable" DROP CONSTRAINT IF EXISTS "variable-unique-set-name-device";
ALTER TABLE "status-history" DROP CONSTRAINT IF EXISTS "fk-status-history2variable";
ALTER TABLE "alarm-history" DROP CONSTRAINT IF EXISTS "CONSTRAINT_6C";
ALTER TABLE "alarm-history" DROP CONSTRAINT IF EXISTS "fk-alarm-history2alarm-info";
ALTER TABLE "alarm-info" DROP CONSTRAINT IF EXISTS "fk-alarm-info2alarm";
ALTER TABLE "device" DROP CONSTRAINT IF EXISTS "fk-device2subsystem";
