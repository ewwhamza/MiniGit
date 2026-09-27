# MiniGit — Product Requirements Document

| | |
|---|---|
| **Product** | MiniGit — a lightweight version control system written in pure Java |
| **Platform** | Desktop GUI (Java Swing) + command-line interface, Java 17+, pure Java — runs on Windows / macOS / Linux |
| **Document status** | Draft v1.1 — GUI promoted to core scope |
| **Last updated** | 2026-09-27 |
| **Source** | Project topic selection; modelled on the internal design of Git |

---

## 1. Overview

Version control is something every developer uses every day, yet most people treat
Git as a black box — a set of commands memorised without understanding what happens
underneath. MiniGit is a from-scratch implementation of the core ideas behind Git:
content-addressed storage, snapshots, commits as a linked history, branches as
movable pointers, and line-by-line diffs.

MiniGit ships with two front ends over one shared engine:

- **A desktop GUI** (Swing) — open a project folder, see changed files, stage them
  with a checkbox, read colour-coded diffs, write a commit message, browse history,
  and switch branches, all without touching a terminal. This is the primary interface.
- **A command-line interface** whose commands deliberately mirror Git's
  (`minigit init`, `minigit add`, `minigit commit`, …) — for scripting, testing,
  and showing that the engine is independent of the UI.

The whole project — engine, GUI, CLI, and tests — is written in Java alone.

The project is intentionally small enough to build in a few weeks, but every feature
exercises a real computer-science concept: hashing, trees, graphs, file I/O,
compression, serialization, and dynamic programming.

### 1.1 Problem statement

1. **Git is opaque to learners.** Students use `git commit` without knowing what a
   commit actually *is* on disk, why hashes matter, or how branches cost nothing.
2. **Manual versioning is fragile.** Without a tool, people keep `report_final.docx`,
   `report_final_v2.docx`, `report_REALLY_final.docx` — no history, no comparison,
   no safe way back.
3. **Typical student projects don't exercise core CS.** CRUD applications teach
   database access but rarely touch hashing, tree structures, or algorithms.

### 1.2 Goals

- **G1** — Track the full history of a folder of files as a chain of immutable snapshots.
- **G2** — Store file content once, no matter how many commits reference it (deduplication via hashing).
- **G3** — Show exactly what changed between any two versions, line by line.
- **G4** — Let the user restore any past version of the project safely.
- **G5** — Support lightweight branching so parallel lines of work can coexist.
- **G6** — Be fully explainable in a viva: every design decision traceable to a named concept.
- **G7** — Make every core operation available through a clear desktop GUI, so the tool is usable by someone who has never opened a terminal.

### 1.3 Non-goals (this release)

- **Git compatibility.** MiniGit borrows Git's *ideas*, not its exact byte format.
  A `.minigit` folder is not readable by real Git, and vice versa.
- Networking — no `clone`, `push`, `pull`, or remote repositories.
- Packfiles, delta compression, or garbage collection.
- Handling very large or binary-heavy repositories efficiently.
- Multi-user concurrency or locking across machines.
- Any non-Java technology: no web front end, no JavaFX (it is no longer part of the
  JDK), no third-party look-and-feel or UI libraries, no scripts in other languages.

---

## 2. Users

| User | Who | What they need |
|---|---|---|
| **Developer / student** | Anyone working on a folder of text files (code, notes, assignments) | Save versions, see history, compare, undo mistakes |
| **Evaluator / faculty** | Person assessing the project | A clear live demo and code that maps cleanly to CS concepts |

There are no accounts or roles. The author name and email recorded on each commit
come from a local config file (see FR-CFG).

---

## 3. Architecture

### 3.1 Layers

```
            java -jar minigit.jar            java -jar minigit.jar <command> [args]
              (no arguments)                            |
                    |                                   |
                Main.java  ---------------------------- +
                    |                                   |
         +----------+----------+             +----------+-----------+
         |      GUI (Swing)    |             |   CLI                |
         |  MainWindow         |             |  CommandParser       |
         |  ChangesPanel       |             |  InitCommand,        |
         |  DiffViewer         |             |  AddCommand, ...     |
         |  HistoryPanel ...   |             |  (format as text)    |
         +----------+----------+             +----------+-----------+
                    |                                   |
                    +-----------------+-----------------+
                                      |
                        MiniGit  (service facade — one method per operation,
                                  returns plain result objects, never prints)
                                      |
                                 Repository   (locates .minigit, owns the services below)
                                      |
                  +---------+---------+---------+----------+
                  |         |                   |          |
             ObjectStore  Index               Refs      Config
             (blobs,     (staging area)     (HEAD,     (author name,
              trees,                         branches,  email)
              commits)                       tags)
```

