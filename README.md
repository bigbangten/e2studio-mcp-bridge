# e2studio MCP Bridge

Live introspection bridge for **Renesas e² studio** — gives Claude/Codex accurate menu paths, command IDs, workbench state, build/problem info, launch config analysis, dialog inspection, **and debug runtime state** (halt location, registers, memory, console output).

**Fork of [`s32ds-mcp-bridge`](https://github.com/bigbangten/s32ds-mcp-bridge)** — the two differ only in vendor bundle filters (`com.renesas.*` vs `com.nxp.*`), install paths, and default port (`39232` vs `39231`, so both can run side-by-side).

## What's here

- `eclipse-bridge/` — Eclipse plug-in (OSGi bundle `com.example.e2studio.agent.bridge`) that exposes an HTTP API inside the running e² studio process at `http://127.0.0.1:39232`. Built with Tycho.
- `mcp-server/` — Python FastMCP wrapper (`e2studio_mcp_server`) turning every bridge endpoint into an MCP tool.
- `claude-plugin/` — Claude Code plugin directory (`/e2:ask`, `/e2:menu`, `/e2:status`, `/e2:setup`).

## Install

Same flow as the S32DS parent project — see [QUICKSTART.md](QUICKSTART.md). The installer detects e² studio installs under `C:\Renesas\RA\e2studio_*\eclipse`, `C:\Renesas\RX\e2studio_*\eclipse`, `C:\Renesas\RL78\e2studio_*\eclipse`, or the legacy `C:\Renesas\e2_studio\eclipse`. Multiple co-installed versions are supported (the installer asks which to target).

## Phase 4 debug runtime endpoints

- `GET /debug/status` — quick halted/running summary across launches
- `GET /debug/location` — current halt location (function, line, file) for first suspended thread
- `GET /debug/registers` — register groups + values on suspended top frame
- `GET /debug/memory?addr=0x...&length=N` — memory hex dump (capped 4096 bytes, read-only)
- `GET /debug/breakpoints`, `/debug/stackframes`, `/debug/variables` (unchanged from Phase 1)
- `GET /console/tail?name=...&lines=N` — debugger/build/UART console tail

## License

MIT — see [LICENSE](LICENSE).
