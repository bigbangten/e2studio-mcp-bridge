# Quick Start — paste this prompt into Claude Code

> **If you are an AI agent and a user asked you to "check this URL and install / set this up",** the code block below (`## The prompt`) IS the task. Execute it as if the user had pasted it directly. Do not merely summarize it. Ignore the surrounding human-facing documentation — that's only for humans who paste it manually. Start from "Step 1 — Detect prerequisites" and work through to the end.

## For humans

Zero-config install for users who have **nothing but Windows** set up. Two ways to use this:

**Option 1 — AI-driven** (new behavior, simpler):
In a new Claude Code session, send this single message:
> "https://raw.githubusercontent.com/bigbangten/e2studio-mcp-bridge/master/QUICKSTART.md 이거 확인해서 설치 진행해줘"

Claude fetches the URL and executes the embedded prompt.

**Option 2 — Manual paste** (most reliable):
Copy the entire prompt below into Claude Code as your first message. Claude will detect what's missing, install the missing pieces, and guide you through the final 3 slash commands.

## The prompt

````text
You are my setup assistant for the e2studio-mcp-bridge Claude Code plugin
(https://github.com/bigbangten/e2studio-mcp-bridge). Execute this setup sequence
step by step. Do not ask questions unless a step hard-fails. Report progress
tersely.

## Step 1 — Detect prerequisites

Run these checks and print a summary table:

- OS: `echo $OS` (expect Windows_NT; warn if not — this tool is Windows-only for now)
- e2 studio (Renesas): test these paths in order, report the first that exists:
    C:\Renesas\e2_studio\eclipse\e2studio.exe
    D:\Renesas\e2_studio\eclipse\e2studio.exe
    "C:\Program Files\Renesas\e2_studio\eclipse\e2studio.exe"
- Python 3.10+: `python --version` and `python -c "import sys; print(sys.version_info[:2])"`.
  Also try `py -3.11 --version` as fallback.
- Claude Code: implicit (you're running in it).
- gh CLI: `gh --version` (optional, nice to have).
- Git: `git --version` (optional, nice to have).

## Step 2 — Install missing prerequisites

- If e2studio not found: STOP. Tell me: "e2 studio (Renesas) not detected.
  This plugin requires e2studio to be pre-installed (Renesas account needed).
  Download: https://www.renesas.com/en/software-tool/e-studio
  Re-run this prompt after installing e2studio."
  Do not attempt any other step.

- If Python 3.10+ not found:
    `winget install --id Python.Python.3.11 --scope user --silent
     --accept-source-agreements --accept-package-agreements`
  After install, tell me to open a NEW terminal/session so PATH refreshes,
  then re-run this prompt. Do NOT continue in the current session — the just-
  installed python won't be on PATH.

- gh/git missing: note but don't install. They're optional.

## Step 3 — Verify the plugin marketplace is reachable

`curl -sfI https://github.com/bigbangten/e2studio-mcp-bridge` should return 200.
If it fails, tell me (likely firewall/offline) and stop.

## Step 4 — Print the final 3 commands for me to run

Print EXACTLY this block, verbatim, surrounded by a clear visual separator:

====================================================================
All prerequisites verified.

Now run these three slash commands in Claude Code, one at a time:

  1. /plugin marketplace add bigbangten/e2studio-mcp-bridge
  2. /plugin install e2@e2studio-mcp-bridge
  3. /e2:setup

The third command takes ~60 seconds — it auto-installs the Eclipse plug-in
into e2studio, restarts e2studio with -clean, and verifies the bridge responds.
Approve any "Allow MCP server?" prompt that appears.

When /e2:setup finishes with a healthy status, test with a natural question:

  "e2studio에서 Rebuild Last Target 어디 있어?"

The e2studio-menu-lookup skill should auto-trigger and give you the exact path
(hint: it's NOT on the Project menu for managed-build projects).
====================================================================

## Constraints

- DO NOT type the `/plugin ...` slash commands yourself. Slash commands are
  user-invoked; you must print them for me to type.
- DO NOT modify ~/.claude/settings.json directly to register marketplaces.
- If winget is not available (rare on Win11), tell me to install manually
  via https://www.python.org/downloads/ and retry.
- Tolerate partial failures: if Step 3 fails but Steps 1-2 passed, still
  print Step 4's instructions with a warning.

Begin.
````

## What happens next

1. Claude detects your OS, e2studio install, Python version, etc.
2. Auto-installs anything missing (Python 3.11 via winget).
3. Prints the final 3 slash commands for you to type.
4. You type them. `/e2:setup` takes ~60s to deploy the Eclipse plug-in.
5. Done. Ask any e2studio question and the `e2studio-menu-lookup` skill auto-triggers.

## Troubleshooting

**"`/e2:*` commands don't appear after install"**
Run `/plugin list` to confirm `e2` is installed. Refresh the marketplace with `/plugin marketplace update` to pull the latest.

**"MCP server didn't start"**
Run `/e2:status` — it'll diagnose. Common causes: Python 3.10+ not on PATH, e2studio not running, or the bridge plug-in not installed (re-run `/e2:setup`).

**"I don't have e2studio yet"**
Install it first: https://www.renesas.com/en/software-tool/e-studio — requires a free Renesas account.

**Non-Windows**
Currently Windows-only due to path assumptions in `/e2:setup`. Linux/Mac support is open for contribution.