**Key rule:** the GUI and the CLI are both thin clients of the `MiniGit` facade.
Neither contains version-control logic, and the GUI never shells out to the CLI.
For example, `status()` returns a `StatusResult` object; the CLI prints it as text,
and the GUI renders it as lists with checkboxes. This separation (MVC-style) is what
makes the engine unit-testable without a window open.

### 3.2 On-disk repository layout

```
my-project/
├── .minigit/
│   ├── HEAD                  "ref: refs/heads/main"  or a raw commit hash (detached)
│   ├── config                author name / email
│   ├── index                 the staging area
│   ├── objects/
│   │   ├── 3a/
│   │   │   └── 7bd3e2360a3d...   compressed object, file name = rest of hash
│   │   └── ...
│   └── refs/
│       ├── heads/
│       │   ├── main          contains a commit hash
│       │   └── feature-x
│       └── tags/
│           └── v1.0
├── .minigitignore            optional ignore patterns
├── src/...
└── README.md
```

### 3.3 Core concepts

| Concept | Meaning in MiniGit | CS idea demonstrated |
|---|---|---|
| **Blob** | The raw content of one file | Content-addressed storage |
| **Tree** | A directory listing: names → blob/tree hashes | Tree data structure, recursion |
| **Commit** | A snapshot: root tree + parent(s) + author + message | Linked list / DAG |
| **Hash** | SHA-1 of an object's bytes — its permanent ID | Hashing, integrity |
| **Index** | The list of files staged for the next commit | Map / serialization |
| **Branch** | A file holding one commit hash | Pointers |
| **HEAD** | Which branch (or commit) is currently checked out | Indirection |
| **Diff** | The minimal set of line changes between two files | LCS / dynamic programming |

---

## 4. Current state

Phases 1–5 are complete (2026-09-27).

| Area | State |
|---|---|
| Project skeleton / build | **Built** — Maven + wrapper, runnable `target/minigit.jar` |
| CLI dispatch, `help`, `config`, `init` | **Built** |
| Object storage | **Built** — blobs, trees, commits; hashing, zlib, dedup, corruption check, prefix lookup, `hash-object`, `cat-file` |
| Staging area | **Built** — `add`, `rm` (with safety check), `.minigitignore`, stat-based change detection |
| Commits and history | **Built** — `commit`, `log` (with branch/tag labels), `show`, revision syntax (`HEAD~2`, `main^`, tags, hash prefixes) |
| Status | **Built** — staged / unstaged / untracked |
| Branching and checkout | **Built** — `branch` (`-d` safe / `-D` force), `checkout` (branch, detached commit, `-b`) with overwrite protection, `tag`, `restore`, `restore --staged`, mixed `reset` |
| Diff | **Built** — LCS line diff, unified format with 3-line context, `diff`, `--staged`, `A B`, `--stat`, binary detection |
| Merge | **Built** — fast-forward, merge base (lowest common ancestor), line-level three-way merge, conflict markers, resolve-by-staging, two-parent merge commits, `merge --abort`, modify/delete conflicts |
| GUI | **Built** — all P0 and P1 (FR-GUI-1 to 21), plus merge-conflict banner and conflict marking (23). Not built (P2): commit graph (22), side-by-side diff (24), file watcher (25), dark theme (26) |
| Not built (P2) | `log --graph`, `stash`, `reset --hard`, Myers diff |
| Tests | **125 passing** — unit tests per package, facade workflow and merge tests, CLI integration tests, GUI model tests |

---

## 5. Functional requirements

Requirements are identified as `FR-<module>-<n>` and carry a priority:
**P0** must-have for submission · **P1** important · **P2** desirable / stretch.

### 5.1 M1 — Repository and object storage

