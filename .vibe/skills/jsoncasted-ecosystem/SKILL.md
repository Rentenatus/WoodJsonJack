---
name: jsoncasted-ecosystem
description: Load this skill when working in the jsonCasted ecosystem (jsonCasted, jsonCasted_edit, or WoodJsonJack projects). Contains the full package structure, dependency graph, cross-project imports, and quick-load commands for the MCP server.
---

# jsonCasted Ecosystem Structure

Three Java projects by Janusch Rentenatus that build on each other:

```
jsonCasted (base library)
    │
    ├──→ jsonCasted_edit (editor framework, depends on jsonCasted)
    │        │
    │        └──→ WoodJsonJack (Swing UI, depends on both)
```

Shared package root: `de.jare`

## Quick-load commands (MCP server)

```
load_java_project({ source: "D:\\git_jsonCasted",      alias: "jsonCasted" })
load_java_project({ source: "D:\\git_jsonCasted_edit",  alias: "jsonCasted_edit" })
load_java_project({ source: "D:\\git_WoodJsonJack",     alias: "WoodJsonJack" })
```

IDE workspace copies (NetBeans/Ant) are under `D:\sdk_workspaces\` with
subdirectories `jsonCasted/`, `jsonCasted_edit/`, `WoodJsonJack/`.

## Full structure reference

See `project-structure.md` in this skill directory for the complete package tree,
file lists, build configs, cross-project import analysis, assets, and the
architecture summary. Read it with `read_file` when you need details.
