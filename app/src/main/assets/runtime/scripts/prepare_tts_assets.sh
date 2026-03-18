#!/system/bin/sh

set -eu

APP_HOME="$1"
NATIVE_LIB_DIR="$2"

normalize_proxy() {
  proxy_value="$1"
  case "$proxy_value" in
    *://*)
      printf "%s" "$proxy_value"
      ;;
    *)
      printf "http://%s" "$proxy_value"
      ;;
  esac
}

detect_http_proxy() {
  if [ -n "${HTTPS_PROXY:-}" ]; then
    printf "%s" "$HTTPS_PROXY"
    return 0
  fi
  if [ -n "${HTTP_PROXY:-}" ]; then
    printf "%s" "$HTTP_PROXY"
    return 0
  fi

  settings_proxy="$(settings get global http_proxy 2>/dev/null || true)"
  case "$settings_proxy" in
    ""|"null"|":0")
      ;;
    *)
      normalize_proxy "$settings_proxy"
      return 0
      ;;
  esac

  proxy_host="$(getprop http.proxyHost 2>/dev/null || true)"
  proxy_port="$(getprop http.proxyPort 2>/dev/null || true)"
  if [ -n "$proxy_host" ] && [ -n "$proxy_port" ] && [ "$proxy_port" != "0" ]; then
    printf "http://%s:%s" "$proxy_host" "$proxy_port"
    return 0
  fi

  return 1
}

HTTP_PROXY_VALUE="$(detect_http_proxy || true)"

. "$(dirname "$0")/container_env.sh" "$APP_HOME" "$NATIVE_LIB_DIR"
/system/bin/sh "$(dirname "$0")/bootstrap_container.sh" "$APP_HOME" "$NATIVE_LIB_DIR"

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
    ASTRBOT_HTTP_PROXY="$HTTP_PROXY_VALUE" \
    DEBIAN_FRONTEND=noninteractive \
    /bin/bash -lc '
      set -eu
      if [ -n "${ASTRBOT_HTTP_PROXY:-}" ]; then
        export http_proxy="$ASTRBOT_HTTP_PROXY"
        export https_proxy="$ASTRBOT_HTTP_PROXY"
        export HTTP_PROXY="$ASTRBOT_HTTP_PROXY"
        export HTTPS_PROXY="$ASTRBOT_HTTP_PROXY"
        export all_proxy="$ASTRBOT_HTTP_PROXY"
        export ALL_PROXY="$ASTRBOT_HTTP_PROXY"
      fi

      if ! command -v python3 >/dev/null 2>&1; then
        apt-get -o APT::Sandbox::User=root update
        apt-get -o APT::Sandbox::User=root install -y python3 python3-pip ffmpeg
      fi
      if ! python3 -m pip --version >/dev/null 2>&1; then
        apt-get -o APT::Sandbox::User=root update
        apt-get -o APT::Sandbox::User=root install -y python3-pip
      fi
      if ! command -v ffmpeg >/dev/null 2>&1; then
        apt-get -o APT::Sandbox::User=root update
        apt-get -o APT::Sandbox::User=root install -y ffmpeg
      fi

      SITE_PACKAGES_DIR=/root/.astrbot-tts-sitepackages
      MARKER_FILE=/root/.astrbot-tts-assets-ready
      PROXY_STATUS="none"
      if [ -n "${ASTRBOT_HTTP_PROXY:-}" ]; then
        PROXY_STATUS="detected"
      fi

      install_pilk() {
        label="$1"
        index_url="$2"
        rm -rf "$SITE_PACKAGES_DIR"
        mkdir -p "$SITE_PACKAGES_DIR"
        if [ -n "$index_url" ]; then
          python3 -m pip install --no-cache-dir --break-system-packages --target "$SITE_PACKAGES_DIR" -i "$index_url" pilk && {
            SELECTED_MIRROR="$label"
            return 0
          }
        else
          python3 -m pip install --no-cache-dir --break-system-packages --target "$SITE_PACKAGES_DIR" pilk && {
            SELECTED_MIRROR="$label"
            return 0
          }
        fi
        return 1
      }

      SELECTED_MIRROR=""
      if [ -n "${ASTRBOT_HTTP_PROXY:-}" ]; then
        install_pilk "PyPI" "" ||
          install_pilk "Tsinghua" "https://pypi.tuna.tsinghua.edu.cn/simple" ||
          install_pilk "Aliyun" "https://mirrors.aliyun.com/pypi/simple"
      else
        install_pilk "Tsinghua" "https://pypi.tuna.tsinghua.edu.cn/simple" ||
          install_pilk "Aliyun" "https://mirrors.aliyun.com/pypi/simple" ||
          install_pilk "PyPI" ""
      fi

      if [ -z "$SELECTED_MIRROR" ]; then
        echo "failed to install pilk from all known sources" >&2
        exit 19
      fi

      printf "ready\n" > "$MARKER_FILE"
      printf "ASTRBOT_TTS_ASSET_SUMMARY: Proxy %s; mirror %s.\n" "$PROXY_STATUS" "$SELECTED_MIRROR"
    '