| ID | Priority | Requirement |
|---|---|---|
| FR-REPO-1 | P0 | `minigit init` creates `.minigit/` with `objects/`, `refs/heads/`, `refs/tags/`, an empty `index`, and `HEAD` pointing to `refs/heads/main`. |
| FR-REPO-2 | P0 | Running `init` in a folder that already has a repository prints a message and changes nothing. |
| FR-REPO-3 | P0 | Every command except `init` searches the current folder and its parents for `.minigit/`. If none is found it prints `not a minigit repository` and exits with status 1. |
| FR-OBJ-1 | P0 | An object is stored as `<type> <size>\0<content>`, where type is `blob`, `tree`, or `commit`. |
| FR-OBJ-2 | P0 | An object's ID is the SHA-1 hash (40 hex characters) of its full stored bytes, computed with `java.security.MessageDigest`. |
| FR-OBJ-3 | P0 | Objects are written to `objects/<first 2 chars>/<remaining 38 chars>`, compressed with `java.util.zip.Deflater`. |
| FR-OBJ-4 | P0 | Writing an object that already exists is a no-op — identical content is stored once (deduplication). |
| FR-OBJ-5 | P0 | Objects are immutable once written. |
| FR-OBJ-6 | P1 | When reading an object, the hash is recomputed and compared; a mismatch reports the object as corrupt. |
| FR-OBJ-7 | P1 | Any command accepting a hash also accepts a unique prefix of at least 4 characters (e.g. `3a7b`). An ambiguous prefix is an error that lists the matches. |
| FR-OBJ-8 | P1 | `minigit cat-file <hash>` prints an object's type and decoded content — a debugging aid and a strong demo tool. |
| FR-OBJ-9 | P1 | `minigit hash-object [-w] <file>...` prints a file's blob hash, and with `-w` stores it. Blob hashes are identical to real Git's. |

### 5.2 M2 — Staging area (index)

| ID | Priority | Requirement |
|---|---|---|
| FR-IDX-1 | P0 | `minigit add <path>...` hashes each file, writes it as a blob, and records `path → blob hash` in the index. |
| FR-IDX-2 | P0 | `add` accepts directories (recursively) and `.` for the whole working tree. |
| FR-IDX-3 | P0 | The index persists between runs as a sorted text file: one line per entry, `<blob hash> <size> <last-modified> <path>`. |
| FR-IDX-4 | P0 | Paths are stored relative to the repository root with forward slashes, on every OS. |
| FR-IDX-5 | P0 | The `.minigit/` folder itself is never added. |
| FR-IDX-6 | P1 | `minigit rm <path>` removes the file from the index and the working tree; `rm --cached` removes it from the index only. |
| FR-IDX-7 | P1 | Patterns in `.minigitignore` (`*.class`, `build/`, `*.log`, …) are skipped by `add .` and hidden from `status`. |
| FR-IDX-8 | P2 | `minigit restore --staged <path>` unstages a file, resetting its index entry to the version in HEAD. |

### 5.3 M3 — Commits and history

| ID | Priority | Requirement |
|---|---|---|
| FR-CMT-1 | P0 | `minigit commit -m "<message>"` builds tree objects from the index (one tree per directory, recursively), then writes a commit object. |
| FR-CMT-2 | P0 | A commit's content is: `tree <hash>`, zero or more `parent <hash>` lines, `author <name> <email> <unix-timestamp>`, a blank line, then the message. |
| FR-CMT-3 | P0 | After committing, the current branch's ref file is updated to the new commit hash. |
| FR-CMT-4 | P0 | The first commit in a repository has no parent. |
| FR-CMT-5 | P0 | Committing when the index matches HEAD's tree exactly prints `nothing to commit` and creates nothing. |
| FR-CMT-6 | P0 | A commit without `-m`, or with an empty message, is rejected. |
| FR-CMT-7 | P0 | On success, prints the branch, the short hash (7 chars), and the message: `[main 3a7bd3e] Add login page`. |
| FR-LOG-1 | P0 | `minigit log` walks from HEAD back through parents, printing hash, author, date, and message for each commit, newest first. |
| FR-LOG-2 | P1 | `minigit log --oneline` prints one line per commit: short hash and first line of the message. |
| FR-LOG-3 | P1 | `minigit show <commit>` prints the commit's details followed by its diff against its parent. |
| FR-LOG-4 | P2 | `minigit log --graph` draws branch and merge lines in ASCII. |

### 5.4 M4 — Status

| ID | Priority | Requirement |
|---|---|---|
| FR-STAT-1 | P0 | `minigit status` shows the current branch (or `HEAD detached at <hash>`). |
| FR-STAT-2 | P0 | It lists **staged changes**: files whose index entry differs from HEAD's tree — shown as *new file*, *modified*, or *deleted*. |
| FR-STAT-3 | P0 | It lists **unstaged changes**: tracked files whose working copy differs from the index — *modified* or *deleted*. |
| FR-STAT-4 | P0 | It lists **untracked files**: files in the working tree that are in neither the index nor ignored. |
| FR-STAT-5 | P1 | For speed, a file whose size and last-modified time match its index entry is assumed unchanged and is not re-hashed. |
| FR-STAT-6 | P2 | Output is coloured (green staged, red unstaged) using ANSI codes, with a `--no-color` flag. |

