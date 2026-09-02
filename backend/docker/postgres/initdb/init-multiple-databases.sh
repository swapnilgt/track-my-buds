#!/bin/bash
# Creates one database and a matching owner role per service, enabling PostGIS
# on each. Driven by the POSTGRES_MULTIPLE_DATABASES env var (comma-separated).
# Runs once, on first container start, from /docker-entrypoint-initdb.d.
set -euo pipefail

create_service_database() {
  local db="$1"
  echo "  creating database '$db' with owner role '$db'"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
    CREATE ROLE "$db" WITH LOGIN PASSWORD '${APP_DB_PASSWORD}';
    CREATE DATABASE "$db" OWNER "$db";
    GRANT ALL PRIVILEGES ON DATABASE "$db" TO "$db";
EOSQL
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$db" \
    -c 'CREATE EXTENSION IF NOT EXISTS postgis;'
}

if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
  for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
    create_service_database "$db"
  done
  echo "multiple database creation complete"
fi
