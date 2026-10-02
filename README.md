# Kai

Kai reads your team's documents and tells you which ones need updating when something
changes. You describe the change in plain words, for example *"We now require Java 21
instead of 17"*, or you paste a few paragraphs of release notes. Kai then checks every
document in the folders you chose, lists the ones that mention the change, explains why,
and proposes the edit.

What Kai can do:

- **Find affected documents.** Kai reads Word (.docx), PowerPoint (.pptx), PDF and text
  files (.md, .txt and similar) in one or more folders, including Box Drive and other synced folders.
- **Propose the edits.** For text files Kai writes the new wording and shows it as a
  before/after view (red = removed, green = added). A second AI check reviews each edit and
  tells you if something looks off.
- **Let you decide.** You can change any proposed text, or leave a file out. Nothing is
  written until you click **Finalize**.
- **Keep you safe.** Before changing anything, Kai backs up the original files and saves a
  report of every change. If a single file fails to save, Kai puts **all** files back as they were.

Word, PowerPoint and PDF files are listed when they need a change, but you update those
yourself. Kai only edits text files for now.

This file has two parts:

1. **For users** (below): what you need, how to set Kai up, and how to use it. No
   technical knowledge needed.
2. **For developers** (at the bottom): how the project is built, its structure, and how
   each way of running Kai is made.

---

# Part 1: For users

## Two ways to get Kai

Your Kai admin gives you Kai in one of two forms. Both work the same once Kai is running.
The only difference is what you need on your computer and how you start it.

| | **A. Kai program** (recommended) | **B. Kai jar** |
|---|---|---|
| What you get | `kai.exe` (Windows) or `kai` (Mac), plus `kai.properties` | `kai.jar`, plus `kai.properties` |
| What you need to install | Nothing | Java 25 |
| How you start it | Double-click the program | Type one command in a terminal |
| Size | About 200 MB | About 150 MB, plus Java |
| Best for | Most people | People who already have Java, or use Linux |

