CREATE TABLE "runtime-diary" (
"id" SERIAL PRIMARY KEY,
"created" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
"device-code" VARCHAR(32) NOT NULL,
"daily-rec?" BOOLEAN NOT NULL,
"from" TIMESTAMP NOT NULL,
"to" TIMESTAMP NOT NULL,
"runtime-mins" INTEGER NOT NULL,
"T-max" SMALLINT NULL,
"T-avg" SMALLINT NULL,
"fuel-kg" INTEGER NULL,
"T-avg-exh" SMALLINT NULL,
"turbine-runtime-mins" INTEGER NULL
);
