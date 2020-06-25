ALTER TABLE "alarm-history" DROP COLUMN "duration-min";
ALTER TABLE "alarm-history" ADD COLUMN "income-msg" VARCHAR(1024) NULL;
