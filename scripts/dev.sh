#!/usr/bin/env bash
# Brings up everything needed to work on Label Lens:
#
#   1. PostgreSQL in Docker (container labellens-postgres), migrated and seeded
#   2. the API, in this terminal until Ctrl-C
#   3. the debug app, built, installed on the connected phone or emulator, and opened
#
#   scripts/dev.sh              all three
#   scripts/dev.sh --web        database, API and the website at http://localhost:5173 (no app)
#   scripts/dev.sh --api-only   database and API, no app
#   scripts/dev.sh --app-only   just build, install and open the app
#
# PORT changes the API port (default 8787). The database keeps running afterwards;
# stop it with `docker compose down`.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
port="${PORT:-8787}"
package="app.labellens"
run_api=true
run_app=true
run_web=false

for arg in "$@"; do
  case "$arg" in
    --web) run_web=true; run_app=false ;;
    --api-only) run_app=false ;;
    --app-only) run_api=false ;;
    -h|--help) sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option: $arg. Try --help." >&2; exit 2 ;;
  esac
done

say() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
need() { command -v "$1" >/dev/null 2>&1 || { echo "$1 is not installed or not on PATH." >&2; exit 1; }; }
healthy() { curl -fs -m 2 "http://127.0.0.1:$port/health" >/dev/null 2>&1; }

api_pid=""
web_pid=""
stop_api() {
  if [[ -n "$web_pid" ]] && kill -0 "$web_pid" 2>/dev/null; then
    kill "$web_pid" 2>/dev/null || true
    wait "$web_pid" 2>/dev/null || true
  fi
  if [[ -n "$api_pid" ]] && kill -0 "$api_pid" 2>/dev/null; then
    echo
    echo "Stopping the API. PostgreSQL keeps running; \`docker compose down\` stops it."
    kill "$api_pid" 2>/dev/null || true
    wait "$api_pid" 2>/dev/null || true
  fi
}
trap stop_api EXIT
trap 'exit 130' INT TERM

# ---------- Database and API ----------

if $run_api; then
  need node
  need pnpm
  need curl
  need docker

  say "Starting PostgreSQL (labellens-postgres)"
  (cd "$root" && docker compose up -d --wait)

  [[ -f "$root/api/.env" ]] || cp "$root/api/.env.example" "$root/api/.env"
  if [[ ! -d "$root/api/node_modules" ]]; then
    say "Installing API dependencies"
    (cd "$root/api" && pnpm install)
  fi

  say "Updating the database"
  (cd "$root/api" && pnpm -s db:migrate && pnpm -s seed)

  if healthy; then
    say "API already running on port $port, using it"
  else
    say "Starting the API on port $port"
    (cd "$root/api" && exec env PORT="$port" LOG_REQUESTS=1 node --env-file-if-exists=.env src/server.js) &
    api_pid=$!
    for _ in $(seq 1 40); do
      healthy && break
      if ! kill -0 "$api_pid" 2>/dev/null; then
        echo "The API stopped while starting. See the message above." >&2
        api_pid=""
        exit 1
      fi
      sleep 0.5
    done
    healthy || { echo "The API did not answer on port $port within 20 seconds." >&2; exit 1; }
  fi
fi

# ---------- App ----------

if $run_app; then
  need adb
  devices="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
  count="$(printf '%s' "$devices" | grep -c . || true)"
  if [[ "$count" -eq 0 ]]; then
    echo
    echo "No phone or emulator connected (check \`adb devices\`; unlock the phone and allow USB debugging)." >&2
    [[ -n "$api_pid" ]] || exit 1
    echo "Skipping the app. The API stays up." >&2
  elif [[ "$count" -gt 1 && -z "${ANDROID_SERIAL:-}" ]]; then
    echo
    echo "More than one device is connected. Pick one with ANDROID_SERIAL=<serial>:" >&2
    echo "$devices" >&2
    [[ -n "$api_pid" ]] || exit 1
    echo "Skipping the app. The API stays up." >&2
  else
    # The app carries a copy of the additive data for when it has no connection.
    # Skipped without Node; the copy already in the repository is used then.
    if command -v node >/dev/null 2>&1; then
      say "Refreshing the additive data bundled in the app"
      (cd "$root/api" && node scripts/export-app-data.js)
    fi
    say "Building and installing the app"
    (cd "$root/android" && ./gradlew :app:installDebug --console=plain -q)
    # Makes 127.0.0.1:$port on the phone reach the API on this computer.
    adb reverse "tcp:$port" "tcp:$port" >/dev/null
    say "Opening Label Lens"
    adb shell am force-stop "$package"
    adb shell am start -n "$package/.MainActivity" >/dev/null
  fi
fi

# ---------- Website ----------

if $run_web; then
  if [[ ! -d "$root/www/node_modules" ]]; then
    say "Installing website dependencies"
    (cd "$root/www" && pnpm install)
  fi
  say "Starting the website on http://localhost:5173"
  (cd "$root/www" && exec env API_PROXY="http://127.0.0.1:$port" pnpm -s dev) &
  web_pid=$!
fi

# ---------- Stay with the API ----------

if [[ -n "$api_pid" ]]; then
  say "API running at http://localhost:$port. Requests are logged below. Ctrl-C to stop."
  wait "$api_pid"
elif [[ -n "$web_pid" ]]; then
  wait "$web_pid"
fi
