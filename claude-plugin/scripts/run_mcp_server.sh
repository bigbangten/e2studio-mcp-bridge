#!/usr/bin/env bash
# Launcher the Claude Code plugin uses to start the MCP server (stdio).
# Auto-resolves Python 3.11+, installs the mcp-server package if missing, passes the bridge token via env.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MCP_SERVER_DIR="${REPO_ROOT}/mcp-server"

# Find a Python 3.11+ interpreter (mcp package requires 3.10+)
find_python() {
  for candidate in python3.12 python3.11 python3.10 python3 python; do
    if command -v "$candidate" >/dev/null 2>&1; then
      local ver
      ver=$("$candidate" -c 'import sys; print(sys.version_info[0]*100+sys.version_info[1])' 2>/dev/null || echo 0)
      if [ "$ver" -ge 310 ]; then
        echo "$candidate"
        return 0
      fi
    fi
  done
  # Windows default install
  if [ -x "/c/Users/$USER/AppData/Local/Programs/Python/Python311/python.exe" ]; then
    echo "/c/Users/$USER/AppData/Local/Programs/Python/Python311/python.exe"
    return 0
  fi
  if [ -x "/c/Users/$USER/AppData/Local/Programs/Python/Python312/python.exe" ]; then
    echo "/c/Users/$USER/AppData/Local/Programs/Python/Python312/python.exe"
    return 0
  fi
  return 1
}

PYTHON=$(find_python) || {
  echo "ERROR: Python 3.10+ required (mcp package). Install with: winget install Python.Python.3.11" >&2
  exit 1
}

# Ensure mcp-server package is importable (editable install is idempotent)
if ! "$PYTHON" -c "import e2studio_mcp_server" 2>/dev/null; then
  "$PYTHON" -m pip install --user --quiet -e "${MCP_SERVER_DIR}" >&2 || {
    echo "ERROR: failed to install e2studio-mcp-server from ${MCP_SERVER_DIR}" >&2
    exit 1
  }
fi

# Resolve bridge token from workspace metadata
TOKEN_PATH="${E2STUDIO_WORKSPACE:-$HOME/workspace_e2studio}/.metadata/.plugins/com.example.e2studio.agent.bridge/token"
if [ ! -f "$TOKEN_PATH" ]; then
  # Windows default-user fallback
  CANDIDATE="/c/Users/$USER/workspace_e2studio/.metadata/.plugins/com.example.e2studio.agent.bridge/token"
  [ -f "$CANDIDATE" ] && TOKEN_PATH="$CANDIDATE"
fi

if [ -f "$TOKEN_PATH" ]; then
  # Fix ACL if cat fails (idempotent)
  if ! cat "$TOKEN_PATH" >/dev/null 2>&1; then
    if command -v icacls >/dev/null 2>&1; then
      icacls "$(cygpath -w "$TOKEN_PATH" 2>/dev/null || echo "$TOKEN_PATH")" //grant "$USER:R" >/dev/null 2>&1 || true
    fi
  fi
  export E2STUDIO_BRIDGE_TOKEN="$(cat "$TOKEN_PATH" | tr -d '\r\n')"
fi

export E2STUDIO_BRIDGE_URL="${E2STUDIO_BRIDGE_URL:-http://127.0.0.1:39232}"

exec "$PYTHON" -m e2studio_mcp_server.server
