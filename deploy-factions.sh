#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
REMOTE="${VF_DEPLOY_REMOTE:-vetroforge-root}"
REMOTE_DIR="${VF_DEPLOY_DIR:-/home/Mc/Factions}"
REMOTE_JAR="${REMOTE_DIR}/plugins/vetroforge.jar"
LOCAL_JAR="${ROOT}/target/vetroforge.jar"
TABLIST_PARITY_FILE="${ROOT}/scripts/reference/tablist-parity.sha256"
SKIP_TABLIST_PARITY="${VF_SKIP_TABLIST_PARITY:-0}"
KNOWN_GOOD_FILE="${ROOT}/scripts/reference/factions-known-good.tsv"
DEPLOY_PASSWORD="${VF_DEPLOY_PASSWORD:-}"

cd "$ROOT"

usage() {
  cat <<'EOF'
Usage:
  ./deploy-factions.sh
      Buildet, prueft und deployed target/vetroforge.jar.

  ./deploy-factions.sh known-good list
      Zeigt lokale Known-Good Labels.

  ./deploy-factions.sh known-good scan
      Zeigt serverseitige vetroforge.jar Backups.

  ./deploy-factions.sh known-good add <label> <remote-jar-path> [note...]
      Merkt ein serverseitiges Backup unter einem Label.
      Beispiel:
      ./deploy-factions.sh known-good add features-latest /home/Mc/Factions/plugins/vetroforge.jar.bak-ah-shop-roleperms-20260705-004932 "Raid/Crates neuer Stand"

  ./deploy-factions.sh known-good restore <label>
      Installiert das gelabelte serverseitige Backup, loescht Remap-Cache und startet Factions neu.

  ./deploy-factions.sh known-good remove <label>
      Entfernt ein lokales Known-Good Label.
EOF
}

ensure_known_good_file() {
  mkdir -p "$(dirname "$KNOWN_GOOD_FILE")"
  if [[ ! -f "$KNOWN_GOOD_FILE" ]]; then
    cat > "$KNOWN_GOOD_FILE" <<'EOF'
# label	remote_path	sha256	note
# Beispiel:
# features-latest	/home/Mc/Factions/plugins/vetroforge.jar.bak-ah-shop-roleperms-20260705-004932	02a52c50080fca8c485bcd992fc594898fdb4775264fc29f5733f3aa1334c96a	Raid/Crates/Shop/Factions neuer Stand
EOF
  fi
}

remote_exec() {
  local cmd="$1"
  if [[ -n "$DEPLOY_PASSWORD" ]]; then
    VF_EXPECT_REMOTE="$REMOTE" VF_EXPECT_CMD="$cmd" VF_EXPECT_PASS="$DEPLOY_PASSWORD" expect <<'EOF'
set timeout -1
log_user 0
spawn ssh -o StrictHostKeyChecking=no $env(VF_EXPECT_REMOTE) $env(VF_EXPECT_CMD)
expect {
  "*assword:*" { send "$env(VF_EXPECT_PASS)\r"; log_user 1; exp_continue }
  eof
}
catch wait result
exit [lindex $result 3]
EOF
  else
    ssh "$REMOTE" "$cmd"
  fi
}

remote_upload() {
  local source="$1"
  local target="$2"
  if [[ -n "$DEPLOY_PASSWORD" ]]; then
    VF_EXPECT_REMOTE="$REMOTE" VF_EXPECT_SOURCE="$source" VF_EXPECT_TARGET="$target" VF_EXPECT_PASS="$DEPLOY_PASSWORD" expect <<'EOF'
set timeout -1
log_user 0
spawn scp -o StrictHostKeyChecking=no $env(VF_EXPECT_SOURCE) "$env(VF_EXPECT_REMOTE):$env(VF_EXPECT_TARGET)"
expect {
  "*assword:*" { send "$env(VF_EXPECT_PASS)\r"; log_user 1; exp_continue }
  eof
}
catch wait result
exit [lindex $result 3]
EOF
  else
    scp "$source" "${REMOTE}:${target}"
  fi
}

known_good_lookup() {
  local label="$1"
  ensure_known_good_file
  awk -F '\t' -v label="$label" '$1 == label { print; found=1; exit } END { if (!found) exit 1 }' "$KNOWN_GOOD_FILE"
}

known_good_list() {
  ensure_known_good_file
  echo "[known-good] Datei: $KNOWN_GOOD_FILE"
  awk -F '\t' '
    /^#/ || NF < 3 { next }
    { printf "  %-18s %-96s %s\n", $1, $2, ($4 ? $4 : "-") }
  ' "$KNOWN_GOOD_FILE"
}

