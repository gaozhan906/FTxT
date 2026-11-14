#!/usr/bin/env bash
# Sinkronisasi environment mesin dengan kebutuhan project.
#
# Pemakaian:
#   bash _tools/env-setup.sh --check   # default: hanya cek, tidak mengubah apa pun
#   bash _tools/env-setup.sh --fix     # perbaiki config non-destruktif + perbarui blok status di agent-rules.md
#   bash _tools/env-setup.sh --prune   # --fix + hapus JDK lama & /opt/gradle (DESTRUKTIF, perlu perintah eksplisit)
#
# Exit code: 0 = bersih/berhasil, 1 = ada drift (mode --check) atau error.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RULES="$ROOT/agent-rules.md"
PROFILE="/etc/profile"
PROFILE_BACKUP="/etc/profile.ftxt.bak"
LOCAL_PROPS="$ROOT/local.properties"
SDK_DIR="/opt/android_sdk"
JAVA_ROOT="/opt/java"
NDK_ROOT="/opt/ndk"
CMAKE_ROOT="/opt/cmake"
AAPT2="/usr/bin/aapt2"

MODE="check"

usage() {
  sed -n '2,10p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

for arg in "$@"; do
  case "$arg" in
    --check) MODE="check" ;;
    --fix)   MODE="fix" ;;
    --prune) MODE="prune" ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Argumen tidak dikenal: $arg" >&2; usage >&2; exit 2 ;;
  esac
done

if [ "$MODE" != "check" ] && [ "$(id -u)" -ne 0 ]; then
  echo "PERINGATAN: bukan root; penulisan $PROFILE dan --prune akan dilewati/gagal." >&2
fi

TMP_FILES=()
cleanup() {
  for f in "${TMP_FILES[@]:-}"; do [ -n "$f" ] && rm -f "$f"; done
}
trap cleanup EXIT
tmpfile() { local f; f="$(mktemp)"; TMP_FILES+=("$f"); printf '%s' "$f"; }

have() { command -v "$1" >/dev/null 2>&1; }

jdk_version() {
  local d="$1" v=""
  if [ -f "$d/release" ]; then
    v="$(sed -n 's/^JAVA_VERSION="\([^"]*\)".*/\1/p' "$d/release" | head -1)"
  fi
  if [ -z "$v" ] && [ -x "$d/bin/java" ]; then
    v="$("$d/bin/java" -version 2>&1 | awk -F'"' '/version/{print $2; exit}')"
  fi
  printf '%s' "$v"
}

arch_of() {
  local f="$1"
  [ -e "$f" ] || { printf 'tidak ada'; return; }
  file -L -b "$f" 2>/dev/null | grep -q 'aarch64' && printf 'aarch64' || printf 'x86-64/other'
}

JAVA_PAIRS=""
while IFS= read -r d; do
  [ -x "$d/bin/java" ] || continue
  v="$(jdk_version "$d")"
  [ -n "$v" ] && JAVA_PAIRS+="${v}"$'\t'"${d}"$'\n'
done < <(find "$JAVA_ROOT" -mindepth 1 -maxdepth 1 -type d -name 'jdk*' 2>/dev/null | sort)

JDK_PATH=""
JDK_VER=""
if [ -n "$JAVA_PAIRS" ]; then
  JDK_LINE="$(printf '%s' "$JAVA_PAIRS" | sort -V -k1,1 | tail -1)"
  JDK_VER="${JDK_LINE%%$'\t'*}"
  JDK_PATH="${JDK_LINE#*$'\t'}"
fi

OLD_JDKS=()
if [ -n "$JDK_PATH" ]; then
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    p="${line#*$'\t'}"
    [ "$p" = "$JDK_PATH" ] || OLD_JDKS+=("$p")
  done < <(printf '%s' "$JAVA_PAIRS")
fi

GRADLE_VER="$(sed -n 's/.*gradle-\([0-9][0-9.]*\)-bin\.zip.*/\1/p' "$ROOT/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null | head -1)"
[ -n "$GRADLE_VER" ] || GRADLE_VER="tidak diketahui"

BT_LIST="$(find "$SDK_DIR/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sed 's#.*/##' | sort -V | paste -sd ',' - | sed 's/,/, /g')"
PLAT_LIST="$(find "$SDK_DIR/platforms" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sed 's#.*/##' | sort -V | paste -sd ',' - | sed 's/,/, /g')"
COMPILE_SDK="$(sed -n 's/.*compileSdk[ \t]*=[ \t]*\([0-9][0-9]*\).*/\1/p' "$ROOT/build.gradle" | head -1)"
BT_NEWEST="$(find "$SDK_DIR/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1)"
BT_AAPT2_ARCH="$(arch_of "$BT_NEWEST/aapt2" 2>/dev/null)"

