ALTER TABLE "device" ALTER COLUMN "subsystem-id" TYPE INTEGER;
ALTER TABLE "variable" ALTER COLUMN "device-id" TYPE INTEGER;
ALTER TABLE "var-group-member" ALTER COLUMN "var-group-id" TYPE INTEGER;
ALTER TABLE "var-group-member" ALTER COLUMN "variable-id" TYPE INTEGER;
