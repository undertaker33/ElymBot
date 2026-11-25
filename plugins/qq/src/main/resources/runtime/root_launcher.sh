#!/bin/bash

set -eu
trap 'code=$?; echo "elymbot_napcat_entry exit code: ${code}"' EXIT

export DEBIAN_FRONTEND=noninteractive
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export HOME=/root
export NAPCAT_DISABLE_PIPE=1
export NAPCAT_DISABLE_MULTI_PROCESS=1
export NAPCAT_WEBUI_PREFERRED_PORT=6099

NAPCAT_HOME="/root/napcat"
NAPCAT_CONFIG_DIR="$NAPCAT_HOME/config"
NAPCAT_ENTRY="/root/launcher.sh"
NAPCAT_LAUNCHER_SO="/root/libnapcat_launcher.so"
NAPCAT_INSTALLER_CACHE="/root/napcat-install.sh"
ONLINE_LAUNCHER_SOURCE="/root/online-launcher.cpp"
NAPCAT_CONFIG_BACKUP="/root/napcat_config_backup"
ELYMBOT_DPKG_DIR="/var/lib"
ELYMBOT_APT_STATE_DIR="/var/lib/apt"
ELYMBOT_APT_CACHE_DIR="/var/cache/apt"
DPKG_ADMINDIR="/var/lib/dpkg"
RUNTIME_SECRET_FILE="${ELYMBOT_APP_HOME:-}/runtime/config/runtime-secrets.env"
RUNTIME_ASSET_DIR="${ELYMBOT_APP_HOME:-}/runtime/assets"
BUNDLED_NAPCAT_INSTALLER_SOURCE="$RUNTIME_ASSET_DIR/napcat-installer.sh"
BUNDLED_NAPCAT_ZIP_SOURCE="$RUNTIME_ASSET_DIR/NapCat.Shell.zip"
BUNDLED_QQ_DEB_SOURCE="$RUNTIME_ASSET_DIR/QQ.deb"
BUNDLED_QQ_RPM_SOURCE="$RUNTIME_ASSET_DIR/QQ.rpm"
BUNDLED_LAUNCHER_CPP_SOURCE="$RUNTIME_ASSET_DIR/launcher.cpp"
BUNDLED_OFFLINE_DEBS_ARCHIVE="$RUNTIME_ASSET_DIR/offline-debs.tar"
OFFLINE_DEB_DIR="/root/offline-debs"

cd /root 2>/dev/null || cd / 2>/dev/null || true

write_progress() {
  local percent="$1"
  local label="$2"
  local mode="${3:-0}"

  if [ -n "${PROGRESS_FILE:-}" ]; then
    printf "%s\n" "$percent" > "$PROGRESS_FILE" 2>/dev/null || true
  fi
  if [ -n "${PROGRESS_LABEL_FILE:-}" ]; then
    printf "%s\n" "$label" > "$PROGRESS_LABEL_FILE" 2>/dev/null || true
  fi
  if [ -n "${PROGRESS_MODE_FILE:-}" ]; then
    printf "%s\n" "$mode" > "$PROGRESS_MODE_FILE" 2>/dev/null || true
  fi
}

load_runtime_secrets() {
  if [ -z "${ELYMBOT_APP_HOME:-}" ]; then
    echo "runtime secret source is unavailable" >&2
    exit 6
  fi
  if [ ! -f "$RUNTIME_SECRET_FILE" ]; then
    echo "runtime secret file is unavailable" >&2
    exit 6
  fi

  # shellcheck disable=SC1090
  . "$RUNTIME_SECRET_FILE"

  if [ -z "${NAPCAT_WEBUI_SECRET_KEY:-}" ] || [ -z "${NAPCAT_WEBUI_JWT_SECRET_KEY:-}" ] ||
    [ -z "${ONEBOT_WS_TOKEN:-}" ]; then
    echo "runtime secret file is incomplete" >&2
    exit 6
  fi

  export NAPCAT_WEBUI_SECRET_KEY
  export NAPCAT_WEBUI_JWT_SECRET_KEY
  export ONEBOT_WS_TOKEN
}

mark_installer_cached() {
  local cached="$1"

  if [ -n "${INSTALLER_CACHE_FILE:-}" ]; then
    printf "%s\n" "$cached" > "$INSTALLER_CACHE_FILE" 2>/dev/null || true
  fi
}

