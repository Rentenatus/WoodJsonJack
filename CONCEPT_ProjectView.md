# Project View Concept (WoodJsonJack)

## Purpose and scope

This concept specifies the **project**: a persistent, virtual working set that lives in a project
root folder, and the **project sidebar** — a third panel far left in the Wood editor that renders
that working set as a tree. It is the IDE-facing layer of the editor: it locates the files the
editor is good at (JSON bundles) and the files around them (sources, tests, assets, generated
code) and makes them navigable without leaving the Wood window.

It builds on what exists and is live-tested today:

| Foundation | State | Where |
|-----------|-------|-------|
| `JsonBundle` — bundle-level resource registry, one shared instance per file, `B == B'` | built | `de.jare.jsoncasted.lang` (doc: `JSON_BUNDLE.md`) |
| `EditBundle` — editor-side bundle holder, parse feedback, per-tab lifecycle | built | `de.jare.jsoncasted.editor.core` (jsonCasted_edit) |
| Open/Close/Show Bundle Resource menu wiring | built | `JackMainActions` / `WoodWindow` (WoodJsonJack) |
| Main menu split File/Bundle/Project (LaF icons, icon-only project bar buttons, New Project = empty `.wood/project.json`) | built | `JackMainMenu` / `UiIcons` / `ProjectSidebar` (WoodJsonJack) |
| Project history as `user-home/.wood/projects.json` (MRU list, most recent first, dedup per folder, cap 10) + "Open Recent" sub menu | built | `ProjectHistory` / `SettingsService` / `JackMainMenu` (WoodJsonJack) |
| Startup reopen: the history's front project opens itself, a vanished folder is skipped silently | built | `WoodWindow.reopenLastProject` (WoodJsonJack) |
| Add Folder menu: partition roles (src/test/assets/json/gen/target) create folder + partition entry, "bundle" creates an empty bundle in the json partition; duplicates and unnames refused without side effects | built | `JackMainActions.addProjectFolderAt/addProjectBundleAt` / `JackMainMenu` (WoodJsonJack) |
| Tree popups: the root offers Add Folder + whole-project refresh, folder nodes get type-appropriate New items (json: JSON file/nested bundle, src/test: Java file, assets: file, bundle: declared JSON resource), read-only mounts get none; the expansion itself loads (treeWillExpand -> lazyLoad, the placeholder never survives an expand) | built | `ProjectSidebar.appendNewItems` / `lazyLoad` / `JackMainActions.addProjectBundleIn/addFileAt/addSubfolderAt/addBundleResourceAt` (WoodJsonJack) |
| Bundle popups: "Open bundle document" (the descriptor in a tab, read-only on an isolated mount) instead of the project document; every contained JSON file offers "Set as main file..." behind a guard dialog naming the current main; the swap joins the file into the declared list, the bundle node re-reads its descriptor on reset (the new main leads) | built | `ProjectSidebar.maybePopup` / `JackMainActions.setBundleMain/setBundleMainAt` / `ProjectTreeNode.reset` (WoodJsonJack) |
| Broken bundle = parse error of the sidebar: a missing main file marks the node [!!] in the theme's error tone (same fore.error/fore.warning keys as the parsed trees), the tooltip names the file and the way out; the state is computed per paint and heals as soon as the file returns | built | `ProjectTreeNode.getError` / `ProjectTreeCellRenderer` (WoodJsonJack) |
| `ProjektEntry` / `ProjectSettings` / `knownProjects` — the older settings-side project concept; its name is now free of the bundle family | built | `de.jare.tree.settings.project` (unchanged, kept as is) |
| Cross-file link resolution (`WoodProxyResolver`, `_woodProviders` / `_woodLink`) | built | jsonCasted |

**This concept adds a new layer above bundles.** It changes neither the diff engine, nor the node
models, nor `JsonBundle` itself. The bundle remains what it is — the resource unit. The project is
*where bundles and other files are found, grouped and mounted*.

Naming decision (binding, already executed): the resource unit is called **Bundle** (`JsonBundle`,
`EditBundle`); the desktop-side working set is called **Project**. These two words never overlap:
a project *contains* bundles; a bundle never knows its project.

---

## 1. The project anchor: `.wood/project.json`

A project is a folder that contains a project document. Convention:

```
D:/wood/myproject/
└── .wood/
    └── project.json        ← the project document (JSON — of course)
```

