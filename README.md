# Kai

Kai reads your team's documents and tells you which ones need updating when something
changes. You type the change in plain words, for example *"We now require Java 21 instead
of 17"*, and Kai lists every document that mentions it, with the reason.

Kai reads Word (.docx), PowerPoint (.pptx), PDF and text files. It never changes a file
without your approval, and it keeps a backup of every file before it changes it.

## Getting started

### 1. Put two files in one folder

You need two files, and they must be in the **same folder** (for example a folder called
`Kai` on your desktop):

| File | What it is |
|---|---|
| `kai.jar` | The Kai program (needs Java 25 installed) |
| `kai.properties` | Kai's settings: which folders to check, and where to keep backups |

### 2. Tell Kai which folders to check

Open `kai.properties` with a plain text editor (**TextEdit** on Mac, **Notepad** on Windows)
and change these two lines:

```
kai.scan.local=./sampleDocs
kai.backup-dir=./kai-backups
```

- `kai.scan.local` is the folder with your documents. `./` means "the folder Kai is in".
  To check several folders, separate them with commas:
  `kai.scan.local=~/Documents/Runbooks, C:\Team\Docs`
- `kai.backup-dir` is where Kai keeps copies of your original files. Kai creates it for you.
- On Windows, paste folder paths as they are, for example from right-click → **Copy as path**.
  `C:\Team\Docs` and `"C:\Team\Docs"` both work.
- Box Drive folders work like any other folder. On a Mac, right-click the folder in Finder, hold
  **Option** and choose **Copy as Pathname**.
- Lines that start with `#` are notes. Kai ignores them.

Save the file. Your Kai admin may also give you values for the **AI model** section.

### 3. Start Kai

Open a terminal (**Terminal** on Mac, **Command Prompt** on Windows) in the Kai folder and run:

```
java -jar kai.jar
```

After a few seconds Kai prints its address. Open **http://localhost:8080** in your browser.

**Keep the terminal window open** while you use Kai. Closing it stops Kai.

### 4. Use Kai

Type the change in the box at the bottom and click **Scan documents**. Kai checks every
file and shows:

| Result | Meaning |
|---|---|
| **Must change** | The file mentions the change. Kai can update it for you |
| **Update by hand** | The file mentions the change, but it is a Word, PowerPoint or PDF file. Open it and change it yourself |
| **Could not check** | Kai could not read the file or reach the AI. Check this file yourself, or try again |
| **No change needed** | Click to see the list. These files do not need updating |

## If something goes wrong

Kai checks its settings when it starts. If something is wrong, the terminal window explains
what to fix, then waits for you to press Enter. Fix `kai.properties`, save it, and start
Kai again.

| Message | What to do |
|---|---|
| *Kai could not find its settings file* | Put `kai.properties` in the same folder as `kai.jar`, and start Kai from that folder. Check the name: it must be exactly `kai.properties` (Windows sometimes hides the ending and saves it as `kai.properties.txt`) |
| *Folder to scan not found* | Check the path after `kai.scan.local`. On Windows use `/`, not `\` |
| *kai.backup-dir is missing* | Add the line `kai.backup-dir=./kai-backups` |
| *Nothing to scan* | Add a `kai.scan.local=...` line with at least one folder |
| *Box folders are not supported yet* | Put a `#` in front of the `kai.scan.box` line |
| *Unknown setting* | A setting name is misspelled. Compare it with the list in the message |
| *Kai could not start* | Another program may be using port 8080, or Kai is already running. Close the other Kai window, or remove the `#` in front of `server.port` in `kai.properties` and change 8080 to another number, such as 8090 |
| *The AI key is not set* / *The AI address is not set* | Fill in the **AI model** section of `kai.properties` with the values from your Kai admin |
| *The AI service refused the key* | The key is wrong or has expired. Ask your Kai admin for a new one |
| *The AI service does not know this address or model* | Check the address (it must end with `/v1`) and the model name with your Kai admin |
| *Kai cannot reach the AI service* | Check that you are online, and on the company VPN if you need one |
| Many files show **Could not check** | If you see "429" or "rate limit", set `kai.scan.parallel=1`. Otherwise, start Kai again: it checks the AI connection when it starts |

---

## For developers

### Requirements

- JDK 25 (the build fails on any other version, see the enforcer rule in `pom.xml`)
- Environment variables `ICA_CODEX_KEY` and `ICA_BASE_URL_V1` (the base URL must end in `/v1`)

### Run from the IDE or command line

IntelliJ: run `KaiApplication` with no arguments. The default working directory is the
project root, so it picks up `./kai.properties`, which scans `./sampleDocs`.

To use another settings file, add a program argument:

```
--config=/path/to/kai.properties
```

Command line:

```
./mvnw spring-boot:run
./mvnw spring-boot:run -Dspring-boot.run.arguments=--config=/path/to/kai.properties
```

### How kai.properties is found and loaded

`KaiApplication.main` calls `KaiConfig.load` **before Spring starts**, so mistakes give a
plain message and exit code 1 instead of a stack trace.

1. `--config=<path>`, if given (removed before the args reach Spring).
2. Otherwise `./kai.properties` in the working directory (IDE, `java -jar`, `spring-boot:run`).

After Spring starts, `ModelCheck` checks that `spring.ai.openai.api-key` and `base-url` are
set (an unresolved `${ICA_CODEX_KEY}` counts as not set), that the URL ends in `/v1`, and
makes one tiny model call. 401/403, 404 and network errors become plain messages and
exit code 1; 429 counts as working. Only then is the URL printed.
A black-holed address can take a few minutes to fail (60 s timeout, SDK retries 3 times).

Relative paths in the file are resolved against the file's own folder. The checked
`kai.*` values become the `KaiProperties` bean (registered by an initializer, not
`@ConfigurationProperties`). The same file is then passed to Spring as
`--spring.config.additional-location`, so any `spring.*` / `server.*` key in it overrides
`application.properties`.

| Key | Default | Rule |
|---|---|---|
| `kai.scan.local` | | Comma-separated folders; each must exist. Needed unless another target is set |
| `kai.scan.box` | | Placeholder for the Box adapter; setting it is an error for now |
| `kai.backup-dir` | **required** | Created if missing |
| `kai.scan.parallel` | `4` | Whole number, 1 or more |
| any other `kai.*` | | Error: "Unknown setting" (catches typos) |

To add a target type (e.g. Box): write a `@Component implements DocumentRepository` whose
`type()` is `box`, then turn the `kai.scan.box` check in `KaiConfig` into a `Target("box", url)`.

### Build the jar

```
./mvnw package                      # -> target/kai-0.0.1-SNAPSHOT.jar
```

Ship it renamed to `kai.jar`, together with a `kai.properties`.

See `PLAN.md` for the architecture and the stage plan.
