# Installation — step by step

This doc is **written for an AI agent to execute autonomously**, but humans can follow it too. Every step has a concrete command and a verification. Don't skip the verifications.

## Scope

- **OS**: Windows 10/11 (Linux/macOS untested).
- **Target**: Renesas e² studio 2025-04 or newer (Eclipse 4.36 / 2024-10 base, Java 21 runtime). Older releases (Eclipse 4.20+) likely work but are untested.
- **For Claude Code users**: prefer running `/e2:setup` — that's the automated version of this file.

## Prerequisites

| Requirement | Required for | Check |
|---|---|---|
| e2 studio installed | everything | `ls "C:\Renesas\e2_studio\eclipse\e2studio.exe"` |
| Python 3.10+ | MCP server | `python --version` (must be ≥3.10) |
| JDK 17+ | building from source only | `java -version` |
| Maven 3.9+ | building from source only | `mvn -v` |

### Install missing Python (Windows)

```bash
winget install Python.Python.3.11
```

### Install missing JDK (Windows)

```bash
winget install EclipseAdoptium.Temurin.21.JDK
```

### Install missing Maven (Windows)

Maven isn't on winget. Download manually:

```bash
mkdir -p "$HOME/maven"
curl -sL -o "$HOME/maven/maven.zip" https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip
python -c "import zipfile; zipfile.ZipFile(r'$HOME/maven/maven.zip').extractall(r'$HOME/maven')"
export PATH="$HOME/maven/apache-maven-3.9.9/bin:$PATH"
mvn --version
```

## 1. Obtain the plug-in JAR

### Option 1a — Download a prebuilt release (recommended)

```bash
DEST="$HOME/.cache/e2studio-mcp-bridge"
mkdir -p "$DEST"
gh release download --repo bigbangten/e2studio-mcp-bridge \
  --pattern 'com.example.e2studio.agent.bridge_*.jar' \
  --dir "$DEST" --clobber
JAR=$(ls "$DEST"/com.example.e2studio.agent.bridge_*.jar | sort -V | tail -1)
```

### Option 1b — Build from source

```bash
export JAVA_HOME=$(cygpath -u "$(dirname "$(dirname "$(where java | head -1)")")")  # or set manually
export MAVEN_OPTS="-Djdk.xml.maxGeneralEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0 -Djdk.xml.entityExpansionLimit=0 -Djdk.xml.elementAttributeLimit=0 -Djdk.xml.maxOccurLimit=0 -Djdk.xml.maxXMLNameLimit=0"
mvn -f eclipse-bridge/releng/pom.xml clean verify
JAR=$(ls eclipse-bridge/releng/com.example.e2studio.agent.repository/target/repository/plugins/com.example.e2studio.agent.bridge_*.jar | head -1)
```

**Why all those `MAVEN_OPTS`?** JDK 17+ caps XML entity sizes at 100,000 chars by default; Eclipse's p2 metadata exceeds that. Disabling the caps is safe for local builds.

Extract the version qualifier:

```bash
VER=$(basename "$JAR" | sed 's/com.example.e2studio.agent.bridge_//; s/.jar$//')
echo "Building version: $VER"
```

## 2. Close e2studio

```bash
tasklist //FI "IMAGENAME eq e2studio*" | grep -q e2studio.exe && taskkill //IM e2studio.exe //F
# Remove stale workspace lock if any
rm -f "$HOME/workspace_e2studio/.metadata/.lock"
```

**Verify**: `tasklist //FI "IMAGENAME eq e2studio*"` shows no running e2studio.exe.

## 3. Install the bundle

```bash
e2studio_ECLIPSE="C:/Renesas/e2studio.3.5/eclipse"   # adjust if different
BI="$e2studio_ECLIPSE/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"

# Remove previous version (if any)
rm -f "$e2studio_ECLIPSE/plugins/com.example.e2studio.agent.bridge_"*.jar
rm -f "$e2studio_ECLIPSE/dropins/com.example.e2studio.agent.bridge_"*.jar

# Copy the new JAR to both plugins/ AND dropins/ (belt-and-braces)
mkdir -p "$e2studio_ECLIPSE/dropins"
cp "$JAR" "$e2studio_ECLIPSE/plugins/"
cp "$JAR" "$e2studio_ECLIPSE/dropins/"

# Strip any previous entry from bundles.info
grep -v "com\.example\.e2studio\.agent\.bridge" "$BI" > "$BI.new" && mv "$BI.new" "$BI"

# Append fresh entry — version MUST match the JAR filename qualifier exactly
echo "com.example.e2studio.agent.bridge,${VER},plugins/com.example.e2studio.agent.bridge_${VER}.jar,4,true" >> "$BI"
```

**Verify**:

```bash
tail -1 "$BI"
# Expect: com.example.e2studio.agent.bridge,<VER>,plugins/com.example.e2studio.agent.bridge_<VER>.jar,4,true
ls "$e2studio_ECLIPSE/plugins/com.example.e2studio.agent.bridge_"*.jar
```

## 4. Start e2studio with `-clean`

`-clean` is required the first time after installing a new bundle so OSGi rescans. Subsequent launches don't need it.

```bash
powershell -NoProfile -Command "Start-Process -FilePath '$e2studio_ECLIPSE/e2studio.exe' -ArgumentList '-clean' -WorkingDirectory '$e2studio_ECLIPSE'"
```

## 5. Wait for bridge to come up

```bash
TOKEN_PATH="$HOME/workspace_e2studio/.metadata/.plugins/com.example.e2studio.agent.bridge/token"
echo -n "Waiting for bridge..."
for i in $(seq 1 60); do
  [ -f "$TOKEN_PATH" ] && { echo " ready ($((i*2))s)"; break; }
  sleep 2; echo -n .
done
[ -f "$TOKEN_PATH" ] || { echo; echo "TIMEOUT"; exit 1; }
```

## 6. Fix ACL (one-time, per workspace)

The plug-in's `TokenStore` locks the token file to owner-only, but Windows + bash combinations fail even for the owner. Re-grant read:

```bash
icacls "$(cygpath -w "$TOKEN_PATH")" //grant "$USER:R"
```

**Verify**:

```bash
TOKEN=$(cat "$TOKEN_PATH" | tr -d '\r\n')
[ ${#TOKEN} -gt 20 ] && echo "Token read OK (${#TOKEN} chars)" || { echo "FAIL"; exit 1; }
```

## 7. Verify the bridge responds

```bash
curl -fs -H "Authorization: Bearer $TOKEN" http://127.0.0.1:39232/health | python -m json.tool
```

**Expected output includes**:
- `"ok": true`
- `"workbenchRunning": true`
- `"e2studioProduct": "e2 studio (Renesas) for S32 Platform"`

If you see `ok: false` or connection refused, consult **Troubleshooting** below.

## 8. Install the Python MCP server

```bash
pip install -e ./mcp-server
# Verify
python -c "import e2studio_mcp_server; print('MCP server importable')"
```

## 9. Wire your AI client

### Claude Code (automatic via plugin)

The plugin's `plugin.json` already declares the MCP server. Just reload:

```
/plugin install e2studio-mcp    # or /plugin reload if already installed
```

Confirm with `/e2:status`.

### Codex CLI

Add to `~/.codex/config.toml`:

```toml
[mcp_servers.e2studio]
command = "python"   # or full path to 3.10+ Python
args = ["-m", "e2studio_mcp_server.server"]
startup_timeout_sec = 20
tool_timeout_sec = 120

[mcp_servers.e2studio.env]
E2STUDIO_BRIDGE_URL = "http://127.0.0.1:39232"
E2STUDIO_BRIDGE_TOKEN = "<paste_token_from_step_6>"
```

Test: `codex exec "use the e2studio tool to call fetch_health"`.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Step 5 times out | Plugin not loading | Look at `$HOME/workspace_e2studio/.metadata/.log` for any line mentioning `com.example.e2studio.agent.bridge`. If silent, check `bundles.info` version matches JAR filename qualifier exactly. |
| Step 7 returns HTTP 401 | Wrong token | Make sure you re-read the token after ACL fix. If test still fails, delete token file and restart e2studio — it'll regenerate. |
| Step 7 returns connection refused | Bundle not started | Verify the JAR exists in `plugins/` AND an entry in `bundles.info`. If both present, relaunch e2studio with `-clean -console` to see OSGi startup messages. |
| `taskkill` says "not found" | Already closed | Fine, continue. |
| Tycho build fails with "class file version 61.0" | JDK too old | Tycho 4.x needs JDK 17+. Install Temurin 21: `winget install EclipseAdoptium.Temurin.21.JDK`. |
| Tycho build fails with "maxGeneralEntitySizeLimit" | JDK 17+ XML hardening | Export all the `MAVEN_OPTS` listed in step 1b. |

## Uninstall

```bash
taskkill //IM e2studio.exe //F 2>/dev/null
rm -f "$e2studio_ECLIPSE/plugins/com.example.e2studio.agent.bridge_"*.jar
rm -f "$e2studio_ECLIPSE/dropins/com.example.e2studio.agent.bridge_"*.jar
BI="$e2studio_ECLIPSE/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
grep -v "com\.example\.e2studio\.agent\.bridge" "$BI" > "$BI.new" && mv "$BI.new" "$BI"
# Start e2studio normally next time (no -clean required)
```