known_good_scan() {
  echo "[known-good] Server-Backups auf ${REMOTE}:${REMOTE_DIR}/plugins"
  remote_exec "ls -lt '${REMOTE_DIR}/plugins/vetroforge.jar'* 2>/dev/null | head -80"
}

known_good_add() {
  local label="${1:-}"
  local remote_path="${2:-}"
  shift 2 || true
  local note="${*:-}"

  if [[ -z "$label" || -z "$remote_path" ]]; then
    echo "[known-good] Fehler: label und remote-jar-path sind Pflicht." >&2
    usage
    exit 1
  fi
  if [[ "$label" == *$'\t'* || "$remote_path" == *$'\t'* ]]; then
    echo "[known-good] Fehler: Tabs in label/path sind nicht erlaubt." >&2
    exit 1
  fi

  ensure_known_good_file
  local sha
  sha="$(remote_exec "set -e; test -s '$remote_path'; sha256sum '$remote_path' | cut -d ' ' -f 1" \
    | grep -Eo '[a-f0-9]{64}' \
    | tail -1)"
  if [[ -z "$sha" ]]; then
    echo "[known-good] Fehler: Konnte SHA fuer $remote_path nicht ermitteln." >&2
    exit 1
  fi

  local tmp
  tmp="$(mktemp)"
  awk -F '\t' -v label="$label" '$1 != label { print }' "$KNOWN_GOOD_FILE" > "$tmp"
  printf '%s\t%s\t%s\t%s\n' "$label" "$remote_path" "$sha" "$note" >> "$tmp"
  mv "$tmp" "$KNOWN_GOOD_FILE"
  echo "[known-good] Gespeichert: $label -> $remote_path ($sha)"
}

known_good_remove() {
  local label="${1:-}"
  if [[ -z "$label" ]]; then
    echo "[known-good] Fehler: label ist Pflicht." >&2
    usage
    exit 1
  fi
  ensure_known_good_file
  local tmp
  tmp="$(mktemp)"
  awk -F '\t' -v label="$label" '$1 != label { print }' "$KNOWN_GOOD_FILE" > "$tmp"
  mv "$tmp" "$KNOWN_GOOD_FILE"
  echo "[known-good] Entfernt: $label"
}

known_good_restore() {
  local label="${1:-}"
  if [[ -z "$label" ]]; then
    echo "[known-good] Fehler: label ist Pflicht." >&2
    usage
    exit 1
  fi

  local row remote_path expected_sha note
  if ! row="$(known_good_lookup "$label")"; then
    echo "[known-good] Fehler: Label nicht gefunden: $label" >&2
    known_good_list >&2
    exit 1
  fi
  IFS=$'\t' read -r _ remote_path expected_sha note <<< "$row"

  echo "[known-good] Restore '$label'"
  echo "[known-good] Quelle: $remote_path"
  [[ -n "${note:-}" ]] && echo "[known-good] Notiz:  $note"

  remote_exec "set -euo pipefail
cd '$REMOTE_DIR'
actual=\$(sha256sum '$remote_path' | awk '{print \$1}')
if [[ \"\$actual\" != '$expected_sha' ]]; then
  echo '[known-good] Fehler: SHA passt nicht mehr.' >&2
  echo '[known-good] Erwartet: $expected_sha' >&2
  echo \"[known-good] Aktuell:  \$actual\" >&2
  exit 1
fi
timeout 5 bash -lc 'echo stop > $REMOTE_DIR/console.in' || true
screen -S Factions -X stuff \$'stop\\r' || true
for i in \$(seq 1 300); do
  if ! screen -list | grep -q '[.]Factions[[:space:]]'; then break; fi
  sleep 2
done
if screen -list | grep -q '[.]Factions[[:space:]]'; then
  echo '[known-good] Fehler: Factions stoppte nicht rechtzeitig.' >&2
  ps -eo pid,ppid,cmd | grep -E 'Paper.jar|java .*nogui' | grep -v grep || true
  exit 2
fi
ts=\$(date +%Y%m%d-%H%M%S)
cp '$REMOTE_JAR' '$REMOTE_JAR.bak-before-known-good-$label-'\$ts
cp '$remote_path' '$REMOTE_JAR'
rm -rf '$REMOTE_DIR/plugins/.paper-remapped'
sha256sum '$REMOTE_JAR'
./start.sh
"
  echo "[known-good] Restore fertig. Log:"
  echo "ssh ${REMOTE} \"tail -n 140 ${REMOTE_DIR}/logs/latest.log\""
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ "${1:-}" == "known-good" ]]; then
  sub="${2:-list}"
  shift 2 || true
  case "$sub" in
    list) known_good_list "$@" ;;
    scan) known_good_scan "$@" ;;
    add) known_good_add "$@" ;;
    remove|rm) known_good_remove "$@" ;;
    restore) known_good_restore "$@" ;;
    *) echo "[known-good] Unbekannter Befehl: $sub" >&2; usage; exit 1 ;;
  esac
  exit 0
