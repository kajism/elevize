CREATE TABLE "subsystem" (
"id" SERIAL PRIMARY KEY,
"code" VARCHAR(32) NOT NULL,
"title" VARCHAR(128) NOT NULL);

CREATE TABLE "device" (
"id" SERIAL PRIMARY KEY,
"code" VARCHAR(32) NOT NULL,
"title" VARCHAR(128) NOT NULL,
"subsystem-id" BIGINT NOT NULL, /* changed to INTEGER in 026 */
"device-num" SMALLINT NOT NULL,
"var-header" VARCHAR(5000) NULL);

CREATE TABLE "variable" (
"id" SERIAL PRIMARY KEY,
"device-id" BIGINT NOT NULL,  /* changed to INTEGER in 026 */
"name" VARCHAR(64) NOT NULL,
"set-name" VARCHAR(64),
"kks" VARCHAR(30),
"data-type" VARCHAR(30) NOT NULL,
"comment" VARCHAR(256) NULL);

CREATE TABLE "user"
("id" SERIAL PRIMARY KEY,
"title" VARCHAR(30) NOT NULL,
"login" VARCHAR(128) NOT NULL,
"passwd" VARCHAR(128),
"email" VARCHAR(128),
"roles" VARCHAR(128));

ALTER TABLE "variable" ADD CONSTRAINT "fk-variable2device" FOREIGN KEY ("device-id") REFERENCES "device" ( "id" );
ALTER TABLE "variable" ADD CONSTRAINT "uk-variable-unique-name-device" UNIQUE ("name", "device-id");
ALTER TABLE "variable" ADD CONSTRAINT "uk-variable-unique-set-name-device" UNIQUE ("set-name", "device-id");
ALTER TABLE "device" ADD CONSTRAINT "fk-device2subsystem" FOREIGN KEY ("subsystem-id") REFERENCES "subsystem" ( "id" );

CREATE TABLE "var-group" (
"id" SERIAL PRIMARY KEY,
"title" VARCHAR(64) NOT NULL);

CREATE TABLE "var-group-member" (
"id" SERIAL PRIMARY KEY,
"var-group-id" BIGINT NOT NULL, /* changed to INTEGER in 026 */
"variable-id" BIGINT NOT NULL, /* changed to INTEGER in 026 */
"pos" SMALLINT NOT NULL);

ALTER TABLE "var-group-member" ADD FOREIGN KEY ("variable-id") REFERENCES "variable" ("id") ON DELETE CASCADE;
ALTER TABLE "var-group-member" ADD FOREIGN KEY ("var-group-id") REFERENCES "var-group" ("id") ON DELETE CASCADE;

CREATE TABLE "enum-item" (
"id" SERIAL PRIMARY KEY,
"group-name" VARCHAR(64) NOT NULL,
"name" VARCHAR(64),
"order-pos" SMALLINT,
"label" VARCHAR(64));
