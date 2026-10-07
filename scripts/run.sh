#!/usr/bin/env sh
# Runs the whole of CareLink Follow-Up with one command: database, Redis, API, seed data and the
# screen. Opens the screen in a browser as soon as the API reports ready.
#
# Prerequisite: Docker is running. Node 20+ is needed for the screen; the API needs no local JDK,
# because it is built inside its container.
#
#   ./scripts/run.sh              start everything and open the screen
#   ./scripts/run.sh --reseed     reload the 100,000-patient seed first
#   ./scripts/run.sh --api-only   no screen, no browser (handy for curl or the Swagger UI)
#   ./scripts/run.sh --no-open    start everything, but do not open a browser
#   ./scripts/run.sh --stop       stop everything this script started
#
# Ports can be moved if something already holds one, for example:
#   API_PORT=8081 WEB_PORT=5174 ./scripts/run.sh
set -eu

API_PORT=${API_PORT:-8080}
WEB_PORT=${WEB_PORT:-5173}
DB_PORT=${DB_PORT:-5433}
REDIS_PORT=${REDIS_PORT:-6380}
export API_PORT WEB_PORT DB_PORT REDIS_PORT

OPEN_BROWSER=1
RESEED=0
API_ONLY=0
STOP_ONLY=0

for arg in "$@"; do
  case "$arg" in
    --reseed)   RESEED=1 ;;
    --no-open)  OPEN_BROWSER=0 ;;
    --api-only) API_ONLY=1; OPEN_BROWSER=0 ;;
    --stop)     STOP_ONLY=1 ;;
    -h|--help)  awk 'NR>1 && /^#/ {sub(/^# ?/,""); print; next} NR>1 {exit}' "$0"; exit 0 ;;
    *) echo "Unknown option: $arg (try --help)" >&2; exit 2 ;;
  esac
done

cd "$(dirname "$0")/.."

say()  { printf '\n\033[1m==> %s\033[0m\n' "$1"; }
info() { printf '    %s\n' "$1"; }
die()  { printf '\n\033[31mError: %s\033[0m\n' "$1" >&2; exit 1; }

# ---------------------------------------------------------------- stop and exit

if [ "$STOP_ONLY" = 1 ]; then
  say "Stopping"
  docker compose stop
  info "Stopped. Data is kept; './scripts/run.sh' brings it back."
  info "To delete the database as well: docker compose down"
  exit 0
fi

WEB_PID=""
cleanup() {
  trap - INT TERM EXIT
  if [ -n "$WEB_PID" ]; then
    kill "$WEB_PID" 2>/dev/null || true
    i=0
    while kill -0 "$WEB_PID" 2>/dev/null && [ "$i" -lt 20 ]; do sleep 0.5; i=$((i + 1)); done
    kill -9 "$WEB_PID" 2>/dev/null || true
  fi
  say "Stopping containers"
  docker compose stop >/dev/null 2>&1 || true
  info "Stopped. Data is kept, so the next run skips seeding and starts in seconds."
  exit 0
}

# ---------------------------------------------------------------- preflight

say "Checking prerequisites"

command -v docker >/dev/null 2>&1 || die "Docker is not installed, or not on PATH."
docker info >/dev/null 2>&1 || die "Docker is installed but not running. Start Docker and try again."
docker compose version >/dev/null 2>&1 || die "The 'docker compose' plugin is missing. Install Compose v2."
info "Docker is running"

if [ "$API_ONLY" = 0 ]; then
  command -v node >/dev/null 2>&1 || die "Node is needed for the screen. Install Node 20+, or use --api-only."
  info "Node $(node --version)"
fi

# A port held by something else is the most common reason this fails, and the error Docker gives for
# it is not obvious, so check first and say exactly which port and what to do about it.
port_in_use() {
  if command -v ss >/dev/null 2>&1; then
    ss -ltn 2>/dev/null | awk '{print $4}' | grep -qE "[:.]$1\$"
  elif command -v lsof >/dev/null 2>&1; then
    lsof -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
  else
    return 1  # cannot tell; let Docker report it
  fi
}

# $1 port, $2 description, $3 env var to override, $4 our compose service (empty if none).
# A port our own container already holds is not a clash: compose reuses that container.
check_port() {
  port_in_use "$1" || return 0
  if [ -n "$4" ]; then
    id=$(docker compose ps -q "$4" 2>/dev/null || true)
    if [ -n "$id" ] && [ "$(docker inspect -f '{{.State.Running}}' "$id" 2>/dev/null)" = "true" ]; then
      return 0
    fi
  fi
  die "Port $1 ($2) is already in use by something else.
       Free it, or choose another port:  $3=$(( $1 + 1 )) ./scripts/run.sh"
}

