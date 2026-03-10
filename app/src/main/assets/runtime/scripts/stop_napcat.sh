#!/system/bin/sh

set -eu

APP_HOME="$1"
NATIVE_LIB_DIR="$2"
. "$(dirname "$0")/container_env.sh" "$APP_HOME" "$NATIVE_LIB_DIR"

if [ ! -f "$PID_FILE" ]; then
  echo "NapCat is not running"
  exit 0
fi

PID="$(cat "$PID_FILE" 2>/dev/null || true)"
if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
  kill "$PID"
  rm -f "$PID_FILE"
  echo "NapCat stopped"
  exit 0
fi

rm -f "$PID_FILE"
echo "NapCat pid file removed"
