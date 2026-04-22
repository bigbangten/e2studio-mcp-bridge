---
description: One-shot installer for the e2 studio (Renesas) bridge plug-in. Detects e2studio install, installs the bundle JAR, registers it with Eclipse, restarts e2studio, verifies the bridge responds.
argument-hint: [--e2studio-path <path>] [--rebuild] [--no-restart]
---

Install the `com.example.e2studio.agent.bridge` Eclipse plug-in into the user's e2 studio (Renesas) so the `/s32` commands work.

This is a **non-trivial, mildly invasive install**. Communicate clearly at each step. If anything feels wrong, stop and ask.

## Preconditions the AI must verify BEFORE touching anything

1. e2 studio (Renesas) is installed (any update level).
2. The user is on Windows (current build supports Windows only; Linux would need path tweaks).
3. The user has write access to the e2studio install directory (no admin prompt required).
4. A JDK ≥ 17 is available **only if rebuilding from source**. If a prebuilt JAR is present (via GitHub Release or `releases/` in repo), no JDK needed.

## Installation procedure

### Step 1 — Locate e2studio

Scan standard install locations, in order:
- `C:\Renesas\e2_studio\eclipse`
- `C:\Renesas\e2_studio\*\eclipse`
- `D:\Renesas\e2_studio\eclipse`
- `C:\Program Files\Renesas\e2_studio\eclipse`

Confirm presence of `eclipsec.exe`, `plugins/`, `configuration/org.eclipse.equinox.simpleconfigurator/bundles.info`. Record the path as `$e2studio_ECLIPSE`.

If the user passed `--e2studio-path`, use that instead.

If none found: ask the user for the path. Do not proceed without a valid one.

### Step 2 — Locate or obtain the plug-in JAR

Priority order:

**1. Prebuilt JAR in repo** — `${CLAUDE_PLUGIN_ROOT}/../eclipse-bridge/releng/com.example.e2studio.agent.repository/target/repository/plugins/com.example.e2studio.agent.bridge_*.jar`.

**2. GitHub Release asset** — the preferred path for end users who didn't clone the repo:

```bash
DEST=$HOME/.cache/e2studio-mcp-bridge
mkdir -p "$DEST"

# Prefer gh CLI (no auth needed for public repos when using --repo flag)
if command -v gh >/dev/null 2>&1; then
  gh release download --repo bigbangten/e2studio-mcp-bridge \
    --pattern 'com.example.e2studio.agent.bridge_*.jar' \
    --dir "$DEST" --clobber
fi

# Curl fallback when gh CLI is not available
if [ -z "$(ls "$DEST"/com.example.e2studio.agent.bridge_*.jar 2>/dev/null)" ]; then
  LATEST_URL=$(curl -s https://api.github.com/repos/bigbangten/e2studio-mcp-bridge/releases/latest \
    | grep '"browser_download_url"' \
    | grep 'com.example.e2studio.agent.bridge_' \
    | head -1 | sed 's/.*"\(https[^"]*\)".*/\1/')
  [ -n "$LATEST_URL" ] && curl -sL -o "$DEST/$(basename "$LATEST_URL")" "$LATEST_URL"
fi

JAR=$(ls "$DEST"/com.example.e2studio.agent.bridge_*.jar 2>/dev/null | sort -V | tail -1)
[ -z "$JAR" ] && echo "ERROR: failed to download JAR from GitHub Release" && exit 1
```

**3. Build from source** — only if `--rebuild` passed or nothing above succeeded. Requires JDK ≥ 17 and Maven; invoke `scripts/build_bridge.sh` and pass `-Djdk.xml.maxGeneralEntitySizeLimit=0` etc. (see that script).

Record resolved JAR path as `$JAR` and its version qualifier (from filename `com.example.e2studio.agent.bridge_<VERSION>.jar`) as `$VER`.

### Step 3 — Close e2studio

Ask the user to close e2studio if it's running, then verify:
```bash
tasklist //FI "IMAGENAME eq e2studio*" | grep -q e2studio.exe && taskkill //IM e2studio.exe //F
# wait 3s, confirm gone
```

Remove the workspace lock if present:
```bash
rm -f "$HOME/workspace_e2studio/.metadata/.lock"
```

### Step 4 — Install the bundle