### 5.5 M5 — Diff

The algorithmic heart of the project.

| ID | Priority | Requirement |
|---|---|---|
| FR-DIFF-1 | P0 | A line-level diff between two texts is computed with the **Longest Common Subsequence** algorithm (dynamic programming). |
| FR-DIFF-2 | P0 | Output uses unified format: `--- a/<path>`, `+++ b/<path>`, hunk headers `@@ -l,s +l,s @@`, and lines prefixed with space, `-`, or `+`. |
| FR-DIFF-3 | P0 | Hunks include 3 lines of unchanged context around each change; nearby hunks are merged. |
| FR-DIFF-4 | P0 | `minigit diff` compares the working tree against the index (unstaged changes). |
| FR-DIFF-5 | P0 | `minigit diff --staged` compares the index against HEAD (what the next commit will contain). |
| FR-DIFF-6 | P1 | `minigit diff <commitA> <commitB>` compares two commits, including added and deleted files. |
| FR-DIFF-7 | P1 | A file containing a NUL byte in its first 8 KB is treated as binary: `Binary files differ`, no line diff. |
| FR-DIFF-8 | P2 | Replace LCS with the **Myers diff** algorithm for O((N+M)·D) performance on large files. |

### 5.6 M6 — Branches, checkout, and tags

| ID | Priority | Requirement |
|---|---|---|
| FR-BR-1 | P0 | `minigit branch` lists all branches, marking the current one with `*`. |
| FR-BR-2 | P0 | `minigit branch <name>` creates a branch pointing at the current commit. It fails if the name already exists. |
| FR-BR-3 | P0 | Branch names may contain letters, digits, `-`, `_`, `/`, `.`; they may not contain spaces or `..`, or start with `-`. |
| FR-BR-4 | P1 | `minigit branch -d <name>` deletes a branch. Deleting the current branch is refused. |
| FR-CO-1 | P0 | `minigit checkout <branch>` updates the working tree and index to that branch's commit and points HEAD at the branch. |
| FR-CO-2 | P0 | Checkout writes files that exist in the target commit, and deletes tracked files that do not. Untracked files are left alone. |
| FR-CO-3 | P0 | Checkout is **refused** if there are uncommitted changes to tracked files that it would overwrite. It lists the affected files. No work is ever silently lost. |
| FR-CO-4 | P1 | `minigit checkout <commit-hash>` enters *detached HEAD* state: HEAD contains a raw hash and a warning is printed. |
| FR-CO-5 | P1 | `minigit checkout -b <name>` creates a branch and switches to it in one step. |
| FR-CO-6 | P1 | `minigit restore <path>` discards working-tree changes to one file, restoring it from the index. |
| FR-TAG-1 | P1 | `minigit tag <name> [commit]` creates a lightweight tag. `minigit tag` lists all tags. Tags are accepted anywhere a commit is. |

### 5.7 M7 — Merge and reset

| ID | Priority | Requirement |
|---|---|---|
| FR-MRG-1 | P1 | `minigit merge <branch>`: if the current commit is an ancestor of the target, perform a **fast-forward** — move the branch pointer and update the working tree. |
| FR-MRG-2 | P1 | If the target is already an ancestor of the current commit, print `Already up to date`. |
| FR-MRG-3 | P2 | If the histories have diverged, find the **merge base** (lowest common ancestor in the commit graph) and perform a **three-way merge** file by file. |
| FR-MRG-4 | P2 | Conflicting regions are written with `<<<<<<<`, `=======`, `>>>>>>>` markers. The merge stops and the user resolves, `add`s, and commits. |
| FR-MRG-5 | P2 | A merge commit records two `parent` lines. |
| FR-RST-1 | P1 | `minigit reset <commit>` moves the current branch to that commit and resets the index, leaving the working tree untouched (mixed reset). |
| FR-RST-2 | P2 | `minigit reset --hard <commit>` also resets the working tree, after a confirmation prompt. |

### 5.8 M8 — Configuration, help, and extras

