#!/system/bin/sh

set -eu

APP_HOME="$1"
NATIVE_LIB_DIR="$2"
. "$(dirname "$0")/container_env.sh" "$APP_HOME" "$NATIVE_LIB_DIR"

if [ ! -x "$PROOT_BIN" ]; then
  echo "Missing proot binary: $PROOT_BIN" >&2
  exit 2
fi

if [ ! -d "$ROOTFS_DIR" ] || [ ! -f "$ROOTFS_DIR/usr/bin/env" ]; then
  echo "Ubuntu rootfs not installed at $ROOTFS_DIR" >&2
  echo "Expected extracted rootfs before bridge start" >&2
  exit 3
fi

LEGACY_ROOT_HOME_DIR="$VAR_DIR/container/root-home"
LEGACY_TMP_ROOT_HOME_DIR="$VAR_DIR/container/tmp/astrbot-root"
LEGACY_OPT_ROOT_HOME_DIR="$ROOTFS_DIR/opt/astrbot-root"

mkdir -p "$ROOTFS_DIR/root" "$ROOT_HOME_DIR"

migrate_tree_if_missing() {
  source_dir="$1"
  if [ ! -d "$source_dir" ]; then
    return 0
  fi

  for name in .bashrc .profile .config .cache .local napcat launcher.sh napcat.sh astrbot_napcat_entry.sh; do
    if [ -e "$source_dir/$name" ] && [ ! -e "$ROOT_HOME_DIR/$name" ]; then
      cp -R "$source_dir/$name" "$ROOT_HOME_DIR/$name"
    fi
  done
}

migrate_tree_if_missing "$LEGACY_ROOT_HOME_DIR"
migrate_tree_if_missing "$LEGACY_TMP_ROOT_HOME_DIR"
migrate_tree_if_missing "$LEGACY_OPT_ROOT_HOME_DIR"

if [ -f "$ROOTFS_DIR/root/.bashrc" ] && [ ! -f "$ROOT_HOME_DIR/.bashrc" ]; then
  cp "$ROOTFS_DIR/root/.bashrc" "$ROOT_HOME_DIR/.bashrc"
fi

for name in .profile .config .cache .local; do
  if [ -e "$ROOTFS_DIR/root/$name" ] && [ ! -e "$ROOT_HOME_DIR/$name" ]; then
    cp -R "$ROOTFS_DIR/root/$name" "$ROOT_HOME_DIR/$name"
  fi
done

if [ -d "$ROOTFS_DIR/root/napcat" ] && [ ! -d "$ROOT_HOME_DIR/napcat" ]; then
  cp -R "$ROOTFS_DIR/root/napcat" "$ROOT_HOME_DIR/napcat"
fi

for name in launcher.sh napcat.sh; do
  if [ -f "$ROOTFS_DIR/root/$name" ] && [ ! -f "$ROOT_HOME_DIR/$name" ]; then
    cp "$ROOTFS_DIR/root/$name" "$ROOT_HOME_DIR/$name"
  fi
done

cp "$(dirname "$0")/root_launcher.sh" "$ROOT_HOME_DIR/astrbot_napcat_entry.sh"
chmod +x "$ROOT_HOME_DIR/astrbot_napcat_entry.sh"

echo "Container bootstrap check passed"
