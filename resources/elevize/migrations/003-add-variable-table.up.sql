CREATE TABLE "variable" (
"id" BIGINT auto_increment PRIMARY KEY,
"device-id" BIGINT NOT NULL,
"name" VARCHAR(64) NOT NULL,
"set-name" VARCHAR(64),
"kks" CHAR(16),
"data-type" VARCHAR(30) NOT NULL,
"comment" VARCHAR(256) NULL,
"created" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
"modified" TIMESTAMP NOT NULL AS CURRENT_TIMESTAMP());