fetch_verified() {
  local url="$1" digest="$2" destination="$3" label="$4" accept="${5:-application/vnd.github.raw+json}"
  if [ -f "$destination" ] && printf '%s  %s\n' "$digest" "$destination" | sha256sum --check --status; then
    return
  fi

  echo "downloading $label from $url"
  local partial="${destination}.part"
  curl -fLsS --proto '=https' --proto-redir '=https' \
    -H "Accept: $accept" \
    --connect-timeout 10 --max-time 130 --retry 2 --retry-all-errors --retry-max-time 160 \
    "$url" -o "$partial"
  printf '%s  %s\n' "$digest" "$partial" | sha256sum --check
  mv -f "$partial" "$destination"
}

prepare_online_napcat_installer() {
  write_progress 45 "download-installer" 1
  fetch_verified \
    'https://api.github.com/repos/towjf/napcat-linux-installer/contents/install.sh?ref=fb84f1f78db6ee74ab43d2410f590b844b1c9bf9' \
    'bd6a04d3f4b3fcf1bf4f33d8f02a28c829514497484cb149cc7eb27a59dc341b' \
    "$NAPCAT_INSTALLER_CACHE" 'NapCat installer'
  fetch_verified \
    'https://api.github.com/repos/bianyuan456/napcat-linux-launcher/contents/launcher.cpp?ref=557b5ea2de8867ae8d72f5054ab0cdd96c0aa9c7' \
    '4bb4c04b379cb6bb361dee6b3561ec24f85f32e13ca86a359895e9c67203e258' \
    "$ONLINE_LAUNCHER_SOURCE" 'NapCat launcher source'
  write_progress 55 "download-napcat" 1
  fetch_verified \
    'https://github.com/XingYunStar/NapCatQQ/releases/download/v4.18.42/NapCat.Shell.zip' \
    '02e5ff0f79916c1e04c81c1b4590056406fb4293f1f1300c955e71fd08b2829d' \
    '/root/napcat-shell-v4.18.42.zip' 'NapCat Shell' 'application/octet-stream'
  ln -sfn /root/napcat-shell-v4.18.42.zip /root/NapCat.Shell.zip
  mark_installer_cached 1
}

stage_bundled_runtime_asset() {
  local source_file="$1"
  local target_file="$2"
  local required="${3:-1}"

  if [ ! -f "$source_file" ]; then
    if [ "$required" = "1" ]; then
      echo "missing bundled runtime asset: $source_file" >&2
      return 1
    fi
    return 0
  fi

  cp "$source_file" "$target_file"
}

has_bundled_napcat_installer_assets() {
  [ -f "$BUNDLED_NAPCAT_INSTALLER_SOURCE" ] \
    && [ -f "$BUNDLED_NAPCAT_ZIP_SOURCE" ] \
    && { [ -x /opt/QQ/qq ] || [ -f "$BUNDLED_QQ_DEB_SOURCE" ]; } \
    && [ -f "$BUNDLED_LAUNCHER_CPP_SOURCE" ]
}

prepare_bundled_napcat_installer() {
  if [ -z "${ELYMBOT_APP_HOME:-}" ] || [ ! -d "$RUNTIME_ASSET_DIR" ]; then
    echo "bundled runtime asset dir missing: $RUNTIME_ASSET_DIR" >&2
    return 1
  fi
  if ! has_bundled_napcat_installer_assets; then
    echo "bundled napcat installer assets unavailable under $RUNTIME_ASSET_DIR"
    return 1
  fi

  write_progress 45 "stage-bundled-installer" 1
  stage_bundled_runtime_asset "$BUNDLED_NAPCAT_INSTALLER_SOURCE" "$NAPCAT_INSTALLER_CACHE"
  chmod +x "$NAPCAT_INSTALLER_CACHE"
  stage_bundled_runtime_asset "$BUNDLED_NAPCAT_ZIP_SOURCE" "/root/NapCat.Shell.zip"
  stage_bundled_runtime_asset "$BUNDLED_QQ_DEB_SOURCE" "/root/QQ.deb" 0
  stage_bundled_runtime_asset "$BUNDLED_LAUNCHER_CPP_SOURCE" "/root/launcher.cpp"
  stage_bundled_runtime_asset "$BUNDLED_QQ_RPM_SOURCE" "/root/QQ.rpm" 0

  rm -rf "$OFFLINE_DEB_DIR"
  mkdir -p "$OFFLINE_DEB_DIR"
  if [ -f "$BUNDLED_OFFLINE_DEBS_ARCHIVE" ]; then
    tar -xf "$BUNDLED_OFFLINE_DEBS_ARCHIVE" -C "$OFFLINE_DEB_DIR"
  fi

  mark_installer_cached 1
  write_progress 55 "installer-bundled" 0
}

