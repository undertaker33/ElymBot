#!/system/bin/sh

set -eu

APP_HOME="$1"
NATIVE_LIB_DIR="$2"
. "$(dirname "$0")/container_env.sh" "$APP_HOME" "$NATIVE_LIB_DIR"

if [ ! -f "$PID_FILE" ]; then
  echo "STOPPED"
  exit 1
fi

PID="$(cat "$PID_FILE" 2>/dev/null || true)"
if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
  echo "RUNNING:$PID"
  exit 0
fi

rm -f "$PID_FILE"
echo "STOPPED"
exit 1