**Why two ways?** Kai is written in Java, a language that needs a helper program (the "Java
runtime") to run. Option **B** is Kai on its own, so you have to install Java first. Option
**A** is the same Kai with its own private copy of Java packed inside, so you just
double-click it. Option A is bigger, but there is nothing to install and nothing to type.

In both cases `kai.properties` is Kai's settings file. It tells Kai which folders to check
and where to keep backups. It must be in the **same folder** as the program.

## 1. Put Kai in a folder

Make a folder on your own computer, for example `Documents\Kai` (Windows) or
`Documents/Kai` (Mac), and put the files from your admin in it.

- **Option A, Windows:** right-click `kai-windows.zip` → **Extract All…** and pick that
  folder. Do not start Kai from inside the zip. It cannot find its settings there.
- **Option A, Mac:** double-click `kai-macos.zip`, then move the `macos` folder where you
  like (you can rename it to `Kai`).
- **Option B:** copy `kai.jar` and `kai.properties` into the folder.

Do not put the Kai folder inside Box Drive, OneDrive or another synced folder. Option A
unpacks about 200 MB of its own files next to itself (in a folder called `.kai-runtime`),
and you do not want that uploaded.

## 2. Set up kai.properties

Open `kai.properties` in a plain text editor: **Notepad** on Windows (right-click → **Open
with** → **Notepad**), **TextEdit** on Mac (right-click → **Open With** → **TextEdit**).
Do not use Word.

A few rules for the whole file:

- A line that starts with `#` is a note. Kai ignores it. To switch a setting on, remove the `#`.
- Each setting is one line: `name=value`. Do not change the part before `=`.
- Paths such as `./docs` start from the folder where `kai.properties` is. `~/` means your
  home folder, for example `~/Documents/Runbooks`.
- **Windows:** paste folder paths as they are, for example from right-click → **Copy as
  path**. `C:\Team\Docs` and `"C:\Team\Docs"` both work.
- **Mac:** right-click the folder in Finder, hold **Option**, and choose **Copy "…" as Pathname**.

The settings, in the order you find them in the file:

| Setting | What to put there |
|---|---|
| `kai.scan.local` | **Required.** The folder or folders with your documents. Kai also checks every folder inside them. Separate several folders with commas: `kai.scan.local=~/Documents/Runbooks, C:\Team\Docs`. Box Drive folders work like any other folder, for example `~/Library/CloudStorage/Box-Box/Team Docs` on a Mac. Files that are only in the cloud and not downloaded are skipped |
| `# kai.scan.box` | Leave it as it is, with the `#`. Box web links are not supported yet |
| `kai.backup-dir` | **Required.** Where Kai keeps the original files and a report of every change. Kai creates it. It must **not** be inside a folder you check, for example `kai.backup-dir=~/kai-backups` |
| `kai.scan.parallel` | How many files Kai checks at the same time. Leave it at `4`. Lower it to `2` or `1` if you see "429" or "rate limit" |
| `spring.ai.openai.…` | The AI connection: key, address and model. **Your Kai admin tells you what to put here.** Keep the key private, like a password. `${...}` means "take the value from this computer's settings"; if your admin did not set that up, replace the whole `${...}` with the value |
| `# server.port=8080` | Only if Kai says the port is in use: remove the `#` and change `8080` to another number, such as `8090` |

**Save the file**, and keep the name exactly `kai.properties`.

## 3. Start Kai

### Option A: the Kai program

**Windows:** double-click `kai.exe`. A black window opens. The first time, Windows may show
**"Windows protected your PC"**. Click **More info** → **Run anyway**. This happens because
Kai is an in-house program, not one from a store.

**Mac:** double-click `kai`. A Terminal window opens. The first time, the Mac may say
**"kai" cannot be opened** or **"Apple could not verify…"**. Click **Done**, then open
**System Settings** → **Privacy & Security**, scroll down and click **Open Anyway** next to
the message about `kai`. On older Macs: right-click `kai` → **Open** → **Open**. You only
do this once.

The **first start takes longer** (up to a minute), because Kai unpacks its own copy of Java.
Later starts are faster.

### Option B: the Kai jar

1. Install **Java 25** if you do not have it yet (ask your admin, or download "Temurin 25"
   from adoptium.net). To check, open a terminal and type `java -version`. It should say `25`.
2. Open a terminal in the Kai folder. **Windows:** open the folder in File Explorer, click
   the address bar, type `cmd` and press Enter. **Mac:** right-click the folder in Finder →
   **New Terminal at Folder** (or open Terminal and type `cd ` followed by the folder path).
3. Type this and press Enter:

```
java -jar kai.jar
```

### Both options

Kai checks your settings and the AI connection, then shows:

```
Kai is running at http://localhost:8080
Keep this window open while you use Kai. Close it to stop Kai.
```

Open **http://localhost:8080** in your browser (Chrome, Edge, Safari, …).

**Keep the window open** while you use Kai. To stop Kai, close the window, or press
**Ctrl+C** in it.

## 4. Use Kai

Type the change in the box at the bottom and click **Scan documents**. You can write one
sentence or paste release notes several paragraphs long. **Enter** starts a new line; only
the button sends.

While Kai works, a live log shows what it is doing for each file. The results appear by
themselves when the scan is done. You can refresh the page in the meantime. One scan runs
at a time.

The results are grouped by **Location**: each folder from `kai.scan.local`, with how many
of its files are affected (hover to see the full path). Each file gets one of these results:

| Result | Meaning |
|---|---|
| **Must change** | The file mentions the change. Kai shows its proposed edit below it |
| **Could not apply** | The file must change, but Kai could not place its edit in the text. Update it yourself |
| **Update by hand** | The file mentions the change, but it is a Word, PowerPoint or PDF file. Open it and change it yourself |
| **Could not check** | Kai could not read the file or reach the AI. Check this file yourself, or try again |
| **No change needed** | Click to see the list. These files do not need updating |

Under each **Must change** file you see:

- **What changes:** removed lines in red (−), new lines in green (+). Unchanged parts are folded.
- **The reviewer's note:** "looks right", or what to check.
- **Edit:** opens the proposed text so you can change it. Click **Save edits** and the
  red and green view updates.
- **Include this file:** untick it to leave that file as it is.

Saving edits does **not** change your files yet.

When you are happy, click **Finalize** and confirm. Kai then:

1. Checks that no file changed since the scan. If one did, Kai writes nothing and asks you
   to scan again, so it never overwrites someone else's newer work.
2. Saves a report of every change (before and after) and backs up the original files,
   both in your backup folder.
3. Changes the ticked files. If changing any file fails, Kai puts **all** of them back from
   the backup and says **Rolled back**, so your files are exactly as before.

The reply has an **Open the report** link.

## If something goes wrong

Kai checks its settings and the AI connection when it starts. If something is wrong, the
window says what to fix and waits for you to press **Enter**. Fix `kai.properties`, save
it, and start Kai again.

| Message | What to do |
|---|---|
| *Kai could not find its settings file* | Put `kai.properties` in the same folder as Kai. With option B, also start Kai from that folder. Check the name: Windows sometimes saves it as `kai.properties.txt`. In File Explorer turn on **View** → **Show** → **File name extensions** to see the full name |
| *Folder to scan not found* | Check the path after `kai.scan.local`. Copy it again from File Explorer or Finder |
| *kai.backup-dir is missing* | Add the line `kai.backup-dir=./kai-backups` |
| *The backup folder … is inside a folder Kai scans* | Choose a backup folder outside your document folders, for example `kai.backup-dir=~/kai-backups` |
| *Nothing to scan* | Add a `kai.scan.local=...` line with at least one folder |
| *Box folders are not supported yet* | Put a `#` in front of the `kai.scan.box` line |
| *Unknown setting* | A setting name is misspelled. Compare it with the list in the message |
| *kai.scan.parallel must be a whole number* | Use a number such as `4`, without spaces or letters |
| *The AI key is not set* / *The AI address is not set* | Fill in the AI connection with the values from your Kai admin |
| *The AI address must start with https:// / end with /v1* | Fix `spring.ai.openai.base-url` with your Kai admin |
| *The AI service refused the key* | The key is wrong or has expired. Ask your Kai admin for a new one |
| *The AI service does not know this address or model* | Check the address and the model name with your Kai admin |
| *Kai cannot reach the AI service* | Check that you are online, and on the company VPN if you need one |
| *Kai could not start: unpacking Java failed* (option A) | Kai needs to write into its own folder. Move it somewhere you can save files, such as `Documents` (not `Program Files` or a read-only shared drive), and check that your disk is not full |
| *java is not recognized* / *command not found: java* (option B) | Java is not installed. Install Java 25, or ask your admin for option A |
| *UnsupportedClassVersionError* (option B) | Your Java is too old. Install Java 25 |
| *Kai could not start. The reason is shown above.* | Often "Port 8080 was already in use": Kai may already be running in another window. Use that one, or close it, or change `server.port` |
| Many files show **Could not check** | If you see "429" or "rate limit", set `kai.scan.parallel=1`. Otherwise start Kai again: it checks the AI connection when it starts |

## Updating or removing Kai

When you get a new version, replace only the program (`kai.exe`, `kai` or `kai.jar`). Keep
your own `kai.properties` and your backup folder. Close Kai before you replace it. With
option A, the new version cleans up the old `.kai-runtime` files by itself.

To remove Kai completely, delete the Kai folder. Keep your backup folder if it is somewhere
else and you still need it.

---

# Part 2: For developers

## What it is

Kai is a Spring Boot 4.1 web app (Java 25, Thymeleaf, Spring AI 2.0.1 with the OpenAI
starter) that runs locally on the user's machine and talks to an OpenAI-compatible
gateway. The user only ever sees one chat page at `/`. Behind it, a plain-Java
orchestrator runs three LLM agents per file:

```
ChatController (UI at /)  ── the only thing users interact with
      │
      ▼
Orchestrator  ── plain Java; fixed pool of kai.scan.parallel threads, one task per file
  ├─ ScannerAgent   "Is this file affected? why?"            -> Verdict(affected, reason)
  ├─ EditorAgent    "Which passages change, and to what?"    -> Edits[(original, replacement)]
  └─ ReviewerAgent  "Does the edit do only what was asked?"  -> Review(ok, note)
      │
      ▼
DocumentRepository (adapter: type / list / read / write)  ── LocalFileRepository today
ChangeWriter ── stale check → report → byte backup → write → roll back all on failure
```

Documents are read through Apache Tika (docx, pptx, pdf) or as plain text. Only text files
are writable (`DocumentRepository.canWrite`). Edits are passage replacements that must
match exactly once (`Patch`), so untouched parts of a file stay byte-identical. See
`PLAN.md` for the full design and stage plan.

## Project structure

```
kai/
├── pom.xml                      Spring Boot 4.1.1, Spring AI 2.0.1, Tika 4.0.0; enforcer: JDK 25 only
├── kai.properties               dev settings (scans ./sampleDocs); also the template shipped to users
├── PLAN.md                      architecture, flow and stage plan
├── src/main/java/com/example/kai/
│   ├── KaiApplication.java      main: KaiConfig.load → Spring → ModelCheck → prints the URL
│   ├── chat/                    ChatController: the single UI (/), scan job, /progress, /save, /finalize, /report
│   ├── orchestrator/            Orchestrator, Finding (+ Report), Proposal, Patch, Diff, Progress
│   ├── agent/
│   │   ├── scanner/             ScannerAgent
│   │   ├── editor/              EditorAgent
│   │   └── reviewer/            ReviewerAgent
│   ├── repository/              DocumentRepository + LocalFileRepository (Tika for Office/PDF)
│   ├── writer/                  ChangeWriter: stale check, report, backup, write, rollback
│   └── config/                  KaiConfig (finds and checks kai.properties before Spring),
│                                KaiProperties (the result), ModelCheck (AI connection at startup)
├── src/main/resources/
│   ├── application.properties   built-in defaults; kai.properties overrides them
│   └── templates/               chat.html (the UI), final-report.html (saved report)
├── sampleDocs/                  demo documents (git-ignored)
└── packaging/                   mechanism 2: one executable per OS
    ├── build.sh                 builds dist/ from an input folder (jar + JREs + kai.properties + README)
    ├── launcher/main.go         Go launcher that embeds the Java runtime and kai.jar
    ├── README.md                user guide shipped inside the packages
    ├── build/                   your build input folder (git-ignored)
    └── dist/                    output: windows/, macos/, kai-windows.zip, kai-macos.zip (git-ignored)
```

## Requirements

- JDK 25. The build fails on any other version (enforcer rule in `pom.xml`).
- Environment variables `ICA_CODEX_KEY` and `ICA_BASE_URL_V1`, or put the values straight
  into `kai.properties`. The base URL must start with `https://` and end in `/v1`: the
  OpenAI Java SDK only appends `chat/completions`.
- For mechanism 2 only: a Mac, Go (`brew install go`), and a Java 25 runtime for each target OS.

## Run from the IDE or command line

IntelliJ: run `KaiApplication` with no arguments. The working directory is the project
root, so it picks up `./kai.properties`, which scans `./sampleDocs`. To use another
settings file, add the program argument `--config=/path/to/kai.properties`.

```
./mvnw spring-boot:run
./mvnw spring-boot:run -Dspring-boot.run.arguments=--config=/path/to/kai.properties
```

## How kai.properties is found and loaded

Both mechanisms below depend on this. `KaiApplication.main` calls `KaiConfig.load`
**before Spring starts**, so mistakes give a plain message, wait for Enter, and exit with
code 1 instead of a stack trace.

1. `--config=<path>`, if given (removed before the args reach Spring). The packaged
   launcher always passes this, pointing next to the executable.
2. Otherwise `./kai.properties` in the working directory (IDE, `java -jar`, `spring-boot:run`).

Relative paths in the file are resolved against the file's own folder, not the working
directory. Backslashes are kept literally (so Windows paths paste as-is) and surrounding
quotes are stripped; as a side effect, `\` escapes and line continuations do not work in
this file. The checked `kai.*` values become the `KaiProperties` bean (registered by an
initializer, not `@ConfigurationProperties`). A copy of the file is then passed to Spring
as `--spring.config.additional-location`, so any `spring.*` / `server.*` key in it
overrides `application.properties`.

| Key | Default | Rule |
|---|---|---|
| `kai.scan.local` | | Comma-separated folders; each must exist. Needed unless another target is set |
| `kai.scan.box` | | Placeholder for a Box adapter; setting it is an error for now |
| `kai.backup-dir` | **required** | Created if missing; must not be inside a scanned folder |
| `kai.scan.parallel` | `4` | Whole number, 1 or more |
| any other `kai.*` | | Error: "Unknown setting" (catches typos) |

After Spring starts, `ModelCheck` checks that `spring.ai.openai.api-key` and `base-url`
are set (an unresolved `${ICA_CODEX_KEY}` counts as not set) and well-formed, and makes one
tiny model call. 401/403, 404 and network errors become plain messages and exit code 1;
429 counts as working. Only then is the URL printed. A black-holed address can take a few
minutes to fail (60 s timeout, SDK retries 3 times).

## Two ways to ship Kai

| | **Mechanism 1: jar** | **Mechanism 2: packaged executable** |
|---|---|---|
| Artifact | `kai.jar` (Spring Boot fat jar) | `kai.exe` / `kai` (Go binary with JRE + jar embedded) |
| User needs | JDK/JRE 25 on the PATH | Nothing |
| Start | `java -jar kai.jar` from the folder with `kai.properties` | Double-click; finds `kai.properties` next to itself |
| Config lookup | `--config=` or `./kai.properties` (working directory) | Launcher passes `--config=<exe folder>/kai.properties` |
| Platforms | Any OS with Java 25 (incl. Linux) | Windows x64, macOS arm64 or x64 (one build per OS) |
| Size | ~150 MB | ~195 MB per OS (compressed runtime + jar) |
| Build | `./mvnw package` | `./mvnw package` + 2 JREs + `packaging/build.sh` |
| Signing prompts | None | SmartScreen (Windows), Gatekeeper (macOS) on first start |

Both run the **same jar**: mechanism 2 is only a wrapper around mechanism 1. There is no
second code path to test.

### Mechanism 1: run as a jar

**Why.** It is the simplest artifact a Spring Boot app can have: one file, one command,
works on every OS, and it is exactly what you run in development. It suits developers,
testers, Linux users, and machines where Java 25 is already managed by IT.

**How it works.** `spring-boot-maven-plugin` repackages the build into an executable fat
jar: your classes plus every dependency (Spring, Tomcat, Spring AI, Tika, POI, PDFBox)
under `BOOT-INF/lib`, with a Spring Boot loader as `Main-Class`. `java -jar` starts the
loader, which starts `KaiApplication.main`. Since there is no `--config`, `KaiConfig` looks
for `./kai.properties` in the **working directory**, which is why users must start it from
the Kai folder.

**How to build and ship it.**

```
./mvnw package                       # -> target/kai-0.0.1-SNAPSHOT.jar
cp target/kai-0.0.1-SNAPSHOT.jar dist/kai.jar
cp kai.properties dist/              # point kai.scan.local / kai.backup-dir at user folders first
```

Give the user `kai.jar` and `kai.properties` in one folder. They run:

```
java -jar kai.jar                                  # uses ./kai.properties
java -jar kai.jar --config=/path/to/kai.properties # or an explicit file
```

**Trade-off.** Users must install the right Java (25; older Java fails with
`UnsupportedClassVersionError`) and use a terminal. For non-technical users that is the
main source of setup trouble, which is what mechanism 2 removes.

### Mechanism 2: package as an executable with kai.properties

**Why.** End users are non-technical. They should download one zip, edit one settings file,
and double-click, with no Java install, no PATH, and no terminal commands. GraalVM native
image was tried and dropped: Tika, POI, PDFBox and the OpenAI SDK rely on reflection and
would need a lot of untested native configuration. `jpackage` was not a fit either: it
produces an app folder or installer per OS and has to run on that OS. A small Go launcher gives a
single file per OS, can be cross-built for Windows and macOS from one Mac, and runs the
unchanged jar on a normal JVM.

**How it works at runtime** (`packaging/launcher/main.go`):

1. The binary contains `payload.tar.gz` (a Java 25 runtime as `jre/` plus `kai.jar`) via
   `go:embed`.
2. On start it unpacks the payload to `<exe folder>/.kai-runtime/<first 6 bytes of the
   payload's sha256>/`. It unpacks into a temp folder (`unpack-*`) and renames it at the end,
   so a half-finished unpack (power cut, two starts at once) is never used. If the folder
   already exists, it skips unpacking, so later starts are fast.
3. After a fresh unpack it deletes the folders of older versions (renamed to `trash-*`
   first; on Windows a still-running old version keeps its files locked, so it is left for
   the next start). Users never have to clean up.
4. It runs `<runtime>/jre/bin/java -jar <runtime>/kai.jar --config=<exe folder>/kai.properties`
   (unless the user passed their own `--config=`), with stdin/stdout/stderr attached, so
   Kai's messages and "press Enter" prompts appear in the same window. Ctrl+C reaches Java,
   which shuts down cleanly; the launcher exits with Java's exit code.

Why the exe folder and not a user cache folder: deleting the Kai folder removes everything.
The leading dot hides `.kai-runtime` in Finder, and `LocalFileRepository` skips hidden
paths, so Kai never scans its own runtime even if the folder is inside a scan target.

**How to build it** (on a Mac):

1. Build the jar: `./mvnw package`.
2. Get a Java 25 runtime for each target: a Windows x64 one and a macOS one (arm64 by
   default). The simplest is the Temurin 25 **JRE** download for each OS from adoptium.net,
   unzipped. You can make them smaller with `jlink` (only the modules Kai needs), but a
   jlinked runtime must be built from the target OS's JDK `jmods`, with the same JDK version.
3. Make an input folder (for example `packaging/build/`, which is git-ignored) with exactly:

```
packaging/build/
├── kai-0.0.1-SNAPSHOT.jar   exactly one *.jar (renamed to kai.jar inside the payload)
├── winjre/                  Windows runtime, must contain bin/java.exe
├── macjre/                  macOS runtime, must contain bin/java
├── kai.properties           the settings template users get
└── README.md                exactly one README* (copy of packaging/README.md)
```

4. Run:

```
packaging/build.sh packaging/build                 # Apple Silicon macjre (default)
MAC_ARCH=amd64 packaging/build.sh packaging/build  # if macjre is an Intel runtime
```

For each OS, `build.sh` tars `jre/` + `kai.jar` into `launcher/payload.tar.gz`
(`tar -h` turns symlinks into plain files, `COPYFILE_DISABLE` keeps out macOS `._` files),
cross-compiles the launcher with `GOOS`/`GOARCH` and `CGO_ENABLED=0`, copies
`kai.properties` and the README next to it, and zips the folder (the zip keeps the Mac
executable bit). The output is:

```
packaging/dist/
├── windows/   kai.exe, kai.properties, README.md
├── macos/     kai, kai.properties, README.md
├── kai-windows.zip
└── kai-macos.zip
```

Ship the zip for the user's OS. Each build has a new payload hash, so a new version
unpacks into a fresh folder and never mixes files with the old one.

**Things to know.**

- The binaries are not signed. Windows SmartScreen and macOS Gatekeeper warn on first
  start; `packaging/README.md` tells users how to get past that. Signing (and notarizing on
  macOS) would remove the prompts but needs a code-signing certificate and Apple developer account.
- The first start unpacks about 200 MB, so it takes up to a minute. The Kai folder must be
  writable (not `Program Files`, not a read-only share) and should not be in a synced folder.
- Windows is x64 only. For Linux, use mechanism 1 (or add a `build linux amd64 ...` line
  and a Linux runtime to `build.sh`).
- The JRE inside must be Java 25, the same version the jar is built for.

## Extending

To add a document source (for example Box): write a `@Component implements
DocumentRepository` whose `type()` is `box`, then turn the `kai.scan.box` check in
`KaiConfig` into a `Target("box", url, entry)`. Spring injects every repository into the
`Orchestrator`, and backup and rollback go through the same interface, so they work for
the new source too.