prepare_bundled_launcher_shim() {
  if [ -z "${ELYMBOT_APP_HOME:-}" ] || [ ! -d "$RUNTIME_ASSET_DIR" ]; then
    echo "bundled runtime asset dir missing: $RUNTIME_ASSET_DIR" >&2
    return 1
  fi
  if [ ! -f "$BUNDLED_LAUNCHER_CPP_SOURCE" ]; then
    echo "bundled launcher shim asset unavailable: $BUNDLED_LAUNCHER_CPP_SOURCE"
    return 1
  fi

  stage_bundled_runtime_asset "$BUNDLED_LAUNCHER_CPP_SOURCE" "/root/launcher.cpp"
  g++ -shared -fPIC /root/launcher.cpp -o "$NAPCAT_LAUNCHER_SO" -ldl
}

dpkg_cmd() {
  if [ "$DPKG_ADMINDIR" = "/var/lib/dpkg" ]; then
    dpkg "$@"
  else
    dpkg --admindir="$DPKG_ADMINDIR" "$@"
  fi
}

apt_cmd() {
  if [ "$DPKG_ADMINDIR" = "/var/lib/dpkg" ] \
    && [ "$ELYMBOT_APT_STATE_DIR" = "/var/lib/apt" ] \
    && [ "$ELYMBOT_APT_CACHE_DIR" = "/var/cache/apt" ]; then
    apt-get -o APT::Sandbox::User=root "$@"
  else
    apt-get \
      -o APT::Sandbox::User=root \
      -o "Dir::State=$ELYMBOT_APT_STATE_DIR" \
      -o "Dir::State::status=$DPKG_ADMINDIR/status" \
      -o "Dir::Cache=$ELYMBOT_APT_CACHE_DIR" \
      -o "DPkg::Options::=--admindir=$DPKG_ADMINDIR" \
      "$@"
  fi
}

prepare_writable_paths() {
  chmod 1777 /tmp || true
  [ -f "$DPKG_ADMINDIR/status" ] || cp /var/lib/dpkg/status "$DPKG_ADMINDIR/status" || true
  [ -f "$DPKG_ADMINDIR/available" ] || touch "$DPKG_ADMINDIR/available" || true
  [ -f "$DPKG_ADMINDIR/status-old" ] || cp "$DPKG_ADMINDIR/status" "$DPKG_ADMINDIR/status-old" || true
  touch "$DPKG_ADMINDIR/lock" "$DPKG_ADMINDIR/lock-frontend" || true
  chmod 666 "$DPKG_ADMINDIR/status" "$DPKG_ADMINDIR/status-old" "$DPKG_ADMINDIR/available" || true
  chmod 600 "$DPKG_ADMINDIR/lock" "$DPKG_ADMINDIR/lock-frontend" || true
}

ensure_sudo_shim() {
  if command -v sudo >/dev/null 2>&1; then
    return 0
  fi

  mkdir -p /usr/local/bin
  cat > /usr/local/bin/sudo <<'EOF'
#!/bin/sh
if [ "$#" -eq 0 ]; then
  exit 0
fi
exec "$@"
EOF
  chmod +x /usr/local/bin/sudo
  echo "installed sudo compatibility shim at /usr/local/bin/sudo"
}

repair_dpkg_state() {
  if ! command -v dpkg >/dev/null 2>&1; then
    return 0
  fi

  if dpkg_cmd --configure -a >/dev/null 2>&1; then
    return 0
  fi

  echo "dpkg recovery: first configure pass failed"
  rm -f "$DPKG_ADMINDIR/lock" "$DPKG_ADMINDIR/lock-frontend" "$ELYMBOT_APT_CACHE_DIR/archives/lock" || true
  apt_cmd update || true
  apt_cmd install -f -y || true
  dpkg_cmd --configure -a >/dev/null 2>&1 || true
}

