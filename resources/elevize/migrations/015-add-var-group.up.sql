CREATE TABLE "var-group" (
"id" BIGINT auto_increment PRIMARY KEY,
"title" VARCHAR(64) NOT NULL,
"created" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
"modified" TIMESTAMP NOT NULL AS CURRENT_TIMESTAMP());

CREATE TABLE "var-group-member" (
"id" BIGINT auto_increment PRIMARY KEY,
"var-group-id" bigint NOT NULL,
"variable-id" bigint NOT NULL,
"pos" SMALLINT NOT NULL,
"created" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
"modified" TIMESTAMP NOT NULL AS CURRENT_TIMESTAMP());

ALTER TABLE "var-group-member" ADD FOREIGN KEY ("variable-id") REFERENCES "variable" ("id") ON DELETE CASCADE;
ALTER TABLE "var-group-member" ADD FOREIGN KEY ("var-group-id") REFERENCES "var-group" ("id") ON DELETE CASCADE;
