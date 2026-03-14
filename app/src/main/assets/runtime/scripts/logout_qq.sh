#!/system/bin/sh

set -eu

APP_HOME="$1"
NATIVE_LIB_DIR="$2"
. "$(dirname "$0")/container_env.sh" "$APP_HOME" "$NATIVE_LIB_DIR"

log() {
  printf '[logout_qq] %s\n' "$1"
}

stop_runtime() {
  if [ ! -f "$PID_FILE" ]; then
    return 0
  fi

  PID="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
    kill "$PID" 2>/dev/null || true
    sleep 1
    if kill -0 "$PID" 2>/dev/null; then
      kill -9 "$PID" 2>/dev/null || true
    fi
  fi
  rm -f "$PID_FILE"
}

log "stopping runtime for logout"
stop_runtime
log "logout completed without clearing quick-login history"