ensure_base_packages() {
  if command -v curl >/dev/null 2>&1 \
    && command -v unzip >/dev/null 2>&1 \
    && command -v zip >/dev/null 2>&1 \
    && command -v jq >/dev/null 2>&1 \
    && command -v Xvfb >/dev/null 2>&1 \
    && command -v screen >/dev/null 2>&1 \
    && command -v xauth >/dev/null 2>&1 \
    && command -v g++ >/dev/null 2>&1; then
    write_progress 30 "base-ready" 0
    return 0
  fi

  write_progress 15 "install-base" 1
  echo "installing base dependencies from network"
  apt_cmd update
  if apt_cmd install -y curl ca-certificates zip unzip jq xvfb screen xauth procps g++; then
    write_progress 30 "base-ready" 0
    return 0
  fi

  echo "base package install failed, retrying after dpkg recovery"
  repair_dpkg_state || true
  apt_cmd update
  apt_cmd install -y curl ca-certificates zip unzip jq xvfb screen xauth procps g++
  write_progress 30 "base-ready" 0
}

backup_napcat_config() {
  if [ -d "$NAPCAT_CONFIG_DIR" ]; then
    write_progress 38 "backup-config" 0
    echo "backing up napcat config"
    rm -rf "$NAPCAT_CONFIG_BACKUP"
    mkdir -p "$NAPCAT_CONFIG_BACKUP"
    cp -R "$NAPCAT_CONFIG_DIR"/. "$NAPCAT_CONFIG_BACKUP"/ 2>/dev/null || true
  fi
}

restore_napcat_config() {
  if [ -d "$NAPCAT_CONFIG_BACKUP" ]; then
    write_progress 82 "restore-config" 0
    echo "restoring napcat config"
    mkdir -p "$NAPCAT_CONFIG_DIR"
    cp -R "$NAPCAT_CONFIG_BACKUP"/. "$NAPCAT_CONFIG_DIR"/ 2>/dev/null || true
    rm -rf "$NAPCAT_CONFIG_BACKUP"
  fi
}

resolve_qq_binary() {
  local candidate
  for candidate in /opt/QQ/qq /usr/bin/qq /usr/local/bin/qq; do
    if [ -x "$candidate" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  return 1
}

ensure_virtual_display() {
  export DISPLAY="${DISPLAY:-:1}"
  export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/runtime-root}"
  export QT_X11_NO_MITSHM=1
  export LIBGL_ALWAYS_SOFTWARE=1
  export ELECTRON_OZONE_PLATFORM_HINT=x11

  mkdir -p "$XDG_RUNTIME_DIR"
  chmod 700 "$XDG_RUNTIME_DIR" 2>/dev/null || true

  if pgrep -f "Xvfb ${DISPLAY}" >/dev/null 2>&1; then
    echo "virtual display already running on ${DISPLAY}"
    return 0
  fi

  rm -f "/tmp/.X${DISPLAY#:}-lock" "/tmp/.X11-unix/X${DISPLAY#:}" 2>/dev/null || true
  mkdir -p /tmp/.X11-unix
  chmod 1777 /tmp/.X11-unix 2>/dev/null || true

  echo "starting Xvfb on ${DISPLAY}"
  nohup Xvfb "$DISPLAY" -screen 0 1280x720x24 -nolisten tcp -ac >/tmp/xvfb.log 2>&1 &

  local retries=20
  while [ "$retries" -gt 0 ]; do
    if [ -S "/tmp/.X11-unix/X${DISPLAY#:}" ]; then
      echo "virtual display ready on ${DISPLAY}"
      return 0
    fi
    sleep 0.2
    retries=$((retries - 1))
  done

  echo "failed to start Xvfb on ${DISPLAY}" >&2
  return 1
}

ensure_qq_launcher_compat() {
  local qq_bin=""
  if ! qq_bin="$(resolve_qq_binary)"; then
    echo "qq launcher compatibility: binary not found"
    return 1
  fi

  if [ -d /usr/local/bin ] || mkdir -p /usr/local/bin 2>/dev/null; then
    ln -sf "$qq_bin" /usr/local/bin/qq || true
  fi

  if [ -f "$NAPCAT_LAUNCHER_SO" ]; then
    cat > "$NAPCAT_ENTRY" <<EOF
#!/bin/bash
export DISPLAY="\${DISPLAY:-:1}"
trap "" SIGPIPE
LD_PRELOAD="$NAPCAT_LAUNCHER_SO" "$qq_bin" --no-sandbox
EOF
    chmod +x "$NAPCAT_ENTRY"
    echo "rewrote napcat launcher to bundled runtime shim using ${qq_bin}"
    return 0
  fi

  if [ -f "$NAPCAT_ENTRY" ] && grep -q 'LD_PRELOAD=./libnapcat_launcher.so qq --no-sandbox' "$NAPCAT_ENTRY"; then
    sed -i "s|LD_PRELOAD=./libnapcat_launcher.so qq --no-sandbox|LD_PRELOAD=./libnapcat_launcher.so ${qq_bin} --no-sandbox|g" "$NAPCAT_ENTRY"
    echo "patched legacy napcat launcher to use ${qq_bin}"
  fi

  return 0
}