AAPT2_ARCH="$(arch_of "$AAPT2")"
AAPT2_VER="$("$AAPT2" version 2>&1 | head -1 | awk '{print $NF}')"
[ -n "$AAPT2_VER" ] || AAPT2_VER="tidak diketahui"

NDK_PAIRS=""
while IFS= read -r d; do
  [ -f "$d/source.properties" ] || continue
  r="$(sed -n 's/^Pkg.Revision[ \t]*=[ \t]*//p' "$d/source.properties" | tr -d '\r' | head -1)"
  [ -n "$r" ] && NDK_PAIRS+="${r}"$'\t'"${d}"$'\n'
done < <(find "$NDK_ROOT" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort)
NDK_PATH=""; NDK_REV=""
if [ -n "$NDK_PAIRS" ]; then
  line="$(printf '%s' "$NDK_PAIRS" | sort -V -k1,1 | tail -1)"
  NDK_REV="${line%%$'\t'*}"; NDK_PATH="${line#*$'\t'}"
fi

CMAKE_PAIRS=""
while IFS= read -r d; do
  [ -x "$d/bin/cmake" ] || continue
  v="$("$d/bin/cmake" --version 2>/dev/null | head -1 | awk '{print $3}')"
  [ -n "$v" ] && CMAKE_PAIRS+="${v}"$'\t'"${d}"$'\n'
done < <(find "$CMAKE_ROOT" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort)
CMAKE_PATH=""; CMAKE_VER=""
if [ -n "$CMAKE_PAIRS" ]; then
  line="$(printf '%s' "$CMAKE_PAIRS" | sort -V -k1,1 | tail -1)"
  CMAKE_VER="${line%%$'\t'*}"; CMAKE_PATH="${line#*$'\t'}"
fi

SQLITE_DIR="$ROOT/_tools/sqlite-jdbc"
SQLITE_OK="tidak"
SQLITE_VER="tidak diketahui"
if [ -f "$SQLITE_DIR/sqlite-jdbc.jar" ] && [ -f "$SQLITE_DIR/slf4j-api.jar" ] && [ -f "$SQLITE_DIR/Query.java" ]; then
  SQLITE_OK="lengkap"
  if have unzip; then
    v="$(unzip -p "$SQLITE_DIR/sqlite-jdbc.jar" META-INF/MANIFEST.MF 2>/dev/null | tr -d '\r' | awk -F': ' '/Implementation-Version|Bundle-Version/{print $2; exit}')"
    [ -n "$v" ] && SQLITE_VER="$v"
  fi
fi

GRADLE_LEGACY=0
[ -d /opt/gradle ] && GRADLE_LEGACY=1

DRIFT=0
WARNINGS=0
warn() { printf '  [PERINGATAN] %s\n' "$*"; WARNINGS=$((WARNINGS+1)); }