fi

echo "[deploy] Maven clean package"
mvn clean package -DskipTests

if [[ ! -s "$LOCAL_JAR" ]]; then
  echo "[deploy] Fehler: $LOCAL_JAR wurde nicht gebaut." >&2
  exit 1
fi

echo "[deploy] JAR pruefen"
if jar tf "$LOCAL_JAR" | grep -E '(^|/).+ [0-9]+\.class$' >/tmp/vf-duplicate-classes.txt; then
  echo "[deploy] Fehler: doppelte/umbenannte Klassen im JAR gefunden:" >&2
  cat /tmp/vf-duplicate-classes.txt >&2
  exit 1
fi

if jar tf "$LOCAL_JAR" | sort | uniq -d | grep . >/tmp/vf-duplicate-entries.txt; then
  echo "[deploy] Fehler: doppelte JAR-Eintraege gefunden:" >&2
  cat /tmp/vf-duplicate-entries.txt >&2
  exit 1
fi

if [[ "$SKIP_TABLIST_PARITY" != "1" ]]; then
  if [[ ! -s "$TABLIST_PARITY_FILE" ]]; then
    echo "[deploy] Fehler: Tablist-Parity-Datei fehlt: $TABLIST_PARITY_FILE" >&2
    exit 1
  fi

  echo "[deploy] Tablist-Parity pruefen"
  tmp_dir="$(mktemp -d)"
  cleanup() { rm -rf "$tmp_dir"; }
  trap cleanup EXIT

  mismatch=0
  while read -r expected entry; do
    [[ -z "${expected:-}" || "${expected:0:1}" == "#" ]] && continue
    if ! jar tf "$LOCAL_JAR" | grep -Fxq "$entry"; then
      echo "[deploy] Fehler: Tablist-Klasse fehlt im JAR: $entry" >&2
      mismatch=1
      continue
    fi

    (cd "$tmp_dir" && jar xf "$LOCAL_JAR" "$entry")
    actual="$(shasum -a 256 "$tmp_dir/$entry" | awk '{print $1}')"
    if [[ "$actual" != "$expected" ]]; then
      echo "[deploy] Fehler: Tablist-Parity verletzt: $entry" >&2
      echo "[deploy] Erwartet: $expected" >&2
      echo "[deploy] Aktuell:  $actual" >&2
      mismatch=1
    fi
  done < "$TABLIST_PARITY_FILE"

  if [[ "$mismatch" != "0" ]]; then
    echo "[deploy] Upload blockiert, damit die bekannte gute Tablist nicht ueberschrieben wird." >&2
    echo "[deploy] Wenn die Tablist bewusst geaendert wurde: Referenz nach Ingame-Test aktualisieren oder VF_SKIP_TABLIST_PARITY=1 setzen." >&2
    exit 1
  fi
fi

echo "[deploy] Upload nach ${REMOTE}:${REMOTE_JAR}"
remote_upload "$LOCAL_JAR" "/tmp/vetroforge.jar"

# Stop-detection uses screen-list, deliberately not `pgrep -f`: the entire body
# of this remote_exec call (including any comment text) is sent to the remote
# host and executed as a single `bash -c "<this text>"`, so a pgrep pattern that
# names the java/jar process would appear in ITS OWN ancestor's argv too and
# self-match forever, regardless of the real JVM's state -- that self-match, not
# a genuinely slow shutdown, was the actual cause of every past "Factions
# stoppte nicht rechtzeitig" failure here (and would equally break start.sh's
# own duplicate-process guard a few lines below if repeated in remote text).
echo "[deploy] Remote installieren und Neustart ausloesen"
remote_exec "set -euo pipefail
cp /tmp/vetroforge.jar '$REMOTE_JAR'
rm -rf '$REMOTE_DIR/plugins/.paper-remapped'
cd '$REMOTE_DIR'
if screen -list | grep -q '[.]Factions[[:space:]]'; then
  echo stop > console.in
  for i in \$(seq 1 300); do
    if ! screen -list | grep -q '[.]Factions[[:space:]]'; then break; fi
    sleep 2
  done
  if screen -list | grep -q '[.]Factions[[:space:]]'; then
    echo '[deploy] Fehler: Factions stoppte nicht rechtzeitig ueber console.in.' >&2
    exit 1
  fi
fi
if [[ -x ./start.sh ]]; then
  ./start.sh
else
  echo '[deploy] Fehler: start.sh nicht gefunden oder nicht ausfuehrbar.' >&2
  exit 1
fi
"

echo "[deploy] Fertig. Live-Log pruefen mit:"
echo "ssh ${REMOTE} \"tail -n 120 ${REMOTE_DIR}/logs/latest.log\""