Rules:

- The folder containing `.wood/project.json` is the **project root**. All relative paths in the
  document resolve from there.
- The project document is itself plain JSON and therefore a first-class editing candidate of this
  editor (open in a tab, edit, save — the same round trip as any other JSON file). No dedicated
  dialog is required to bootstrap; a dialog can come later as a comfort feature on top.
- Existence of `.wood/project.json` is the only marker. No registration in `knownProjects` is
  required to open one (the settings-side project list may, later, remember recently opened
  projects — see section 10).

Skeleton of the document:

```json
{
  "name": "myproject",
  "roots": [
    { "name": "src",      "type": "src",    "path": "./src" },
    { "name": "test",     "type": "test",   "path": "./test" },
    { "name": "assets",   "type": "assets", "path": "./assets" },
    { "name": "profiles", "type": "json",   "path": "./json/profiles" },
    { "name": "gen",      "type": "gen",    "path": "D:/wood/generated", "readonly": true }
  ]
}
```

## 2. Partitions: level-1 mounts, typed

The first level of the sidebar tree — and only the first level — consists of **partitions**. A
partition is a *fictive project partition*: a named, typed mount of a physical folder.

| Property | Meaning |
|----------|---------|
| `name` | the virtual label shown in the tree; also the virtual path segment |
| `type` | one of `src`, `test`, `assets`, `json`, `gen`, `target` — the semantic role (below) |
| `path` | physical location: **relative** (`"./assets"`, resolved against the project root) or **absolute** (external mount, anywhere on any disk) |
| `readonly` | optional; if true, files render but no editor action saves into this mount |

Rules (binding):

1. **Mounts exist only on level 1.** Deeper nesting is always physical subfolder structure below a
   mounted root. No nested or overlapping virtual mounts.
2. **Two partitions must not mount the same physical folder.** A physical folder is mounted at most
   once per project; the editor validates this at load time and refuses with a message.
3. Sources may be **scattered** — like Maven. The project does not assume a particular layout;
   several `src` partitions on different disks are legal.
4. The sidebar tree is a **projection**: virtual structure (partitions) mapped onto physical
   locations (mounted folders). Link semantics, not copy semantics — moving nothing, only showing.

### Partition types (roles)

| Type | Meaning | Browser defaults |
|------|---------|------------------|
| `src` | hand-written code / content | editable |
| `test` | test code / fixtures | editable |
| `assets` | hand-maintained data; the home of JSON that is content, not structure | editable |
| `json` | **bundle partition** — contains bundles (section 4) | editable, bundle-aware |
| `gen` | output of a generator (e.g. Java models generated from JSON) — semantically build output that is meant to be *read* | default `readonly` |
| `target` | build output, wiped by builds | collapsed, `readonly`, not indexed |
| `isolated` | **isolated source** — a view-only old world (section 12, phase 9): bundles to *look at*, e.g. an older version of a project bundle | `readonly`, bundle-aware, fully outside the identity world |

Types are roles, not layouts: the UI derives icon, color and behavior from `type`, never from
guessing path conventions.

## 3. The virtual tree

```
[sidebar]                    [dual-tree editor as today]      [dependents/diff pane]
myproject/
├── src/        (mount ./src)
│   └── … (lazy)
├── profiles/   (mount ./json/profiles — json partition)
│   ├── config1/          ← bundle (has .woodbundle.json)
│   └── devConfig/        ← bundle
├── assets/     (mount ./assets)
└── gen/        (mount D:/wood/generated, readonly)
```

Rules:

- **Synthetic nodes** (the partition nodes themselves) render distinguishable from real folders
  (e.g. link/mount glyph); a partition is an aggregation, not a real directory.
- **Lazy loading:** folder children are read when the node is expanded, never up front. `target` is
  collapsed by default and may also be excluded entirely via a viewer setting later.