| ID | Priority | Requirement |
|---|---|---|
| FR-CFG-1 | P0 | `minigit config user.name "<name>"` and `minigit config user.email "<email>"` store values in `.minigit/config`. |
| FR-CFG-2 | P0 | `commit` is refused with a helpful message if `user.name` is not set. |
| FR-HELP-1 | P0 | `minigit help` lists every command with a one-line description; `minigit help <command>` shows its usage and flags. |
| FR-HELP-2 | P0 | Unknown commands or bad arguments print the usage and exit with status 1, never a Java stack trace. |
| FR-EXT-1 | P2 | `minigit stash` / `stash pop` save and restore uncommitted changes. |

### 5.9 M9 — Desktop GUI

Built with **Swing** (`javax.swing`), which ships with every JDK — no extra libraries.
The GUI is the primary interface and the centrepiece of the demo.

#### Window layout

```
+----------------------------------------------------------------------------+
| MiniGit — D:\Projects\my-app                                        [_][x] |
| File  Repository  Branch  Help                                             |
+----------------------------------------------------------------------------+
| [Refresh] [Commit] [New Branch] [Merge]            Branch: [ main      v]  |
+----------------------------------------------------------------------------+
|  [ Changes ]  [ History ]  [ Objects ]                                     |
+-------------------------+--------------------------------------------------+
| STAGED                  |  src/Main.java                                   |
|  [x] M src/Main.java    |  @@ -10,6 +10,7 @@                               |
|  [x] A README.md        |     public static void main(String[] args) {     |
|                         |  -      System.out.println("Hi");               |  <- red
| UNSTAGED                |  +      System.out.println("Hello");            |  <- green
|  [ ] M src/Util.java    |  +      run(args);                              |  <- green
|  [ ] D old.txt          |     }                                            |
|                         |                                                  |
| UNTRACKED               |                                                  |
|  [ ] notes.md           |                                                  |
+-------------------------+--------------------------------------------------+
| Commit message:                                                            |
| +------------------------------------------------------------------------+ |
| | Greet the user properly                                                | |
| +------------------------------------------------------------------------+ |
|                                                  [ Commit 2 files ]        |
+----------------------------------------------------------------------------+
| On branch main · 2 staged · 2 unstaged · 1 untracked      Last commit 3a7bd3e |
+----------------------------------------------------------------------------+
```

#### Requirements

