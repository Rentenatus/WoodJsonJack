# Tree Diff View Concept (WoodJsonJack)

## Purpose and scope

This concept specifies the **visual tree diff** in WoodJsonJack: the right pane of the dual-tree editor
becomes a diff view next to its dependents view. It turns the delta reports of the tree diff engine from
abstract op lists into a spatial, reviewable presentation — with cherry-picking as the application path.

It builds on the diff concept `doc/TREE_DIFF.md` in jsonCasted and on everything that is already built and
live-tested on the `feature/treediff` / `feature/treediff-ui` branches:

| Foundation | State | Where |
|-----------|-------|-------|
| `DifferableNode` projection + `MerkleHasher` + `NodeValidator` ("hash filters, equals decides") | built | `de.jare.jsoncasted.diff` (jsonCasted) |
| `TreeDiffer` engine (W2: ADD / REMOVE / MODIFY / MOVE / REORDER, radiating flags) | built | `de.jare.jsoncasted.diff` |
| `ThreeWayDiffer` analysis (U1: apply / no-op / conflicts) | built | `de.jare.jsoncasted.diff` |
| `EditTreeMerger` incl. `applyDeltas` (one composite transaction per application, gap G2) | built | `de.jare.jsoncasted.editor.merge` (jsonCasted_edit) |
| Base retention G1 (`adoptBaseSnapshot`) + save refresh R4 (`adoptSavedBase`) | built | `de.jare.jsoncasted.editor.core.EditTree` |
| W2 Diff / W3 Diff / W3 Merge in the root popup (dialogs, conflict decision) | built | `de.jare.tree.ui` (WoodJsonJack) |
| Cast provenance (`isCastInferred`, parse feedback is not content) | built | `EditNodeObject` + `DifferableNode.contentCast` |
| Diff color family `fore.diff.*` (add, remove, modify, move, conflict, radiating; light + dark) | built | `de.jare.tree.settings.theme.AScheme` / `ColorScheme` |
| Merkle invalidation per mutation (`markMerkleDirty`, lazy O(changed) recompute) | built | `EditNodeAbstract` |

**This concept adds the view; it changes no model.** The diff semantics stay where they are; this document
specifies where the visual state lives, how it is built, validated, and applied.

---

## 1. The one view, two modes

Each editor tab (`JackEditTreeContainer`) shows two trees. The left tree is the tab's identity — the
loaded file, live and editable. The right tree is a **view pane**: today it shows dependent resources
(link view). The diff view is a **second mode of that same pane**:

```
Right pane mode:  DEPENDENTS  (as today — link view, dependent resources)
                 DIFF        (this concept — frozen counterpart + marks)
```

- The mode is **per tab**, never global. Switching back to DEPENDENTS restores the link view state
  exactly; the diff copy is freed.
- The mode is switched and displayed in the right pane header (section 6).

**Why no third tab, no copy-of-both-sides:** the pane is already a view pane (dependents are a sight on
the linking set, not a second document); one view keeps the live-reference philosophy of the W2/W3
dialogs for the *ours* side, while the *theirs* side is deliberately frozen (section 3).

## 2. Geometry is the role

With "edit left, pick right" the pane side is not interchangeable — it **is** the merge role:

| Pane | Content | Mutability | Merge role |
|------|---------|-----------|------------|
| **Left** | the tab's own `EditTree` | live, editable (all existing editors, commands, undo) | **ours** — cherry-picks land here |
| **Right** | `deepCopy(true)` of the counterpart tree, wrapped in a fresh `EditTree` | **readonly** | **theirs** — the frozen diff partner |

Consequences:

- The **view opens in the ours tab**, and the UI switches that tab to the front. Which tab that is
  follows the existing flag semantics of `TreeDiffSides`:
  - marked as **LEFT** (ours role) → the view opens in the *marked* tab; the right pane freezes the
    tree of the triggering tab.
  - marked as **RIGHT** (theirs role) → the view opens in the *triggering* tab; the right pane freezes
    the marked tree.
- No "ours/theirs" labels are needed on the panes: the side is the role. The flag only decides
  *where* the view opens and which tree gets copied.
