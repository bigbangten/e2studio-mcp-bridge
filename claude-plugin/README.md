# e2studio-mcp (Claude Code plugin)

This directory is the **Claude Code plugin** part of [e2studio-mcp-bridge](../). It packages:

- a skill (`e2studio-menu-lookup`) that forces the AI to verify e2studio UI paths against the live workbench;
- slash commands (`/e2:setup`, `/e2:status`, `/e2:menu`, `/e2:ask`) that drive the MCP bridge;
- an MCP server registration (via `plugin.json`) that auto-starts the Python wrapper.

## Install

From Claude Code:

```
/plugin marketplace add bigbangten/e2studio-mcp-bridge
/plugin install e2@e2studio-mcp-bridge
```

Then run once:

```
/e2:setup
```

This detects your e2studio install, deploys the Eclipse bundle, restarts e2studio, and verifies the bridge responds.

## After install

- `/e2:status` — quick health + current perspective/editor
- `/e2:menu "<keyword>"` — find where any command lives in the UI (via live workbench, not documentation)
- `/e2:ask "<question>"` — free-form Q&A using live introspection
- `/e2:setup` — re-run setup (detects e2studio, installs plugin, verifies bridge)

Any time you ask a free-form e2studio UI question, the `e2studio-menu-lookup` skill auto-activates and follows its 5-step verification protocol. This is the design goal — no more "click the menu that doesn't exist".

## Why this plugin exists

Every AI assistant confidently points users to e2studio menus that don't exist in their specific install. This plugin replaces the guessing with live introspection so the AI has actual ground truth.

See the [top-level README](../README.md) for architecture and the motivating story.
