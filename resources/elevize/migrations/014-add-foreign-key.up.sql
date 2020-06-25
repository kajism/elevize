ALTER TABLE "variable" ADD CONSTRAINT IF NOT EXISTS "fk-variable2device" FOREIGN KEY ("device-id") REFERENCES "device" ( "id" );
ALTER TABLE "variable" ADD CONSTRAINT "variable-unique-name-device" UNIQUE ("name", "device-id");
ALTER TABLE "variable" ADD CONSTRAINT "variable-unique-set-name-device" UNIQUE ("set-name", "device-id");
ALTER TABLE "device" ADD CONSTRAINT IF NOT EXISTS "fk-device2subsystem" FOREIGN KEY ("subsystem-id") REFERENCES "subsystem" ( "id" );