- File-node types in v1: **JSON (bundle-aware), other files read-only.**
  - double-click `.json` in a `json` partition → open the bundle in a tab (as today's Open Bundle).
  - double-click `.md`, `.txt`, `.java`, any other file → **decided (phase 2): external open**
    (desktop handler, no view inside Wood). The Wood editor does not
    grow a text editor in this concept.
- Changing physical structure under a mount (files added/removed externally) shows up on refresh
  of that subtree (re-expand / F5-style action). No file system watcher in v1.

## 4. Bundles inside a `json` partition: `.woodbundle.json`

A `json` partition is the only place where the sidebar goes **deeper than the physical layout in
meaning**: it groups files into bundles.

A bundle is a folder containing a bundle descriptor:

```
./json/profiles/config1/
├── .woodbundle.json        ← the bundle descriptor
├── config1.json            (main, declared)
├── configDesc.json
└── ConfigTemplate.json
```

Descriptor (`JSON`, relative file names only — the descriptor stays valid wherever the folder moves):

```json
{
  "name": "config1",
  "main": "config1.json",
  "files": [ "config1.json", "configDesc.json", "ConfigTemplate.json" ],
  "imports": [
    { "bundle": "base.logging", "version": "1.2" },
    { "bundle": "profile.base", "path": "../profile-base" }
  ]
}
```

Rules:

1. **Discovery is a scan, not a heuristic:** a folder with a `.woodbundle.json` is a bundle.
   Folders without a descriptor are not bundles. Nothing is guessed from file name patterns.
2. The descriptor **declares the main file** — the entry point that a double-click opens. This is
   today implicit knowledge; from now on it is explicit and machine-readable.
3. `name` defaults to the folder name; it is the display and import identity within the project.
4. **Single-file fallback:** a bare `*.json` directly inside a `json` partition without a bundle
   folder is accepted as an implicit single-file bundle (comfort: it opens, it has identity). The
   first structural action on it (rename/clone/import wiring) **materializes** it: the editor
   offers to create `xyz/` + `.woodbundle.json`. No implicit state survives an explicit action.
5. An `EditBundle` opened from the sidebar is exactly today's `EditBundle` — the project layer only
   finds and labels it.

Descriptor naming decision: **`.woodbundle.json`** (extension stays `.json`, consistent with
`project.json`; file handlers can register on the extension).

## 5. Cloning

Clone = new sibling bundle (folder copy + relabel). The physical destination follows the
**sibling convention**:

```
./json/profiles/config1/          → ./json/profiles/config1_copy/
```

- The clone lands **inside the same partition, next to the original** with a suffix (`_copy`,
  `_2`, … — first free name). The clone is findable in the explorer exactly where the tree shows
  it. A "Save As"-style dialog is the deliberate escape hatch for other destinations.
- The descriptor travels with the copy (all paths in it are relative — still valid).
- `name` inside the descriptor is rewritten to the new folder name.

Two modes, chosen in the clone dialog (both build on the same machinery):

| Mode | Linked files | Links inside the clone | Use case |
|------|--------------|------------------------|----------|
| **Deep (default)** | copied into the clone folder | rewritten to the copied files | "clone = make independent" |
| **Shallow** | referenced in place, not copied | unchanged (still point at the original's files) | "clone = variant on the same scaffold" |

Cloning a bundle and importing a bundle (section 6) are the same physics in two directions:
**distance decides copy depth.** Near things (own project) are referenced; far things (repository)
are materialized.

## 6. Imports: the Maven analogy

`imports` in the descriptor follow Maven's proven split into **two channels**:

| Channel | Entry | Resolution | Liveness |
|---------|-------|------------|----------|
| **Reactor** (like Maven modules) | `{ "bundle": "x", "path": "../x" }` | relative to the project, inside the same project | **live reference** — edits are shared; "mine to touch" |
| **Dependency** (like Maven coordinates) | `{ "bundle": "base.logging", "version": "1.2" }` | local bundle **repository** (below) | **frozen materialized copy**, read-only for the importer |

### The local bundle repository

```
~/.wood/repository/base/logging/1.2/       ← bundle folder with .woodbundle.json + files
```

- Analogous to `~/.m2`: a publish place, not a workbench. Only **version-stable** bundles are
  installed here (an explicit "install to repository" action later; v1: by hand).
- Resolution of a dependency import: **deep-copy** the repository folder into the project's
  import cache (per project, or `~/.wood/cache/`), then treat it like a shallow-frozen sibling:
  the copy is the import's stand. The repository file is never edited in place through an import.
- Version pinning is the immutability promise: `1.2` means *this* copy, forever. There is no
  SNAPSHOT in v1; if that need arises it is a later decision (refresh-on-resolve), not a default.

### Resolution order (v1, binding)

```
import "X":
  1. path imports            → direct, project-relative (reactor)
  2. project-local           → bundle of that name in one of the project's own partitions (convenience)
  3. local repository        → ~/.wood/repository/<name>/<version>/, materialize as deep copy
  4. otherwise               → error message with guidance; NO remote fetch in v1
```

### Deliberately strict in v1 (binding)

- **No transitive resolution.** An imported bundle's own imports are *shown* (tree of imports) but
  each importer wires what it wants explicitly. The existing Wood link resolution is orthogonal and
  not bypassed.
- **No conflict magic.** Two different versions of the same imported bundle in one project: the
  second is **refused with a clear message**, not silently "nearest-wins". That rule can be relaxed
  later if it proves annoying.

## 7. Wood IDs across bundles — decided: bundle-scoped (Strategy B)

Cloning and importing put bundle copies with the **same Wood object IDs and provider synonyms**
into one project whose own bundles may already occupy those IDs. Two strategies were discussed;
the decision is made (binding):

> **Strategy B — bundle-scoped IDs.** Wood object IDs and provider synonyms are unique only
> **within their bundle**. The bundle is the ID namespace ("one world per bundle"), like Java
> packages: `config::mainLogging` of bundle `config1` and `config::mainLogging` of bundle
> `config1_copy` are different IDs because resolution happens **relative to the asking bundle**.

Consequences (binding):

1. **Cloning and importing become copy acts.** No ID remapping, no reference rewriting; a clone is
   byte-identical to its original and stays comparable to it. Section 5 (clone) and section 6
   (import) are no longer blocked.
2. **The resolution engine changes meaning, once:** `syn::id` lookups
   (`WoodProxyResolver`, `JsonSystem.findResourcesBySynonym`, the registration matrix of
   `JSON_BUNDLE.md`) gain the asking bundle as namespace argument. The matrix rule
   "different path, same synonym → error" remains unchanged **inside** a bundle; it no longer
   fires across bundle boundaries.
3. **Cross-bundle sharing — decided (binding):** the bundle descriptor's `imports` array is the
   *single explicit input structure* — there is **no bundle-qualified link syntax** and no
   shared-bundle flag in v1. `syn::id` stays the only link form. Deliberate sharing across
   bundle boundaries is expressed by *declaring an import* (section 6): the import wires the
   imported bundle's namespace into the resolution scope of the asking bundle (reactor:
   live reference; dependency: frozen copy). Without a declared import, cross-bundle resolution
   does not exist. The engine supervises the wiring: when two namespaces are joined in one
   resolution scope and their Wood ID keys collide (`syn::id` of both sides overlap), the
   merge refuses — the remedy is the explicit `RerollID` preparation (section 7.1), never a
   silent overwrite. [&mdash;] Settled after checking the engine side: the resolution is a
   flat per-receiver merge today (`JsonBundle.mergeIntoMain`, `WoodProxyResolver.mergeLinkingSets`),
   which makes the merge point the natural - and only necessary - supervision point.
4. Strategy A (global uniqueness + remap on copy) is **rejected**: it would make every clone an
   identity translation and destroy the "same world in two places" property that the diff and
   comparison features build on.

### 7.1 RerollID — identity refresh as an opt-in tool

Under Strategy B rerolling is never *required* for correctness — clone and import keep their IDs.
But there are real cases where a bundle copy shall deliberately get a **fresh identity**: it is
about to be wired into a shared resolution context (shared bundle, cross-bundle references) where
the same IDs in two places would collide in that merge scope, or the clone is meant to become a
true independent fork. For those cases the machinery of the rejected Strategy A survives — as an
explicit, on-demand action instead of a forced tax on every copy:

```
RerollID(bundle, disjoint_to_bundles: [bundle1, …, bundleN])   ← N optional, may be empty
```

- **Semantics:** every Wood object ID and provider synonym inside the bundle is replaced by a
  newly generated one; every internal reference to a rerolled ID (`_woodLink`, `syn::id` targets)
  is rewritten consistently. The rewrite scope is the bundle itself — well-defined precisely
  *because* of Strategy B.
- **`disjunct_to_bundles`:** the engine-level tool — a list of trees whose ID sets the new values
  must not collide with. Empty list = fresh within the bundle. It stays a parameter so non-UI
  callers (scripts, later shared-scope setups) can pick subsets deliberately.
- **The UI always fills it completely (binding):** the project can enumerate **every tree it
  contains — no matter where it lives:** every resource of every bundle file, every standalone
  JSON file (implicit single-file bundle), and every resource of those standalone files. The
  reroll dialog lists exactly that full set, pre-selected — which makes a UI reroll always
  **project-wide disjoint**: fresh IDs that collide with nothing else in the whole project.
  A curated subset is possible only by explicit deselection, never the default.
- **UI:** sidebar context menu on a bundle — *Reroll IDs…* with the full project tree list
  (all bundles' resources, standalone JSON files and their resources — as above) pre-selected;
  the dialog validates the result (no collisions, no dangling references) before saving. The
  operation is one composite transaction on the bundle's editor history — one undo step rolls
  the whole reroll back.
- **Not** a clone mode: reroll happens after cloning, on the copy the user points at — clone
  (section 5) stays a pure copy act.

## 8. Sidebar UI (the third panel)

```
[ project sidebar ]   [ edit tree ]   [ dependents / diff ]
```

- A new, collapsible panel **far left** in `WoodWindow`; the existing dual-tree editor is not
  restructured.
- Header: project name + project document open button (project.json in a tab) + collapse.
- Per-node context menu (v1): for bundle folders — *Open bundle* (main file), *Open project
  document*, *Clone…* (phase per section 12); for partitions — *Refresh*, *Reveal in explorer*;
  for `readonly` mounts the edit entries are disabled with a hint. Built state: the bundle-level
  edit actions (clone, materialize, reroll) are refused below readonly mounts and isolated sources
  render their tabs as read-only views; the remaining backlog is the **save guard** - a save from
  a tab into a readonly mount is not blocked yet (see status line).
- The sidebar is **read-mostly state**: it holds no edit state of its own. Opening a bundle reuses
  the entire existing tab/`EditBundle` machinery (duplicate open = focus the existing tab).
- Theme: sidebar renders with normal tree colors; partition types may get icon tints from the
  theme family later (no new mandatory color keys in v1).

## 9. Relationship to the settings-side project concept

`ProjektEntry` / `knownProjects` (the older, settings-side list of known project folders) stays as
is and is **not** this concept. It is a remembered-paths list. This concept's project is
self-describing on disk (`.wood/project.json`). Later, a thin integration can add an opened
project to `knownProjects` — convenience only, never a requirement to open one. The two concepts
keep their names because the bundle rename freed the word *project* for exactly this layer.

## 10. Non-goals (v1)

- No text/markdown/java **editing** in Wood (open externally or view read-only; decided phase 2).
- No file system watcher.
- No remote repository fetch (no URL resolution, no download). v1 local repository only.
- No transitive import resolution, no version conflict magic.
- No nested/overlapping mounts; no virtual mounts below level 1.
- No drag & drop restructuring of the virtual tree; project.json is edited as a document (and
  via small context actions where trivially safe).
- No rename/move/delete refactorings of physical folders from the tree (reveal-in-explorer is the
  escape hatch in v1).
- No generator integration (`gen` is only typed and rendered; running a generator is not this
  concept).

## 11. Open questions

1. **Cross-bundle sharing syntax (section 7, consequence 3)** — **decided (binding):** the
   descriptor `imports` are the only sharing structure; no link syntax, no shared-bundle flag
   (see section 7, consequence 3). The engine merge supervises collisions; `RerollID` with
   `disjunct_to_bundles` (section 7.1) is the explicit preparation when two bundles must enter
   one resolution scope collision-free.
2. Bare non-JSON files inside a `json` partition: hide, or show gray? **Decided by the build:** the
   partition is honest - every file shows plainly (no hiding, no graying; a bundle subtree lists
   undeclared files after the declared ones).
3. Recent-projects integration with `knownProjects` (section 9) — v2 comfort, decided when the
   sidebar has proven itself.
4. Bundle version sources: is `version` a descriptor field of the *installed* bundle only, or can
   working bundles carry a version for install? **Decided by the build (v1): installed-only** - the
   version lives on the import (the pin of a dependency import and the cache key); working bundles
   carry no version field. Versioning a working bundle is the later install action's job.
5. Import cache location: per project (e.g. `.wood/cache/`) vs. `~/.wood/cache/`. **Decided &
   built: per project** (` .wood/cache/<name>/<version>/`, materialized exactly once) - the
   project stays self-contained.
6. **Editor provider round trip (fixed - phase 8a):** the editor had no representation of a file's
   `_woodProviders` member and its save dropped the entries silently - saving a wired file from a
   tab stripped the bundle wiring. Phase 8a fixes it (jsonCasted_edit): the import captures the
   member on the `EditTree`, `EditTreeWriter` writes it back verbatim; a save keeps the wiring. This
   is the precondition for phase 8's synonym reroll. Known residual quirk (accepted): string
   values with embedded quotes or backslashes do not escape/unescape symmetrically between parser
   and writer - out of scope, provider synonyms and filenames are identifiers.
7. **Provider filename resolution (decided, binding - phase 8):** a `_woodProviders` filename
   resolves **strictly relative to the folder of the file that declares it**; absolute filenames
   pass through unchanged. Before this rule relative filenames resolved against the process
   working directory (the reason the suites had to run from `test_assets`): a bundle only loaded
   completely when the editor happened to run in the right folder, and the editor session silently
   missed provider trees elsewhere. The engine changed in `JsonBundle.loadProviderGraph` and
   `WoodProxyResolver` (owner-relative resolution, no cwd fallback); the fixtures carry
   file-relative paths now. Downstream consumers (clone deep relocation, reroll classification,
   the imports resolver) already lived by this convention.
8. **Synonym reroll as a switch (decided - amendment of section 7.1):** the wood object IDs always
   reroll (the point of RerollID), the provider synonyms reroll only with the switch on. The dialog
   carries the checkbox, default ON (the binding original), the engine takes the flag
   (`BundleReroller.plan(..., rerollSynonyms)`). With the switch off: the bundle keeps its wiring
   names, the ids still reroll project-wide disjoint, the internal references only exchange their
   id part - dangling validation is not optional. Use case: keep readable wiring names when only
   the identities must become fresh (a shared scope without a rename).
9. **Isolated sources (decided - phase 9):** an `isolated` partition mounts a view-only old world:
   bundles a user wants to *look at* - typically an older version of a project bundle (from git,
   a worktree or a copy; the extraction from git itself is a later comfort, v1 mounts a folder the
   user fills). By their reason of existence these sources carry **IDs that must collide** with the
   live project (an old version has almost the same `syn::id` pairs) - therefore they are fully
   outside the identity world: never bundled into the reroll (neither the enumeration nor the
   disjoint sets - a reroll does not see them), never scanned by the version conflict rule, open
   only as read-only views, clone/materialize refused. Exactly one hook is deliberate, with a
   refined engagement rule: a project-local import resolution may FIND an isolated bundle, but
   only as the **last resort** (the live `json` partitions always win, an old version never
   shadows the current bundle) and **never as a live link into the ghost world** - the hit
   materializes a deep-copy into the project's import cache (`.wood/cache/<name>/<version>/`,
   exactly once, the frozen-stand rail of the repository channel, own badge `ISOLATED`); the
   isolated folder itself stays a read-only view. Explicit wiring into the old world remains
   the user's own decision via a path import. Use case: cherry-picking against a stand (concept
   CONCEPT_TreeDiffView.md) - the isolated mount is the parking place of the theirs side.