| ID | Priority | Requirement |
|---|---|---|
| FR-GUI-1 | P0 | Running `java -jar minigit.jar` with no arguments (or double-clicking the JAR) opens the GUI. Any arguments run the CLI instead. |
| FR-GUI-2 | P0 | A **welcome screen** offers *Open Repository* and *Create Repository*, both via `JFileChooser` in directory mode. Opening a folder without `.minigit` offers to initialise it. |
| FR-GUI-3 | P0 | If `user.name` / `user.email` are not set, a dialog asks for them before the first commit. |
| FR-GUI-4 | P0 | **Changes tab** — lists Staged, Unstaged, and Untracked files in separate groups, each with a status letter (`A` added, `M` modified, `D` deleted) and colour. |
| FR-GUI-5 | P0 | Ticking a file's checkbox stages it (`add`); unticking unstages it. *Stage all* / *Unstage all* buttons act on a whole group. |
| FR-GUI-6 | P0 | Selecting any file shows its diff in the **diff viewer**: removed lines on a red background, added lines on green, hunk headers in grey, with old and new line numbers, rendered with a monospaced font (a `JList` with a custom cell renderer, so line colours span the full width). |
| FR-GUI-7 | P0 | The **commit box** holds a multi-line message. The *Commit* button is disabled while the message is empty or nothing is staged, and its label shows the staged count. |
| FR-GUI-8 | P0 | **History tab** — a `JTable` of commits (short hash, message, author, date), newest first. Selecting a commit shows its full details and the list of files it changed; selecting a file shows that file's diff. |
| FR-GUI-9 | P0 | **Branch selector** in the toolbar — a combo box listing all branches with the current one selected. Choosing another branch performs a checkout. |
| FR-GUI-10 | P0 | *New Branch* opens a dialog for the name (validated per FR-BR-3), with an option to switch to it immediately. |
| FR-GUI-11 | P0 | When checkout is refused (FR-CO-3), the GUI shows a dialog listing the files that would be overwritten, not a silent failure. |
| FR-GUI-12 | P0 | Every error is shown as a `JOptionPane` message with a plain-English explanation. The GUI never crashes or shows a stack trace. |
| FR-GUI-13 | P0 | Operations that touch the disk run on a background thread (`SwingWorker`), so the window never freezes. A busy cursor or progress indicator shows during the operation. |
| FR-GUI-14 | P0 | A status bar shows the current branch, counts of staged / unstaged / untracked files, and the short hash of HEAD. |
| FR-GUI-15 | P1 | The view refreshes automatically when the window regains focus, and on *Refresh* / F5. |
| FR-GUI-16 | P1 | Right-click on a file in the Changes tab: *Discard changes* (restore, after confirmation), *Stop tracking* (`rm --cached`), *Add to .minigitignore*. |
| FR-GUI-17 | P1 | **Objects tab** — a `JTree` browsing the selected commit's tree: folders expand into subtrees, and clicking a file shows its blob hash and content. Makes the object model visible during the viva. |
| FR-GUI-18 | P1 | Right-click on a commit in History: *Checkout this commit* (detached HEAD, with warning), *Create branch here*, *Create tag here*, *Reset to here*. |
| FR-GUI-19 | P1 | *Merge* button opens a dialog to pick a branch to merge into the current one; the result (fast-forward / already up to date / conflict) is shown in a dialog. |
| FR-GUI-20 | P1 | Keyboard shortcuts: Ctrl+Enter commit, F5 refresh, Ctrl+O open repository, Ctrl+B new branch. |
| FR-GUI-21 | P1 | *File → Recent Repositories* lists the last 5 opened folders, stored with `java.util.prefs.Preferences`. |
| FR-GUI-22 | P2 | The History tab draws a **commit graph** column (dots and branch lines) with Java2D, showing where branches split and merge. |
| FR-GUI-23 | P2 | In a merge conflict, conflicted files appear in their own group with *Mark as resolved*. |
| FR-GUI-24 | P2 | Side-by-side diff mode (old on the left, new on the right) as an alternative to the unified view. |
| FR-GUI-25 | P2 | Live refresh using `java.nio.file.WatchService` when files change on disk. |
| FR-GUI-26 | P2 | Light / dark theme toggle, implemented by recolouring Swing `UIManager` defaults. |

---

## 6. Data model

### 6.1 Object formats (content before compression)

**Blob**
```
blob 23\0<raw file bytes>
```

**Tree** — one entry per line, sorted by name. MiniGit uses a readable text format
rather than Git's binary one, for easier debugging.
```
tree 118\0
100644 blob 3a7bd3e2360a3d29eea436fcfb7e44c735d117c4	README.md
040000 tree 9c1185a5c5e9fc54612808977ee8f548b2258d31	src
```
Modes: `100644` normal file, `040000` directory.

**Commit**
```
commit 191\0
tree 9c1185a5c5e9fc54612808977ee8f548b2258d31
parent 5d41402abc4b2a76b9719d911017c592ae1c6c3e
author Mohit <mohit@example.com> 1790496000 +0530

Add login page
```

### 6.2 Index file

```
3a7bd3e2360a3d29eea436fcfb7e44c735d117c4 23 1790495900000 README.md
8f14e45fceea167a5a36dedd4bea2543a4c3f5b1 412 1790495950000 src/Main.java
```
Fields: blob hash, size in bytes, last-modified (epoch ms), path. Sorted by path.

### 6.3 Refs

- `HEAD` — either `ref: refs/heads/<branch>` or a 40-character commit hash.
- `refs/heads/<branch>` — one line, a commit hash.
- `refs/tags/<tag>` — one line, a commit hash.

### 6.4 Proposed class structure