```bash
# Remove any previous version (clean slate)
rm -f "$e2studio_ECLIPSE/plugins/com.example.e2studio.agent.bridge_"*.jar
rm -f "$e2studio_ECLIPSE/dropins/com.example.e2studio.agent.bridge_"*.jar

# Copy the JAR into plugins/ AND dropins/ (belt-and-braces)
mkdir -p "$e2studio_ECLIPSE/dropins"
cp "$JAR" "$e2studio_ECLIPSE/plugins/"
cp "$JAR" "$e2studio_ECLIPSE/dropins/"

# Register in bundles.info so OSGi simpleconfigurator starts it
BI="$e2studio_ECLIPSE/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
# Strip previous entry
grep -v "com\.example\.e2studio\.agent\.bridge" "$BI" > "$BI.new"
mv "$BI.new" "$BI"
# Append new entry with the exact version from the JAR filename
echo "com.example.e2studio.agent.bridge,${VER},plugins/com.example.e2studio.agent.bridge_${VER}.jar,4,true" >> "$BI"
```

### Step 5 — Start e2studio with `-clean`

`-clean` forces OSGi to rescan bundles (required after installing a new plugin):
```bash
powershell -NoProfile -Command \
  "Start-Process -FilePath '$e2studio_ECLIPSE\e2studio.exe' -ArgumentList '-clean' -WorkingDirectory '$e2studio_ECLIPSE'"
```

### Step 6 — Wait for the bridge to come up

Poll up to 120 seconds for:
```bash
HOME_DIR="${USERPROFILE:-$HOME}"
# Prefer the discovery file the bridge plug-in publishes (v0.1.4+)
DISCOVERY="$HOME_DIR/.e2studio-mcp/bridge.json"
TOKEN_PATH="$HOME_DIR/workspace_e2studio/.metadata/.plugins/com.example.e2studio.agent.bridge/token"
while [ ! -f "$DISCOVERY" ] && [ ! -f "$TOKEN_PATH" ] && [ "$((SECONDS))" -lt 120 ]; do sleep 2; done
```

### Step 7 — Fix token ACL

The plugin's TokenStore tightens ACL to owner-only but the resulting ACL blocks even the owner's bash from reading. Only needed if you fell back to the workspace-local path (the discovery file has normal ACL):
```bash
# USERPROFILE/USERNAME are Windows-native; $USER can be empty in MinGW bash.
USR="${USERNAME:-${USER:-$(whoami)}}"
icacls "$(cygpath -w "$TOKEN_PATH")" //grant "$USR:R"
```

### Step 8 — Verify

```bash
# Prefer discovery file (no ACL hassle); fall back to workspace token
if [ -f "$DISCOVERY" ]; then
  TOKEN=$(python -c "import json; print(json.load(open(r'$DISCOVERY'))['token'])")
  BASE=$(python -c "import json; print(json.load(open(r'$DISCOVERY'))['url'])")
else
  TOKEN=$(cat "$TOKEN_PATH" | tr -d '\r\n')
  BASE="http://127.0.0.1:39232"
fi
curl -fs -H "Authorization: Bearer $TOKEN" "$BASE/health" | jq .
```
Expect `ok: true` and `data.workbenchRunning: true`.

### Step 9 — Report

Tell the user:
- Install path
- Plugin version installed
- How to call `/s32 status` to verify any time
- Note: e2studio from now on always starts with the bridge. Plain restarts (no `-clean`) are fine after this first install.

## Failure handling

If any step fails, roll back safely:
- **Step 4 failure (file locked)** → e2studio still running; kill and retry.
- **Step 6 timeout** → token file never appeared. Check `$HOME/workspace_e2studio/.metadata/.log` for "agent" or "bridge". Most common cause: `bundles.info` version mismatch with JAR MANIFEST `Bundle-Version`. Verify both match the qualifier in the filename.
- **Step 8 401 Unauthorized** → token read is picking up a previous token; make sure no stale `TOKEN` env var.
- **Step 8 connection refused** → plugin not started. Re-check Eclipse log for our bundle ID `com.example.e2studio.agent.bridge`.

Print clear diagnostic output at every failure. Do not silently swallow errors.

## Post-install

Tell the user about the companion commands:
- `/s32 status` — check health any time
- `/s32 menu "X"` — find any UI path
- `/s32 view "X"` — open a view
- Skill `e2studio-menu-lookup` auto-activates when they ask e2studio UI questions