repair_existing_napcat_install() {
  write_progress 65 "repair-existing-install" 0
  if prepare_bundled_launcher_shim; then
    echo "repaired existing napcat install with bundled launcher shim"
  else
    echo "bundled launcher shim unavailable, keeping existing napcat launcher"
  fi
  ensure_qq_launcher_compat
  mark_installer_cached 1
}

ensure_napcat_installed() {
  if [ -f "$NAPCAT_ENTRY" ] && [ -d "$NAPCAT_HOME" ]; then
    echo "existing napcat installation detected"
    repair_existing_napcat_install
    return 0
  fi

  mark_installer_cached 0
  backup_napcat_config
  rm -rf "$NAPCAT_HOME"
  rm -f "$NAPCAT_ENTRY"

  local installer_mode="bundled"
  echo "staging bundled napcat installer"
  if [ "${ELYMBOT_INSTALL_MODE:-online}" = offline ]; then
    prepare_bundled_napcat_installer
    echo "using bundled napcat installer assets"
  else
    echo "bundled napcat installer assets unavailable, falling back to upstream installer"
    prepare_online_napcat_installer
    installer_mode="upstream"
  fi

  write_progress 65 "run-installer" 1
  if [ "$installer_mode" = "bundled" ]; then
    echo "running bundled napcat installer"
    /bin/bash "$NAPCAT_INSTALLER_CACHE"
  else
    echo "running upstream napcat installer"
    NAPCAT_CONNECT_TIMEOUT=12 NAPCAT_DOWNLOAD_TIMEOUT=210 \
      /bin/bash "$NAPCAT_INSTALLER_CACHE" --github-proxy 0 --launcher-source "$ONLINE_LAUNCHER_SOURCE" --skip-deps
  fi

  restore_napcat_config

  if [ ! -f "$NAPCAT_ENTRY" ]; then
    echo "napcat installer finished but launcher.sh is missing" >&2
    return 1
  fi

  ensure_qq_launcher_compat
}

write_runtime_config() {
  write_progress 90 "write-config" 0
  mkdir -p "$NAPCAT_CONFIG_DIR"
  echo "writing local NapCat runtime config"
  cat > "$NAPCAT_CONFIG_DIR/webui.json" <<EOF
{
  "host": "127.0.0.1",
  "port": 6099,
  "token": "${NAPCAT_WEBUI_SECRET_KEY}",
  "disableWebUI": false
}
EOF
  cat > "$NAPCAT_CONFIG_DIR/onebot11.json" <<EOF
{
  "network": {
    "httpServers": [],
    "httpClients": [],
    "websocketServers": [],
    "websocketClients": [
      {
        "name": "WsClient",
        "enable": true,
        "url": "ws://127.0.0.1:6199/ws",
        "messagePostFormat": "array",
        "reportSelfMessage": false,
        "reconnectInterval": 5000,
        "token": "${ONEBOT_WS_TOKEN}",
        "debug": false,
        "heartInterval": 30000
      }
    ]
  },
  "musicSignUrl": "",
  "enableLocalFile2Url": false,
  "parseMultMsg": false
}
EOF
}

write_progress 10 "prepare-container" 0
load_runtime_secrets
prepare_writable_paths
ensure_sudo_shim
if [ "${ELYMBOT_INSTALL_MODE:-online}" = offline ]; then
  if ! has_bundled_napcat_installer_assets; then
    echo "offline runtime assets are incomplete" >&2
    exit 7
  fi
else
  ensure_base_packages
fi
ensure_napcat_installed
ensure_virtual_display
write_runtime_config

write_progress 96 "start-napcat" 1
echo "starting napcat launcher"
exec /bin/bash "$NAPCAT_ENTRY"