- The trigger (root popup) is unchanged: `W2 Diff` / `W3 Diff` keep opening their dialogs; each dialog
  gains a **"Show in trees"** button, and the popup gains direct view entries
  **`W2 View: with <desc>`** / **`W3 View: with <desc>`** (same enablement as W2/W3 Diff).

## 3. The theirs side is a frozen copy

The right pane in DIFF mode shows `deepCopy(true)` of the counterpart root plus a fresh
`EditTree` wrapper (fresh `EditTimes`, no linking set needed). This is a **snapshot at diff time**:

- The other tab keeps living; its later edits do **not** appear in the view until a re-diff (section 5).
  That is the point: cherry-picking compares against a *stand*, not a moving target.
- No `JackTreeModel` is ever laid over an `EditTree` that is displayed elsewhere — the original
  double-model risk (parse/undo/selection events of two views on one tree) does not exist.
- The copy's Merkle hashes are content-based: the diff at open time is identical to a diff against the
  live counterpart at that moment.
- Cost: the counterpart tree's memory for the duration of the view; freed on every mode switch and
  re-diff.

**W2 vs. W3 in the view:**

- **W3 view** (the merge view): base = ours' retained import state (`getBaseSnapshot`), ours = left
  pane, theirs = right copy. Marks classify per the `MergePlan`. **Cherry-picking is protected by the
  merge plan** — it is `applyDeltas` on merge-plan deltas.
- **W2 view** (the comparison view): marks from the `DiffReport` of left vs. right copy.
  **W2 cherry-picking exists**: a W2 mark picks through the same `applyDeltas` rail — the report op
  carries everything the application needs, a neutral empty-plan carrier books the outcome (the
  conflict protection is the pair semantics itself: the mark IS the change). Take-all stays W3-only
  (a plain pair has no apply list).