10. **Cherry-picks from a foreign identity world (decided - phase 10):** NO persistence of any
    reroll trace. Instead the pick boundary of a cross-world pair (ours = live session, theirs =
    an isolated stand opened as the diff counterpart) runs a causal translation at pick time:
    (a) **synonym check** - the picked references carry ghost synonyms; the translation derives
    old→current from the ONLY stable anchor, the provider file correspondence (a ghost provider
    entry names the same file as a live provider entry under a new synonym);
    (b) **conflict check** - a translated reference must resolve in the live world: references
    into the pick's own content rewire to the fresh ids, references to live objects must exist
    under the translated synonym (a rerolled away id dangles and REFUSES the pick with guidance -
    nothing is half-applied); (c) **fresh ids for every picked element** - picked identities are
    born new and disjoint in the live world, never inherited from the ghost (identity deltas as
    such never apply - the engine reports identity changes as REMOVE+ADD anyway). Causally
    faithful: nothing is guessed or reconstructed after the fact; unresolvable means refusal,
    the user decides.

## 12. Implementation plan

1. **Model foundation (jsonCasted/jsonCasted_edit):** project document readable/writable
   (`ProjectDocument` with `Partition` records: name/type/path/readonly, path resolution incl.
   project-root relative), `.woodbundle.json` readable/writable (name/main/files/imports).
   Validation rules of sections 2 and 4. Tests. *(Built - the model landed in jsonCasted;
   jsonCasted_edit needed no share of it.)*
