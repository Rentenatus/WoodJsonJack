# WoodJsonJack
Wood Json Jack is a developer‑friendly JSON editor designed for everyone who has ever gotten lost in a forest of nested objects.

---

## Two Edit Modes: Soft and Hard

Most model-driven editors — Eclipse EMF, for example — know only one mode: every change is
validated against the model immediately, and an invalid state is rejected before it exists.
Safe, but narrow: you can never sketch a structure first and bind it to the model later.

Wood Json Jack offers both worlds on the same tree:

- **Soft mode** — the free sketch. Edit names and values as you like; the background parser
  follows asynchronously and marks what does not fit the model (red node labels, parse
  problems in the fast log). The change stays, you keep working.
- **Hard mode** — the strict counterpart, validated in real time rather than later: every
  change is parsed synchronously with the command. Names edit through comboboxes of the
  permissible fields of the owning type, so the model guides the edit instead of judging
  afterwards. A rename the parser refuses is rolled back — never silently, but with an
  entry in the fast log and the fast log message window. Duplicate field names in the
  parent object are checked and rolled back alike.

Switch between the modes at any time; the hard mode accepts only a fully verified tree.

---

## Wood Json Jack vs. the Eclipse EMF Editor

The generic Eclipse EMF tree editor is the classic reference point for model-driven editing:
a generated editor per metamodel, strictly model-driven, every step dictated by Ecore.
Wood Json Jack takes the opposite route for JSON — one generic editor, fed by
self-describing model descriptions, with both edit modes on the same tree.

| Aspect | Eclipse EMF editor | Wood Json Jack |
|---|---|---|
| Metamodel | Ecore metamodel; an editor is generated per model | Self-describing JSON description files; one generic editor for every model |
| Edit principle | One mode only: the model dictates every step, an invalid state is rejected before it exists | Two modes on the same tree: the free soft sketch and the strictly validated hard mode |
| Sketching | Not possible — the metamodel must exist first and in full | Soft mode: edit names, values and structure freely; the background parser follows asynchronously and marks what does not fit |
| Hard validation | Largely by construction (features and child types are filtered), the rest via live diagnostics | Synchronous parse with every command; a refused rename rolls back transactionally |
| Renaming | Feature names are part of the metamodel, not of the instance — no rename in the tree | Renames re-bind the instance to another declared field; in hard mode a combobox offers only the permissible names, free text in soft mode |
| Adding elements | Filtered child menu, only permissible containment types | Same source of truth in hard mode (`HardEditAdvisor`), free in soft mode; the Object pane shows missing declared fields as empty rows — an edit creates the child |
| Duplicate fields | Impossible by construction | Allowed transiently in soft mode; in hard mode the rename guard checks the cardinality of the parent and rolls a duplicate back |
| Error feedback | Problems view and validation decorators on the nodes | Red node labels, parse problems in the fast log, and the fast log message window opens on every refused hard change — a refusal is never silent |
| Undo / Redo | Command stack of the editing domain | Undoable commands as well — including the rollback of refused hard renames and re-binding primitive value edits in both modes |
| References | EReferences with cross-document proxies resolved by the resource set | Wood links (`_woodLink`): loss-free import and export, reference stubs, deleted but referenced objects stay selectable in the linking set |
| UI toolkit | SWT/JFace: tree plus property sheet | Swing: tree plus the Attributes and Object panes — sandbox views that show model side and instance side of the same node, with comboboxes and detail dialogs |
| Serialization | XMI (or a generated JSON format) | JSON, loss-free round trip including links and annotations |

Both worlds share the same goal — an instance that provably fits its model. The difference
is the road: EMF insists on it from the first click, Wood Json Jack lets you sketch first
and verifies on demand — and is just as strict once you switch to hard mode.

---

## Support 🐾
If you like my projects, consider [supporting my work](https://github.com/sponsors/Rentenatus) (and feeding Mistral 🐱)! 

---
