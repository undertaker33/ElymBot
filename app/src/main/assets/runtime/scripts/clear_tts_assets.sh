#!/system/bin/sh

set -eu

APP_HOME="$1"
NATIVE_LIB_DIR="$2"

. "$(dirname "$0")/container_env.sh" "$APP_HOME" "$NATIVE_LIB_DIR"
/system/bin/sh "$(dirname "$0")/bootstrap_container.sh" "$APP_HOME" "$NATIVE_LIB_DIR"

WRITABLE_TMP="$TMP_COMPAT_DIR"
mkdir -p "$WRITABLE_TMP"
/system/bin/chmod 1777 "$WRITABLE_TMP" 2>/dev/null || true
export PROOT_TMP_DIR="$WRITABLE_TMP"

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
    PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    DEBIAN_FRONTEND=noninteractive \
    /bin/bash -lc '
      set -eu
      rm -rf /root/.astrbot-tts-sitepackages
      rm -f /root/.astrbot-tts-assets-ready
      printf "cleared\n"
    '
