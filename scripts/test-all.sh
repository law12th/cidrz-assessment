#!/usr/bin/env sh
# Runs every automated test: the API (unit tests, plus integration tests against a real PostgreSQL
# and a real Redis started by Testcontainers) and the front end.
# Needs Docker running and Node 20+. The Gradle wrapper provisions the JDK it needs.
set -eu
cd "$(dirname "$0")/.."
echo "== API tests =="
(cd backend && ./gradlew --no-daemon test)
echo "== Front-end tests =="
(cd web && npm ci --silent && npm test)