2. **Sidebar skeleton (WoodJsonJack):** third panel, project open action (choose folder, needs
   `.wood/project.json`), partition rendering, lazy expansion, file nodes, double-click wiring for
   JSON (via existing bundle open), reveal-in-explorer. *(Built.)*
3. **Bundle grouping:** scan of `json` partitions for descriptors, bundle subtree rendering,
   single-file fallback with materialize action. *(Built - the scan is bundle-aware in `json` and
   `isolated` partitions since phase 9.)*
4. **Project document round trip:** open project.json in a tab (it is JSON), edit, save, sidebar
   refresh; clone dialog *without* execution (dry) until 6. *(Built.)*
5. **Bundle-scoped resolution (engine prerequisite, requires question 1 of section 11):**
   namespace-aware resolution in jsonCasted — asking bundle as resolution context,
   `WoodProxyResolver` / `JsonSystem` supervision, cross-bundle sharing syntax. Regression: all
   existing link tests stay green (single-bundle projects must behave exactly as today).
   *(Built - questions 1 settled binding; the engine carries the namespaced lane; the editor
   activates it with the import wiring, see status line.)*
6. **Clone execution:** folder copy, sibling convention, deep/shallow, descriptor rewrite.
   (No ID remapping — Strategy B, section 7.) *(Built - shallow clones byte-identical, deep
   relocates external provider files with rewritten references and a full rollback.)*