| Package | Classes | Responsibility |
|---|---|---|
| `minigit` | `Main` | Entry point; no args → GUI, args → CLI; catches all errors |
| `minigit.api` | `MiniGit` (facade), `StatusResult`, `FileChange`, `CommitInfo`, `DiffResult`, `MergeResult` | The single service layer both front ends call — **Facade pattern** |
| `minigit.gui` | `MiniGitApp`, `MainWindow`, `WelcomePanel`, `ChangesPanel`, `DiffViewer`, `CommitPanel`, `HistoryPanel`, `ObjectsPanel`, `BranchSelector`, `StatusBar` | Swing UI components |
| `minigit.gui.dialogs` | `NewBranchDialog`, `MergeDialog`, `ConfigDialog` | Modal dialogs |
| `minigit.gui.model` | `ChangesTableModel`, `CommitTableModel`, `CommitTreeModel` | Swing models over facade results — **MVC** |
| `minigit.gui.util` | `BackgroundTask`, `Theme`, `DiffStyler` | `SwingWorker` wrapper, colours and fonts, diff syntax colouring |
| `minigit.cli` | `CommandParser`, `Command` (interface), `CommandRegistry` | Argument parsing, dispatch — **Command pattern** |
| `minigit.cli.commands` | `InitCommand`, `AddCommand`, `CommitCommand`, `LogCommand`, `StatusCommand`, `DiffCommand`, `BranchCommand`, `CheckoutCommand`, `MergeCommand`, … | One class per user command |
| `minigit.core` | `Repository`, `ObjectStore`, `Index`, `RefStore`, `Config`, `WorkingTree` | Repository services |
| `minigit.model` | `GitObject` (abstract), `Blob`, `Tree`, `TreeEntry`, `Commit` | Object types — **inheritance + polymorphism** (`serialize()` / `parse()`) |
| `minigit.diff` | `LineDiff`, `Edit`, `Hunk`, `UnifiedFormatter` | Diff algorithm and output |
| `minigit.merge` | `MergeBaseFinder`, `ThreeWayMerger` | Graph traversal (BFS) and merging |
| `minigit.util` | `Hashing`, `Compression`, `PathUtils`, `IgnoreRules` | Shared helpers |
| `minigit.exception` | `MiniGitException`, `NotARepositoryException`, `ObjectNotFoundException` | Custom exceptions with user-friendly messages |

---

## 7. Non-functional requirements

### 7.1 Correctness and data safety

| ID | Requirement |
|---|---|
| NFR-SAFE-1 | No command may destroy uncommitted work without explicit confirmation or a flag (`--hard`, `--force`). |
| NFR-SAFE-2 | Ref and index updates are written to a temporary file then atomically renamed (`Files.move` with `ATOMIC_MOVE`), so a crash mid-write never leaves a half-written file. |
| NFR-SAFE-3 | Committed objects are never modified or deleted by any command. |
| NFR-SAFE-4 | Files are read and written as raw bytes; text encoding is only applied for diff display (UTF-8). |

### 7.2 Performance

| ID | Requirement |
|---|---|
| NFR-PERF-1 | `add`, `commit`, and `status` complete in under 1 second for a repository of 500 files / 5 MB. |
| NFR-PERF-2 | `log` over 1,000 commits completes in under 1 second. |
| NFR-PERF-3 | `diff` of two 2,000-line files completes in under 1 second (LCS table of ~4M cells). |

### 7.3 Portability and build

| ID | Requirement |
|---|---|
| NFR-PORT-1 | 100% Java 17+, standard library only — including the GUI (Swing / AWT / Java2D). No third-party runtime dependencies. JUnit 5 is used for tests only and is not shipped. |
| NFR-PORT-2 | Works identically on Windows, macOS, and Linux. Paths inside `.minigit` always use `/`. |
| NFR-PORT-3 | Builds with Maven into a single runnable JAR. `java -jar minigit.jar` opens the GUI; `java -jar minigit.jar <command>` runs the CLI. |
| NFR-PORT-4 | Line endings are preserved byte-for-byte; no CRLF conversion. |

### 7.4 Code quality and testing

| ID | Requirement |
|---|---|
| NFR-QA-1 | JUnit 5 unit tests for hashing, object round-trips (serialize → parse), index read/write, LCS diff, and merge-base finding. |
| NFR-QA-2 | Integration tests that run full command sequences in a temporary directory (`init → add → commit → branch → checkout → diff`). |
| NFR-QA-3 | At least 70% line coverage on `core`, `model`, and `diff` packages. |
| NFR-QA-4 | Every user-facing error is a clear one-line message; stack traces appear only with a `--debug` flag. |
| NFR-QA-5 | Public classes and non-trivial methods carry Javadoc. |

### 7.5 GUI usability

| ID | Requirement |
|---|---|
| NFR-UX-1 | The GUI uses the operating system's native look and feel (`UIManager.getSystemLookAndFeelClassName()`). |
| NFR-UX-2 | All Swing components are created and updated only on the Event Dispatch Thread; disk work happens only off it. |
| NFR-UX-3 | Any action finishes, or visibly shows progress, within 200 ms of the click. |
| NFR-UX-4 | Minimum window size 1000 × 650; panels resize sensibly using split panes (`JSplitPane`). |
| NFR-UX-5 | Destructive actions (discard changes, hard reset, delete branch) always ask for confirmation first. |
| NFR-UX-6 | A first-time user can init a repo, make a commit, and view its diff through the GUI alone, without instructions. |

