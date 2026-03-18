#!/system/bin/sh

set -eu

APP_HOME="$1"
NATIVE_LIB_DIR="$2"
INPUT_FILE="$3"
OUTPUT_FILE="$4"

. "$(dirname "$0")/container_env.sh" "$APP_HOME" "$NATIVE_LIB_DIR"
/system/bin/sh "$(dirname "$0")/bootstrap_container.sh" "$APP_HOME" "$NATIVE_LIB_DIR"

if [ ! -f "$INPUT_FILE" ]; then
  echo "input audio not found: $INPUT_FILE" >&2
  exit 4
fi

MARKER_FILE="$ROOT_HOME_DIR/.astrbot-tts-assets-ready"
if [ ! -f "$MARKER_FILE" ]; then
  echo "tts assets are not prepared yet" >&2
  exit 5
fi

WRITABLE_TMP="$TMP_COMPAT_DIR"
mkdir -p "$WRITABLE_TMP"
/system/bin/chmod 1777 "$WRITABLE_TMP" 2>/dev/null || true
export PROOT_TMP_DIR="$WRITABLE_TMP"

ANDROID_TZ="$(getprop persist.sys.timezone 2>/dev/null || true)"
if [ -z "$ANDROID_TZ" ]; then
  ANDROID_TZ="UTC"
fi

"$PROOT_BIN" \
  -0 \
  -r "$ROOTFS_DIR" \
  --link2symlink \
  -b /dev \
  -b /proc \
  -b /sys \
  -b /system \
  -b /apex \
  -b /dev/pts \
  -b /sdcard \
  -b /storage/emulated/0:/sdcard \
  -b /storage/emulated/0:/storage/emulated/0 \
  -b "$APP_HOME":"$APP_HOME" \
  -b "$WRITABLE_TMP":"$WRITABLE_TMP" \
  -b "$WRITABLE_TMP":/dev/shm \
  -w /root \
  /usr/bin/env -i \
    HOME=/root \
    TMPDIR="$WRITABLE_TMP" \
    TERM=xterm-256color \
    LANG=en_US.UTF-8 \
    TZ="$ANDROID_TZ" \
    PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    ASTRBOT_APP_HOME="$APP_HOME" \
    ASTRBOT_INPUT_FILE="$INPUT_FILE" \
    ASTRBOT_OUTPUT_FILE="$OUTPUT_FILE" \
    DEBIAN_FRONTEND=noninteractive \
    /bin/bash -lc '
      set -eu
      export PYTHONPATH=/root/.astrbot-tts-sitepackages
      python3 "$ASTRBOT_APP_HOME/runtime/scripts/encode_tencent_silk.py" "$ASTRBOT_INPUT_FILE" "$ASTRBOT_OUTPUT_FILE"
    '