7. **Imports:** path imports (same-project wiring), local repository layout, materialize-once
   cache, resolution with strict refusal rules. *(Built - binding channel order, conflict rule,
   no transitive resolution; the isolated find runs through the same materialize-once rail since
   phase 9.)*
8. **RerollID (section 7.1):** id generation + reference rewrite inside one bundle,
   `disjunct_to_bundles` collision validation — engine tool with subset support, but the UI
   always passes **all project trees** (bundle resources, standalone files, their resources) =
   project-wide disjoint. Undo as one composite transaction. Can follow directly after phase 5;
   also benefits from settling question 1 of section 11. *(Built; amendment: the synonym switch,
   question 8; preconditions 8a - the editor provider round trip fix and the strict file-relative
   provider filename resolution, question 7.)*
9. **Isolated sources (question 9):** the `isolated` partition type — a view-only old world
   outside the identity world: bundle-aware rendering, read-only open, no clone/materialize,
   no reroll participation (neither enumeration nor disjoint), no conflict scan; a project-local
   import serves an isolated bundle only as the last resort and as a FIND - the hit materializes
   a frozen copy, never a live link into the ghost world. *(Built.)*
10. **Cross-world cherry-picks (question 10):** the pick boundary translation of a cross-world
   pair (live session ↔ isolated stand): synonym check via the provider file correspondence,
   conflict check on every translated reference (dangling refuses the whole pick), fresh ids for
   every picked element - no reroll trace is persisted; the translation is causal, per pick.
   *(Built.)*
11. Each phase: compile all three repos, test loop per repo recipe, live test in the running app,
   commit. (Established loop of this feature series.)

---
*Status: phases 1 to 10 built - project document model (jsonCasted), sidebar skeleton, bundle
grouping with single-file fallback, project document round trip with dialog, bundle-scoped IDs
(strategy B) with the engine's supervised merging, the clone execution, the import resolution
(path reactor, project-local, repository with the materialize-once cache, conflict rule - no
transitive resolution), RerollID (engine plan tool with disjunct_to_bundles and the synonym
switch, editor commands as one composite undo step, dialog with the project-wide preselected
tree list; its preconditions landed as 8a - the editor provider round trip fix - and the strict
file-relative provider filename resolution of question 7), and the isolated sources (phase 9 -
the view-only old world outside the identity world, cherry-pick ready), and the cross-world pick
boundary translation (phase 10 - the synonym check via the relative provider name, the conflict
check, fresh ids for every picked element - no reroll trace persisted, question 10). Open: none of the planned
phases; the noted backlog items are readonly enforcement on save and the dormant namespaced open
lane until the import wiring establishes it; git extraction for isolated mounts is a later comfort.
Names and rules marked "binding" are the agreed state of the discussion; everything else is
proposal until the phase that builds it.*
