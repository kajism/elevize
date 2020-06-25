DROP TABLE IF EXISTS "stock";

CREATE TABLE "inventory-item" (
"id" SERIAL PRIMARY KEY,
"name" VARCHAR(256) NOT NULL,
"note" VARCHAR(256) NULL
);

CREATE TABLE "inventory-tx" (
"id" SERIAL PRIMARY KEY,
"created" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
"user-login" VARCHAR(128) NOT NULL,
"item-id" INTEGER NOT NULL,
"delta-pcs" SMALLINT NOT NULL,
"note" VARCHAR(256) NULL
);

ALTER TABLE "inventory-item" ADD CONSTRAINT "uk-inventory-item-name" UNIQUE ("name");
ALTER TABLE "inventory-tx" ADD CONSTRAINT "fk-inventory-tx2item" FOREIGN KEY ("item-id") REFERENCES "inventory-item" ( "id" );