---

## 8. Release phasing

Sequenced so that each phase produces a working, demoable tool.

The engine is built first and driven from the CLI, because the CLI is the fastest way
to test it. The GUI is then layered on top of the finished facade.

### Phase 1 — Foundations *(week 1)*

Maven project skeleton, `Main` + command dispatch, `init`, `Hashing`,
`Compression`, `ObjectStore`, `Blob`, `cat-file`, `config`, `help`.
The `MiniGit` facade is created from day one, and CLI commands call only it.

**Outcome:** can hash a file, store it, and read it back — the storage engine works.

### Phase 2 — Snapshots *(week 2)*

Index, `add`, `Tree` building, `Commit`, `commit`, `log`, `status`, `.minigitignore`.

**Outcome:** a working single-branch version control engine, fully usable from the CLI.

### Phase 3 — Diff and time travel *(week 3)*

LCS diff and unified formatter, `diff` / `diff --staged` / `diff A B`, `show`,
`branch`, `checkout` (with the overwrite safety check), `tag`, `restore`, `reset`.

**Outcome:** the engine is feature-complete for the P0 scope.

### Phase 4 — GUI *(week 4)*

Welcome screen, main window, Changes tab with staging checkboxes, diff viewer,
commit box, History tab, branch selector, New Branch dialog, status bar, error
dialogs, `SwingWorker` background tasks (FR-GUI-1 to FR-GUI-14).

**Outcome:** every P0 operation available in the GUI. *This is the minimum viable submission.*

### Phase 5 — Merge and polish *(week 5, stretch)*

Fast-forward merge, merge base, three-way merge with conflict markers,
Objects tab, context menus, keyboard shortcuts, recent repositories, commit graph,
`log --graph`, test coverage, README, demo script.

**Outcome:** a complete, presentable project.

---

## 9. Success criteria

| Criterion | Target |
|---|---|
| All P0 requirements implemented and passing tests | 100% |
| P1 requirements implemented | At least 75% |
| Live demo script, **performed entirely in the GUI** (create repo → commit ×3 → new branch → edit → view diff → switch branch → merge) | Runs start to finish with no errors |
| Same repository opened in CLI and GUI | Both show identical status and history |
| Deduplication visible in demo | Committing an unchanged file twice creates zero new blobs, shown in the Objects tab / with `cat-file` |
| Crash-free on bad input | No stack trace for any malformed command in a 20-case negative test list |
| Viva readiness | Each team member can explain hashing, the object model, and the diff algorithm |

---

## 10. Assumptions

1. Tracked files are mostly text (source code, notes, Markdown).
2. Repositories are small: hundreds of files, not hundreds of thousands.
3. A single user operates on the repository at a time; no locking is needed.
4. The target machine has JDK 17 or later installed.
5. SHA-1 is acceptable for identifying objects; its known collision weaknesses do not matter for this use.
6. Symbolic links, file permissions beyond "normal file", and empty directories are not tracked.

---

## 11. Open questions

| # | Question | Needed by |
|---|---|---|
| Q1 | What is the submission deadline? This decides whether Phase 5 (merge) is in scope. | Phase 1 |
| Q2 | Is this a solo or team project? If a team, split by package (storage / commands / diff / GUI). | Phase 1 |
| Q3 | Is a report or UML class diagram also required? The class table in §6.4 is the starting point for one. | Phase 3 |
| Q4 | Should the tree format be Git's binary format instead of readable text, for extra authenticity? Costs a little more code; no functional gain. | Phase 2 |

### 11.1 Resolved

| Question | Decision |
|---|---|
| Is a GUI required? | **Yes** — GUI is P0 and the primary interface (§5.9). |
| Which technology? | **Java only.** GUI in Swing (part of the JDK). No JavaFX, no web, no external UI libraries. |
| Build tool? | Maven, for compiling, JUnit tests, and packaging the runnable JAR. |

---

## 12. Out of scope

Remote repositories (`clone`, `push`, `pull`, `fetch`) · Git on-disk compatibility ·
packfiles and delta compression · garbage collection · rebase and cherry-pick ·
submodules · hooks · signed commits · large-file support · multi-user locking ·
non-Java technologies (JavaFX, web UI, third-party UI libraries).
