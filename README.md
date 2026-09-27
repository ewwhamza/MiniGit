# MiniGit

A lightweight version control system written in pure Java — Git's core ideas
(content-addressed objects, snapshots, branches, diffs) built from scratch.

See [docs/PRD.md](docs/PRD.md) for the full requirements and roadmap.

## Requirements

- JDK 17 or later. Nothing else — the Maven wrapper downloads Maven on first use.

## Build and test

```sh
./mvnw package          # Windows: mvnw.cmd package
```

This compiles, runs the tests, and produces `target/minigit.jar`.

## Run

### Desktop GUI

```sh
java -jar target/minigit.jar
```

Or double-click `target/minigit.jar`. Run from inside a repository, it opens that
repository straight away; otherwise it shows a welcome screen to open or create one.

- **Changes tab** — staged, unstaged, and untracked files. Tick a checkbox to
  stage a file, untick to unstage. Click a file to see its colour-coded diff.
  Write a message and press **Commit** (or Ctrl+Enter).
- **History tab** — every commit with its branch and tag labels. Click a commit
  to see its details and changed files; click a file to see its diff.
- **Branch box** (top right) — pick a branch to switch to it. **New Branch…**
  (Ctrl+B) creates one. MiniGit refuses to switch if it would overwrite
  uncommitted work, and lists the files.
- **Merge…** combines another branch into the current one. If both changed the
  same lines, a yellow banner appears and the files are marked **C**: edit them
  to remove the `<<<<<<<` `=======` `>>>>>>>` markers, tick them to stage, then
  press **Commit Merge** — or **Abort Merge** to undo.
- **Objects tab** — browse a commit's snapshot the way it is stored: commit →
  tree → subtrees and blobs, with each object's hash, file path, and content.
- **Right-click** a file (stage, discard changes, stop tracking, ignore) or a
  commit (check out, create branch or tag here, reset branch to here).
- F5 or **Refresh** re-reads the repository; it also refreshes whenever the
  window regains focus, so edits made in another program show up.

### Command line

```sh
java -jar target/minigit.jar <command> [args]
```

### Commands

| Command | What it does |
|---|---|
| `init [<dir>]` | Create an empty repository (`.minigit/`) |
| `add <path>...` | Stage files or folders (`add .` stages everything) |
| `rm [--cached] [-r] [-f] <path>...` | Stop tracking files, deleting them unless `--cached` |
| `commit -m <message>` | Record the staged changes as a commit |
| `status` | Show staged, unstaged, and untracked files |
| `diff [--staged \| <commit> <commit>] [--stat]` | Line-by-line changes (LCS algorithm) |
| `log [--oneline] [-n <count>]` | Show the commit history, newest first |
| `show [<commit>] [--stat]` | Show a commit and what it changed |
| `branch [<name> [<start>] \| -d/-D <name>] [-v]` | List, create, or delete branches |
| `checkout <branch> \| <commit> \| -b <name>` | Switch branches, or look at an old commit |
| `merge <branch> \| --abort` | Combine a branch into this one (fast-forward or three-way merge) |
| `tag [<name> [<commit>] \| -d <name>]` | Name a commit permanently, e.g. `v1.0` |
| `restore [--staged] <path>...` | Discard edits, or unstage files |
| `reset [<commit>]` | Move the branch back, keeping your files (`reset HEAD~1` undoes a commit) |

Anywhere a commit is expected you can write a branch, a tag, a hash prefix,
`HEAD`, or add `~N` / `^` to go back, e.g. `HEAD~2`.
| `hash-object [-w] <file>...` | Print a file's blob hash; `-w` also stores it |
| `cat-file [-t \| -s \| -p] <hash>` | Show a stored object (a 4+ character hash prefix works) |
| `config <key> [<value>]`, `--list`, `--unset <key>` | Read and write settings such as `user.name` |
| `help [<command>]` | List commands or show one command's usage |

Add `--debug` to any command to see a stack trace when something fails.

Files matching patterns in `.minigitignore` (e.g. `*.class`, `build/`) are skipped.

### Try it

```sh
mkdir demo && cd demo
java -jar ../target/minigit.jar init
java -jar ../target/minigit.jar config user.name "Your Name"
echo hello > a.txt
java -jar ../target/minigit.jar add .
java -jar ../target/minigit.jar commit -m "First commit"
java -jar ../target/minigit.jar log
java -jar ../target/minigit.jar cat-file -p <commit hash>    # see the commit object
java -jar ../target/minigit.jar cat-file -p <tree hash>      # and the tree it points to
```

## Project layout

```
src/main/java/minigit/
├── Main.java          entry point: no arguments → GUI, arguments → CLI
├── api/               MiniGit facade — the only thing the CLI and GUI call
├── cli/               argument parsing, command registry, one class per command
├── gui/               Swing GUI: MainWindow, panels, dialogs, table/list models
├── diff/              LCS line diff, hunks, unified formatter
├── merge/             three-way merge with conflict markers
├── core/              Repository, ObjectStore, Index, RefStore, Checkout, …
├── model/             GitObject, Blob, ObjectType, RawObject
├── util/              Hashing, Compression, FileUtils
└── exception/         user-facing error types
```
