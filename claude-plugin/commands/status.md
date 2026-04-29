---
description: Show e2 studio bridge health + current workbench state in one glance.
---

Quick status check for the e2studio MCP bridge. Use when the user asks "is it working?", "connected?", or wants to see the current IDE state at a glance.

## Procedure

```bash
# Resolve bridge URL + token. Priority:
#   1. ~/.e2studio-mcp/bridge.json (discovery file, v0.1.4+)
#   2. $E2STUDIO_WORKSPACE/.metadata/.plugins/.../token (env-specified workspace)
#   3. ~/workspace_e2studio/.metadata/.plugins/.../token (Windows default)
# We use USERPROFILE on Windows as canonical home — $USER is empty in some MinGW
# bash shells, so don't rely on it.

HOME_DIR="${USERPROFILE:-$HOME}"
DISCOVERY="$HOME_DIR/.e2studio-mcp/bridge.json"
TOKEN=""
BASE=""

if [ -f "$DISCOVERY" ]; then
  BASE=$(python -c "import json,sys; print(json.load(open(r'$DISCOVERY'))['url'])")
  TOKEN=$(python -c "import json,sys; print(json.load(open(r'$DISCOVERY'))['token'])")
else
  # Fallback: workspace-local token file (for pre-v0.1.4 bridges)
  WS="${E2STUDIO_WORKSPACE:-$HOME_DIR/workspace_e2studio}"
  TOKEN_PATH="$WS/.metadata/.plugins/com.example.e2studio.agent.bridge/token"
  if [ ! -f "$TOKEN_PATH" ]; then
    echo "Bridge not reachable:"
    echo "  - No discovery file at $DISCOVERY"
    echo "  - No workspace token at $TOKEN_PATH"
    echo "Is e2studio running? If yes but still failing, run /e2:setup."
    exit 1
  fi
  # Windows ACL may block even owner; grant read on-demand
  if ! cat "$TOKEN_PATH" >/dev/null 2>&1; then
    USR="${USERNAME:-${USER:-$(whoami)}}"
    icacls "$(cygpath -w "$TOKEN_PATH" 2>/dev/null || echo "$TOKEN_PATH")" //grant "$USR:R" >/dev/null 2>&1
  fi
  TOKEN=$(cat "$TOKEN_PATH" | tr -d '\r\n')
  BASE="http://127.0.0.1:39232"
fi

AUTH="Authorization: Bearer $TOKEN"

# Health
HEALTH=$(curl -fs -H "$AUTH" "$BASE/health" 2>&1) || { echo "Bridge not responding at $BASE: $HEALTH"; exit 1; }
echo "$HEALTH" | jq '.data | {product: .e2studioProduct, eclipseVer: .eclipseVersion, pid: .pid, bridgeVersion, workspace}'

# State summary
curl -fs -H "$AUTH" "$BASE/state" | jq '{
  perspective: .data.activePerspective.name,
  editor: .data.activeEditor.input.path,
  projects: [.data.workspaceProjects[] | select(.accessible) | .name],
  openViews: [.data.openViews[].partName],
  dirty: .data.dirtyEditors
}'

# Problems count by severity
curl -fs -H "$AUTH" "$BASE/markers/problems" | jq '.data.bySeverity'
```

Report:
- Bridge reachable (OK or FAIL) and `bridgeVersion` (lets the user see if their e2studio-side plug-in is behind the latest published)
- e2studio product name + Eclipse version
- Active perspective + editor
- Open projects
- Problems counts by severity

If reachable but state looks empty (no active editor, no projects), note it — could indicate a fresh workspace or the user is between operations.