**One extra entry, the disk pair:** **Diff Changes (vs. Disk)** (Project menu, and the save
conflict's "Merge (3-way)"/"User diff (W2)" options): ours is the live working copy, theirs is the
on-the-fly loaded disk version. With a retained import state it opens as W3
(base = import state — the save quadrant made visible), without as W2; the conflict dialog can
force the plain W2 user diff. Picking there takes external changes into the working copy.

## 4. Where the diff information lives

**Not on the node models.** Marks are view state, not content; they must not ride along in `deepCopy`,
undo/redo, or the Merkle hash. `EditNodeAbstract` and `JsonNode` stay untouched. The only model service
the view consumes is the read-only `getMerkleHash()` for self-validation.

The state lives in the view layer, optional and nullable at every level:

```
DiffViewController                     (one per JackEditTreeContainer, created lazily)
├── mode:            DEPENDENTS | DIFF
├── pair:            oursRoot (left pane) | theirsCopyRoot (right pane) | kind: W2 | W3
├── marksLeft:       Map<EditNodeAbstract, DiffMark>   // keys are nodes of the live ours tree
├── marksRight:       Map<EditNodeAbstract, DiffMark>   // keys are nodes of the frozen copy
└── taken:           per-mark cherry-pick bookkeeping

DiffMark (value type, de.jare.tree.ui)
├── type:            ADD | REMOVE | MODIFY | MOVE | REORDER | CONFLICT
├── radiating:       boolean (from the op; extra badge)
├── merkleHashAtDiff: long — the node's hash at diff time (self-validation, section 5)
├── op:              the DeltaOp / ConflictRecord (tooltip text: old/new, reason)
└── taken:           boolean — set after this change was cherry-picked (grayed out)

JackEditTree
└── diffMarks:       Map<EditNodeAbstract, DiffMark> | null   // set by the controller, null = no marks
```

- The renderer resolves its map by walking the ancestor chain from the `JTree` it renders to the
  `JackEditTree` and reading **its** map. `null` renders exactly as today — zero cost, zero risk for
  the DEPENDENTS mode and all other trees.
- The semantic sources remain `DeltaOp`, `DiffReport`, `MergePlan`, `ConflictRecord` (jsonCasted);
  the mark only *visually summarizes* and never invents its own truth.

**Path resolution:** building the maps requires resolving match keys (`$.a.b[2].#0`) to nodes. That
logic exists but is private in `EditTreeMerger`; it becomes a public utility
`EditPathResolver.resolve(root, path)` in jsonCasted_edit (editor level: member keys, `#0` data
children, annotation-free projection indices), used by the merger and the view alike.

**Mark mapping rules:**

| Op | Left pane (ours) | Right pane (theirs copy) |
|----|------------------|---------------------------|
| ADD (theirs added) | — | ADD on the added node |
| REMOVE (theirs removed) | REMOVE on the node ours still has | — |
| MODIFY (VALUE/CLASS/NAME/LINK_TARGET) | MODIFY on the changed node | MODIFY on the counterpart node |
| MOVE / REORDER | MOVE/REORDER on the moved node resp. parent | same on the counterpart |
| W3: ours deltas (`D_ours`) | mark per delta type (the user's own edits) | — |
| W3: apply list | — | ADD/MODIFY/REMOVE per delta (incoming) |
| W3: conflicts | CONFLICT on the ours node | CONFLICT on the theirs node |

REORDER additionally carries the order labels (`newOrder`, renderer-mirrored names as built) into the
tooltip / badge.

## 5. Staleness: self-validating marks and the edited color

Only the left tree can change (right is frozen, readonly). Marks therefore validate **lazily at paint
time**, per marked node, with the Merkle machinery:

```
valid  ⇔  mark.merkleHashAtDiff == node.getMerkleHash()
```

- valid → the mark's diff color (theme family `fore.diff.*`)
- invalid → the **edited color** (`fore.diff.edited`, the 7th key of the family — to add to
  `AScheme`/`ColorScheme`/`FontSettings` exactly like the other six) plus tooltip
  "changed since the diff ran — re-diff"
- no event wiring, no invalidation lists; `markMerkleDirty` + lazy recompute already maintain the
  hash. A mark on a node deleted after the diff simply stops rendering (the map entry remains
  unreferenced until the next re-diff).
- Edge case, accepted and documented: an edit chain A→B→A restores the hash; the mark shows fresh
  again even though an intermediate state existed.

**Re-diff policy (decision): manual, with good signaling.** The view header aggregates staleness
("3 of 12 marks outdated") and offers a **Re-diff** button that re-runs the same pair (W2:
`diff(left, newCopy)`; W3: `analyze(base, left, newCopy)`) and rebuilds both maps. The root popup
actions re-diff implicitly. Automatic re-diff (parse-quiet, focus change, timer) is explicitly
deferred to a later version — the user keeps control over the moment of comparison.

## 6. Mode display and pane header

The right pane header (the existing `JackEditTree` header panel) becomes the mode surface:

- A **segmented control "Dependents | Diff"** (the existing link checkbox is absorbed into it). The
  Diff segment is disabled with a hint when no side is marked (`TreeDiffSides.hasRemembered()`).
- In DIFF mode the header additionally shows: the pair description
  (`root #A ↔ root #B`, `TreeDiffSides.describe`), the **Re-diff** button, the staleness summary,
  and a **Close diff** affordance (= switch back to Dependents).
- In the left pane header: nothing changes (the left pane has no modes).

## 7. Cherry-picking (W3 only)

Cherry-picking takes selected theirs changes into the live ours tree — on the already built rails:

- **Trigger:** context menu / double-click on a marked node in the right pane, plus a
  **"Take all incoming"** button in the view header.
- **Mechanism:** `EditTreeMerger.applyDeltas(oursTree, theirsCopyRoot, plan, chosenDeltas, history)`
  — every pick (or pick batch) is **one composite transaction = one undo step** on the ours tab's
  history (`JackUndoManager.getManagerFor(oursModel).getHistory()`, as in the W3 Merge dialog).
- **Conflict nodes:** picking a CONFLICT-marked node is the inline "take theirs" decision — the
  conflict's theirs delta goes through the same path. "Keep mine" is the default non-action.
- **MOVE / REORDER:** not applicable in v1 (`applyDeltas` records them as problems); the pick action
  is disabled on such marks with a hint.
- **After a pick:** the mark on the right flips to **taken** (grayed); the left node changes and its
  own mark (if any) self-invalidates to the edited color until the next re-diff. Undoing the pick
  restores the left state — if that state equals the diff-time state, the marks become fresh again by
  themselves (hash equality), which is correct: the comparison is honest again.
- Radiating picks (wood id / link target changes) show the radiating badge before applying; the
  dependent lookup (`EditProject.getDependents`, concept R10) is still open and explicitly out of
  scope here.

## 8. Lifecycle

| Event | Behavior |
|-------|----------|
| Open (popup "W2/W3 View") | resolve roles by flag → switch to the ours tab → deep copy theirs → build maps → set pane maps → right pane readonly → header switches to DIFF |
| Re-diff (button / popup rerun) | fresh copy + fresh maps; taken flags reset |
| Mode switch to DEPENDENTS | pane maps → null, copy freed, link view restored |
| Tab close of the ours tab | controller dies with the container; copy freed by GC |
| Close of the *other* tab | irrelevant — the view holds a copy, not a reference (a deliberate strength of the frozen model) |
| Save (ours tab) | R4 refreshes the base; the view marks stay as they are until the next re-diff (base changes make W3 marks stale in the *classification* sense — a re-diff is the honest answer and is signaled) |
| Undo/redo | only ever touches the left tree; marks self-validate (section 5) |

## 9. Rules and decisions (binding)

1. No diff state on `EditNodeAbstract` or `JsonNode` — marks live in the view layer, keyed by node.
2. Geometry is the role: left = ours (live, editable), right = theirs (frozen copy, readonly).
3. The view opens in the ours tab; the flag decides which tab that is.
4. Marks validate themselves via Merkle hash comparison at paint time; stale shows the edited color.
5. Re-diff is manual (button / popup re-run); automatic re-diff is a later version.
6. Cherry-picking is W3-only, always through `applyDeltas`, always one undo step per batch.
7. MOVE/REORDER application stays deferred (v1) and says so in the UI.
8. The theme keys are the only allowed color source: `WoodSettings.getColorPrefix() + "fore.diff.*"`;
   the 7th key `fore.diff.edited` is added with this concept.
9. The dialogs (W2 Diff, W3 Diff, W3 Merge) stay as the precise list presentation; the view is the
   spatial presentation. Both read the same engine results.

## 10. Implementation plan

1. **Foundation:** add `fore.diff.edited` to the theme family; expose `EditPathResolver` in
   jsonCasted_edit (refactor `EditTreeMerger.resolve` onto it; merger tests stay green).
2. **Diff view skeleton:** `DiffMark`, `DiffViewController`, mode switch with header segment control,
   deep copy + fresh `EditTree`, readonly wiring, `JackEditTree.setDiffMarks/getDiffMarks`.
3. **Renderer:** mark lookup, colors, badges (radiating, REORDER order), tooltips, edited color.
4. **Mark building:** W2 (`DiffReport`) and W3 (`MergePlan`) mapping incl. conflict marks.
5. **Popup + dialogs:** view entries in the root popup, "Show in trees" in the dialogs.
6. **Cherry-picking:** pick actions, take-all, taken flags, undo wiring.
7. Each phase compiles against the three repos and ends with a live test round in the running app
   (the established loop of this feature).

## 11. Non-goals (this concept)

- No changes to the diff engine or the node models.
- No automatic re-diff.
- No MOVE/REORDER application (engine-side still v1-deferred).
- ~~No W2 cherry-picking~~ — lifted after W3 proved itself: W2 marks pick through the same
  `applyDeltas` rail with a neutral plan carrier (report op carries everything); take-all stays
  W3-only.
- No dependent lookup (`getDependents`, R10) — radiating marks show the badge, not the dependent list.

## 12. Open questions

1. Auto re-diff after the on-the-fly parse settles (debounced) — v2 candidate if manual re-diffing
   proves annoying in daily use.
2. Jump marks between the view and the dialogs (select an op in the dialog → select the node in the
   tree and vice versa) — cheap to add once both exist, decided during phase 5.
3. Should "Take all incoming" skip radiating picks and ask for them individually (safer) or take them
   silently (faster)? Recommendation: ask individually as long as R10 is not built.