printf '== Ringkasan environment ==\n'
printf 'JDK terbaru : %s %s\n' "${JDK_VER:-TIDAK ADA}" "${JDK_PATH:+($JDK_PATH)}"
printf 'JDK usang   : %s\n' "$( [ ${#OLD_JDKS[@]} -eq 0 ] && echo 'tidak ada' || printf '%s ' "${OLD_JDKS[@]}" )"
printf 'Gradle      : wrapper %s; /opt/gradle %s\n' "$GRADLE_VER" "$( [ "$GRADLE_LEGACY" -eq 1 ] && echo 'ADA (usang)' || echo 'tidak ada' )"
printf 'Android SDK : %s\n' "$SDK_DIR"
printf 'build-tools : %s\n' "${BT_LIST:-tidak ada}"
printf 'platforms   : %s\n' "${PLAT_LIST:-tidak ada}"
printf 'compileSdk  : %s\n' "${COMPILE_SDK:-tidak diketahui}"
printf 'aapt2       : %s (%s); build-tools terbaru: %s\n' "$AAPT2_ARCH" "$AAPT2_VER" "$BT_AAPT2_ARCH"
printf 'NDK         : %s %s\n' "${NDK_REV:-tidak ada}" "${NDK_PATH:+($NDK_PATH)}"
printf 'CMake       : %s %s\n' "${CMAKE_VER:-tidak ada}" "${CMAKE_PATH:+($CMAKE_PATH)}"
printf 'SQLite tool : %s (%s)\n' "$SQLITE_OK" "$SQLITE_VER"

[ -n "$JDK_PATH" ] || { echo "ERROR: tidak ada JDK di $JAVA_ROOT" >&2; exit 1; }