check_port "$API_PORT" "the API" API_PORT api
check_port "$DB_PORT" "PostgreSQL" DB_PORT db
check_port "$REDIS_PORT" "Redis" REDIS_PORT redis
[ "$API_ONLY" = 0 ] && check_port "$WEB_PORT" "the screen" WEB_PORT ""
info "Ports checked: $API_PORT (API), $DB_PORT (db), $REDIS_PORT (redis)"

trap cleanup INT TERM

# ---------------------------------------------------------------- containers

say "Starting PostgreSQL, Redis and the API"
if ! docker image inspect carelink-followup-api >/dev/null 2>&1; then
  info "Building the API image. The first time takes a few minutes."
fi
docker compose up -d --build

# ---------------------------------------------------------------- wait for the API

say "Waiting for the API"
ready=0
for _ in $(seq 1 180); do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${API_PORT}/actuator/health/readiness" 2>/dev/null)" = "200" ]; then
    ready=1
    break
  fi
  if [ -z "$(docker compose ps -q api)" ] || [ "$(docker inspect -f '{{.State.Running}}' "$(docker compose ps -q api)" 2>/dev/null)" != "true" ]; then
    echo
    docker compose logs --no-log-prefix --tail=30 api || true
    die "The API container stopped. Its last log lines are above."
  fi
  sleep 1
done
[ "$ready" = 1 ] || die "The API did not become ready within 3 minutes. Try: docker compose logs api"
info "API ready on http://localhost:${API_PORT}"

# ---------------------------------------------------------------- seed

patients=$(docker compose exec -T db psql -U carelink -d carelink -tAc \
  'SELECT count(*) FROM patient' 2>/dev/null | tr -d '[:space:]' || true)
case "$patients" in ''|*[!0-9]*) patients=0 ;; esac

if [ "$RESEED" = 1 ] || [ "$patients" -eq 0 ]; then
  say "Loading seed data"
  info "100,000 patients and 400,000 visits. All invented. Takes about 15 seconds."
  docker compose exec -T db psql -q -v ON_ERROR_STOP=1 -U carelink -d carelink -f /seed/seed.sql >/dev/null
  info "Seeded"
else
  say "Seed data already present"
  info "$patients patients. Use --reseed to reload."
fi

# ---------------------------------------------------------------- API only

if [ "$API_ONLY" = 1 ]; then
  say "Ready"
  info "API         http://localhost:${API_PORT}/api/follow-up?facility_id=FAC-0101"
  info "Swagger UI  http://localhost:${API_PORT}/swagger-ui.html"
  info "Try it:     curl -s -H 'Authorization: Bearer dev-clinician-0101' \\"
  info "              'http://localhost:${API_PORT}/api/follow-up?facility_id=FAC-0101&page_size=5'"
  info "Stop with:  ./scripts/run.sh --stop"
  trap - INT TERM
  exit 0
fi

# ---------------------------------------------------------------- the screen

if [ ! -d web/node_modules ]; then
  say "Installing front-end dependencies"
  (cd web && npm ci)
fi

say "Starting the screen"
# The dev server proxies /api to the API, so the browser makes same-origin calls exactly as it will
# in production, where the facility node serves both the UI and the API.
# Exec Vite's own binary rather than going through `npm run dev`. npm would remain as a parent
# process, and killing npm leaves Vite orphaned, still holding the port. With exec, the PID recorded
# here is Vite itself, so stopping this script really does stop the server.
API_ORIGIN="http://localhost:${API_PORT}" WEB_PORT="$WEB_PORT" \
  sh -c 'cd web && exec ./node_modules/.bin/vite --port "$WEB_PORT" --strictPort' &
WEB_PID=$!

for _ in $(seq 1 60); do
  port_in_use "$WEB_PORT" && break
  kill -0 "$WEB_PID" 2>/dev/null || die "The dev server stopped before it started listening."
  sleep 1
done
port_in_use "$WEB_PORT" || die "The dev server did not start within 60 seconds."

URL="http://localhost:${WEB_PORT}"
if [ "$OPEN_BROWSER" = 1 ]; then
  opener=""
  for candidate in xdg-open open wslview; do
    command -v "$candidate" >/dev/null 2>&1 && { opener="$candidate"; break; }
  done
  if [ -n "$opener" ]; then
    "$opener" "$URL" >/dev/null 2>&1 || true
  else
    info "No browser opener found; open $URL yourself."
  fi
fi

say "Running"
info "Screen      $URL"
info "API         http://localhost:${API_PORT}"
info "Swagger UI  http://localhost:${API_PORT}/swagger-ui.html"
info "Sign in     use the 'Signed in as (development)' menu to switch role"
printf '\n    Press Ctrl+C to stop.\n\n'

wait "$WEB_PID" 2>/dev/null || true
cleanup
