# Setting up MiniGit

This guide takes you from a fresh machine to a running MiniGit, with the GUI,
the command line, and the tests. It takes about five minutes.

For what MiniGit does and how to use it, see [README.md](README.md). For the
full requirements, see [docs/PRD.md](docs/PRD.md).

---

## 1. Install Java 17 or later

MiniGit needs a **JDK** (not just a JRE), version **17 or newer**. Nothing else
is required: no Maven, no Git, no libraries.

Check what you have:

```sh
java -version
javac -version
```

Both must report 17 or higher. If they don't, or the commands are not found:

1. Install a JDK, for example [Eclipse Temurin 17 or 21](https://adoptium.net/)
   (free, all platforms). On Windows, tick **"Set JAVA_HOME"** and **"Add to PATH"**
   in the installer.
2. Open a **new** terminal and run the two commands again.

---

## 2. Get the project

Copy or clone the project folder onto your machine. It should contain:

```
pom.xml        mvnw        mvnw.cmd        .mvn/        src/        docs/
```

Open a terminal **in that folder** for the remaining steps.

---

## 3. Build

Windows (Command Prompt or PowerShell):

```bat
mvnw.cmd package
```

macOS / Linux / Git Bash:

```sh
./mvnw package
```

This compiles the code, runs all the tests, and produces **`target/minigit.jar`**.

The first build needs an internet connection: the Maven wrapper (`mvnw`)
downloads Maven 3.9.9 and the JUnit test library into your user folder
(`~/.m2`), a one-time download of about 10 MB. Later builds work offline.

To build without running the tests (faster):

```sh
mvnw.cmd package -DskipTests
```

A successful build ends with `BUILD SUCCESS`.

---

## 4. Run

### The GUI

```sh
java -jar target/minigit.jar
```

Or double-click `target/minigit.jar` in your file manager. The welcome screen
lets you **Open** an existing repository or **Create** a new one in any folder.

If you start it from inside a folder that already has a MiniGit repository,
that repository opens straight away.

### The command line

Pass a command after the JAR:

```sh
java -jar target/minigit.jar help
java -jar target/minigit.jar init
java -jar target/minigit.jar status
```

`help` lists every command, and `help <command>` explains one.

### Quick check that everything works

```sh
mkdir demo
cd demo
java -jar ../target/minigit.jar init
java -jar ../target/minigit.jar config user.name "Your Name"
echo hello > a.txt
java -jar ../target/minigit.jar add .
java -jar ../target/minigit.jar commit -m "First commit"
java -jar ../target/minigit.jar log
```

You should see your commit with its hash, your name, and the date.

---

## 5. Run the tests

```sh
mvnw.cmd test
```

All tests should pass (`Tests run: 125, Failures: 0, Errors: 0`). Detailed
reports are written to `target/surefire-reports/`.

---

## 6. Open in an IDE (optional)

MiniGit is a standard Maven project, so any Java IDE can open it directly:

| IDE | How |
|---|---|
| **IntelliJ IDEA** | *File → Open*, pick the project folder (or `pom.xml`), choose *Open as Project*. |
| **VS Code** | Install the *Extension Pack for Java*, then *File → Open Folder*. |
| **Eclipse** | *File → Import → Maven → Existing Maven Projects*, pick the project folder. |

Set the project JDK to 17 or later if the IDE asks.

- **Run the GUI:** run the `main` method in `src/main/java/minigit/Main.java` with no arguments.
- **Run a CLI command:** give it program arguments, e.g. `status`. Set the working
  directory to a folder containing a MiniGit repository.
- **Run the tests:** right-click `src/test/java` → *Run All Tests*.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `java` or `javac` is not recognised | The JDK is not on your `PATH`. Reinstall with "Add to PATH" ticked, or add the JDK's `bin` folder to `PATH`, then open a new terminal. |
| `UnsupportedClassVersionError` | The JAR was built with a newer Java than the one running it. Run `java -version` and install JDK 17+. |
| `JAVA_HOME not found` / `JAVA_HOME is set to an invalid directory` from `mvnw` | Set `JAVA_HOME` to your JDK folder, e.g. `C:\Program Files\Java\jdk-17`, then open a new terminal. |
| `./mvnw: Permission denied` (macOS/Linux) | Run `chmod +x mvnw` once. |
| First build fails with a download or connection error | The wrapper needs internet once to fetch Maven and JUnit. Check your connection or proxy, then build again. |
| Double-clicking the JAR opens it as a ZIP archive | Right-click → *Open with* → your Java runtime (listed as *Java(TM) Platform SE binary* or *OpenJDK Platform binary*), or run `java -jar target/minigit.jar` from a terminal. |
| `No display is available for the GUI` | You are on a machine without a desktop (e.g. over SSH). Use the command line instead: `java -jar target/minigit.jar help`. |
| `fatal: not a minigit repository` | Run the command inside a folder where you ran `minigit init`, or one of its subfolders. |
| `tell MiniGit who you are first` | Run `java -jar target/minigit.jar config user.name "Your Name"` in the repository. The GUI asks for this automatically before your first commit. |

---

## Where MiniGit keeps things

| What | Where |
|---|---|
| A repository's history and settings | the `.minigit/` folder inside that repository |
| The GUI's "recent repositories" list | Java Preferences for your user account (on Windows, the registry under `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\minigit`) |
| Maven and JUnit, downloaded by the wrapper | `~/.m2/` in your user folder |

Deleting a repository's `.minigit/` folder deletes its whole history but leaves
your files alone.