if [ ${#OLD_JDKS[@]} -gt 0 ]; then
  printf '\n[DIPERLUKAN --prune] JDK usang: %s\n' "${OLD_JDKS[*]}"
  DRIFT=1
fi
if [ "$GRADLE_LEGACY" -eq 1 ]; then
  printf '\n[DIPERLUKAN --prune] /opt/gradle masih ada\n'
  DRIFT=1
fi
if [ ! -f "$AAPT2" ]; then warn "aapt2 tidak ada di $AAPT2"; DRIFT=1; fi
if [ "$AAPT2_ARCH" != "aarch64" ]; then warn "aapt2 bukan aarch64 ($AAPT2_ARCH)"; DRIFT=1; fi
if [ ! -d "$SDK_DIR" ]; then warn "Android SDK tidak ada di $SDK_DIR"; DRIFT=1; fi
if [ "$SQLITE_OK" != "lengkap" ]; then warn "tool SQLite di _tools/sqlite-jdbc tidak lengkap"; DRIFT=1; fi

# --- /etc/profile yang diinginkan ---
PROFILE_DESIRED="$(tmpfile)"
PROFILE_FILTERED="$(tmpfile)"
awk '
  /^# BEGIN FTxT-ENV/ {inblock=1; next}
  /^# END FTxT-ENV/ {inblock=0; next}
  inblock {next}
  /^[ \t]*export[ \t]+JAVA_HOME=/ {next}
  /^[ \t]*export[ \t]+JRE_HOME=/ {next}
  /^[ \t]*export[ \t]+CLASSPATH=.*tools\.jar/ {next}
  /^[ \t]*export[ \t]+PATH=.*JAVA_HOME/ {next}
  /^[ \t]*export[ \t]+PATH=.*\/opt\/gradle/ {next}
  /^[ \t]*export[ \t]+ANDROID_HOME=/ {next}
  /^[ \t]*export[ \t]+PATH=.*ANDROID_HOME/ {next}
  {print}
' "$PROFILE" > "$PROFILE_FILTERED" 2>/dev/null || : > "$PROFILE_FILTERED"
# Rapikan: maksimal satu baris kosong berurutan, tanpa baris kosong di akhir.
awk '
  BEGIN { blank=0 }
  /^[ \t]*$/ { blank++; next }
  { if (blank > 0) { print ""; blank=0 } print }
' "$PROFILE_FILTERED" > "$PROFILE_DESIRED"
{
  printf '\n# BEGIN FTxT-ENV (dikelola _tools/env-setup.sh; jangan edit manual)\n'
  printf 'export JAVA_HOME="%s"\n' "$JDK_PATH"
  printf 'export PATH="${JAVA_HOME}/bin:$PATH"\n'
  printf 'export ANDROID_HOME="%s"\n' "$SDK_DIR"
  printf 'export PATH="${ANDROID_HOME}/platform-tools:$PATH"\n'
  printf 'export PATH="${ANDROID_HOME}/tools/bin:$PATH"\n'
  printf 'export PATH="${ANDROID_HOME}/tools:$PATH"\n'
  printf '# END FTxT-ENV\n'
} >> "$PROFILE_DESIRED"

PROFILE_DIFF=0
cmp -s "$PROFILE" "$PROFILE_DESIRED" || { PROFILE_DIFF=1; DRIFT=1; }

# --- local.properties yang diinginkan ---
LOCAL_DESIRED="$(tmpfile)"
if [ -f "$LOCAL_PROPS" ]; then
  awk '!/^[ \t]*(sdk|ndk|cmake)\.dir[ \t]*=/' "$LOCAL_PROPS" > "$LOCAL_DESIRED"
fi
{
  printf 'sdk.dir=%s\n' "$SDK_DIR"
  [ -n "$NDK_PATH" ] && printf 'ndk.dir=%s\n' "$NDK_PATH"
  [ -n "$CMAKE_PATH" ] && printf 'cmake.dir=%s\n' "$CMAKE_PATH"
} >> "$LOCAL_DESIRED"

LOCAL_DIFF=0
cmp -s "$LOCAL_PROPS" "$LOCAL_DESIRED" 2>/dev/null || { LOCAL_DIFF=1; DRIFT=1; }

# --- blok status otomatis di agent-rules.md ---
STATE_BLOCK="$(tmpfile)"
{
  printf -- '- **JDK:** `%s` (OpenJDK `%s`) di `%s`\n' "$(basename "$JDK_PATH")" "$JDK_VER" "$JDK_PATH"
  printf -- '- **Gradle:** wrapper `%s` (hanya `./gradlew`); `/opt/gradle` %s\n' "$GRADLE_VER" "$( [ "$GRADLE_LEGACY" -eq 1 ] && echo 'masih ada (usang)' || echo 'tidak ada' )"
  printf -- '- **Android SDK:** `%s` — build-tools: %s; platforms: %s; `compileSdk=%s`\n' "$SDK_DIR" "${BT_LIST:-tidak ada}" "${PLAT_LIST:-tidak ada}" "${COMPILE_SDK:-tidak diketahui}"
  printf -- '- **aapt2:** `%s` %s (%s); aapt2 di build-tools terbaru: %s\n' "$AAPT2" "$AAPT2_ARCH" "$AAPT2_VER" "$BT_AAPT2_ARCH"
  printf -- '- **NDK:** %s\n' "$( [ -n "$NDK_PATH" ] && printf '`%s` di `%s`' "$NDK_REV" "$NDK_PATH" || echo 'tidak ada' )"
  printf -- '- **CMake:** %s\n' "$( [ -n "$CMAKE_PATH" ] && printf '`%s` di `%s`' "$CMAKE_VER" "$CMAKE_PATH" || echo 'tidak ada' )"
  printf -- '- **SQLite reader:** `_tools/sqlite-jdbc` %s (sqlite-jdbc `%s`)\n' "$SQLITE_OK" "$SQLITE_VER"
  if [ ${#OLD_JDKS[@]} -gt 0 ]; then
    printf -- '- **JDK usang:** %s\n' "$(printf '`%s` ' "${OLD_JDKS[@]}")"
  fi
} > "$STATE_BLOCK"

RULES_DESIRED="$(tmpfile)"
if grep -q '^<!-- BEGIN FTxT-ENV-STATE' "$RULES" 2>/dev/null; then
  awk -v block="$STATE_BLOCK" '
    /^<!-- BEGIN FTxT-ENV-STATE/ {print; while ((getline line < block) > 0) print line; close(block); skip=1; next}
    /^<!-- END FTxT-ENV-STATE/ {skip=0; print; next}
    skip {next}
    {print}
  ' "$RULES" > "$RULES_DESIRED"
else
  awk -v block="$STATE_BLOCK" '
    {print}
    /^## 3\. Environment Build/ && !done {
      print ""
      print "<!-- BEGIN FTxT-ENV-STATE (dihasilkan _tools/env-setup.sh; jangan edit manual) -->"
      while ((getline line < block) > 0) print line
      close(block)
      print "<!-- END FTxT-ENV-STATE -->"
      done=1
    }
  ' "$RULES" > "$RULES_DESIRED"
fi

RULES_DIFF=0
cmp -s "$RULES" "$RULES_DESIRED" || { RULES_DIFF=1; DRIFT=1; }

echo
if [ "$PROFILE_DIFF" -eq 1 ]; then
  echo "[DIPERLUKAN --fix] $PROFILE tidak sinkron:"
  diff -u "$PROFILE" "$PROFILE_DESIRED" 2>/dev/null | sed -n '1,40p' || true
fi
if [ "$LOCAL_DIFF" -eq 1 ]; then
  echo "[DIPERLUKAN --fix] $LOCAL_PROPS tidak sinkron:"
  diff -u "$LOCAL_PROPS" "$LOCAL_DESIRED" 2>/dev/null | sed -n '1,40p' || true
fi
if [ "$RULES_DIFF" -eq 1 ]; then
  echo "[DIPERLUKAN --fix] blok status di $RULES belum sinkron"
fi

if [ "$MODE" = "check" ]; then
  if [ "$DRIFT" -eq 0 ] && [ "$WARNINGS" -eq 0 ]; then
    echo "Status: bersih, tidak ada yang perlu diubah."
    exit 0
  fi
  echo "Status: ada drift. Jalankan: bash _tools/env-setup.sh --fix (dan --prune bila disarankan)."
  exit 1
fi

# --- apply ---
if [ "$PROFILE_DIFF" -eq 1 ]; then
  if [ "$(id -u)" -eq 0 ]; then
    cp -p "$PROFILE" "$PROFILE_BACKUP" 2>/dev/null || true
    cat "$PROFILE_DESIRED" > "$PROFILE"
    echo "[OK] $PROFILE diperbarui (backup: $PROFILE_BACKUP)"
  else
    echo "[SKIP] $PROFILE tidak bisa ditulis (bukan root)"
  fi
fi
if [ "$LOCAL_DIFF" -eq 1 ]; then
  cat "$LOCAL_DESIRED" > "$LOCAL_PROPS"
  echo "[OK] $LOCAL_PROPS diperbarui"
fi
if [ "$RULES_DIFF" -eq 1 ]; then
  cat "$RULES_DESIRED" > "$RULES"
  echo "[OK] blok status di $RULES diperbarui"
fi

if [ "$MODE" = "prune" ]; then
  if [ "$(id -u)" -eq 0 ]; then
    for d in "${OLD_JDKS[@]:-}"; do
      [ -n "$d" ] && [ -d "$d" ] || continue
      rm -rf "$d" && echo "[HAPUS] $d"
    done
    if [ -d /opt/gradle ]; then
      rm -rf /opt/gradle && echo "[HAPUS] /opt/gradle"
    fi
  else
    echo "[SKIP] --prune butuh root"
  fi
fi

echo "Selesai. Verifikasi login shell: bash -lc 'echo \$JAVA_HOME; java -version; echo \$ANDROID_HOME'"
exit 0
