#!/usr/bin/env sh
# Loads the seed data (100,000 patients, 400,000 visits) into the running compose database.
# The API must have started once first, because its Flyway migration creates the schema.
set -eu
cd "$(dirname "$0")/.."

echo "Waiting for the schema (start the API first: docker compose up -d)..."
i=0
until docker compose exec -T db psql -U carelink -d carelink -tAc "SELECT to_regclass('public.visit')" | grep -q visit; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    echo "Schema not found after 2 minutes. Is the api container running? Try: docker compose logs api" >&2
    exit 1
  fi
  sleep 2
done

docker compose exec -T db psql -v ON_ERROR_STOP=1 -U carelink -d carelink -f /seed/seed.sql
