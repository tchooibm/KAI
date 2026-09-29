# Kai — hackathon plan

Input a change request → agents find affected files and propose edits → **you review
and edit the proposals** → final HTML report → backup → write → auto-rollback on failure.

## Flow

```
[Scan] ──> [Review & edit] ──> [Finalize] ──────────────────────────────┐
 agents     you: tick/untick     1. final report  <backup-dir>/<runId>/report.html
 propose    files, edit the      2. backup        <backup-dir>/<runId>/files/...
            proposed text,       3. write all selected files
            save as often as     4. any write fails -> restore ALL from backup
            you like             5. chat reply: Applied / Rolled back
```

Nothing touches disk until you click **Finalize**. Until then, proposals and your
edits live in the HTTP session.

## Architecture

```
ChatController (UI: /)  ── the ONLY thing users interact with
      │
      ▼
Orchestrator  ── plain Java, decides the order and passes results between agents.
                 Agents have no UI and never talk to the user.
  ├─ ScannerAgent   "Is this file affected? why?"            (1 LLM call per file)
  ├─ EditorAgent    "Rewrite this file to apply the change"  (affected files only)
  └─ ReviewerAgent  "Does the edit do only what was asked?"  (advice shown to you)
      │
      ▼
DocumentRepository (adapter interface: type / list / read / write)
  ├─ LocalFileRepository   folder on disk              ← MVP
  ├─ SharePointRepository  Graph API                   (future)
  └─ OneDriveRepository    Graph API                   (future)

ChangeWriter ── report → backup → write → rollback-on-failure (no LLM)
               backups are stored in <backup-dir> (from the config file), and it reads/writes
               only via DocumentRepository, so rollback works for any adapter
```

Adding a repository = one `@Component implements DocumentRepository` in `repository/`.
Spring injects all of them into the Orchestrator; select one with `kai.scan.repository`.

## Project structure

```
src/main/java/com/example/kai/
├── KaiApplication.java
├── chat/          ChatController: the single user interface (/ and /chat)
├── orchestrator/  Orchestrator, Finding (+ Report): coordinates agents
├── agent/
│   ├── scanner/   ScannerAgent: stage 1 stub, stage 2 LLM
│   ├── editor/    (stage 3) EditorAgent
│   └── reviewer/  (stage 3) ReviewerAgent
├── repository/    DocumentRepository + all adapters (LocalFileRepository; later SharePoint, OneDrive)
├── writer/        (stage 4) ChangeWriter: report, backup, write, rollback
└── config/        KaiProperties: binds kai.* from kai.properties
src/main/resources/templates/chat.html
kai.properties     user-editable config, passed with --config
demo-docs/         sample files for the demo
```

## Configuration

Users edit `kai.properties` and pass it in at startup:

```
java -jar kai.jar --config=/path/to/kai.properties
./mvnw spring-boot:run -Dspring-boot.run.arguments=--config=/path/to/kai.properties
```

Leaving out `--config` uses `./kai.properties`. It holds every setting (app, model,
scan target, backup) and overrides the built-in `application.properties`.

| Key | Default | Purpose |
|---|---|---|
| `kai.scan.repository` | `local` | Which adapter to scan with. Unknown value → startup fails |
| `kai.scan.location` | `.` | What to scan (local: folder path) |
| `kai.backup-dir` | **required** | Where originals are backed up before writing |
| `spring.ai.openai.*`, `server.port`, … | from application.properties | Any Spring Boot / Spring AI key |

A wrong `--config` path, or a blank `kai.backup-dir`, stops startup immediately.
Later settings (e.g. SharePoint credentials) go in the same file under `kai.*`.

## Stages (review each before moving on)

| # | Stage | LLM? | Done when |
|---|-------|------|-----------|
| 1 | Skeleton: `demo-docs/`, `DocumentRepository` + `LocalFileRepository`, orchestrator, **stub** scanner, report table | No | Typing a change request in the chat at `/` returns a table of every file with affected yes/no from a keyword stub ✅ built |
| 2 | ScannerAgent: real LLM, structured output (affected + reason) | Yes | Java-17 request flags `config.yml` (`temurin:17`) and skips CHANGELOG/FAQ |
| 3 | EditorAgent + ReviewerAgent → **review in the chat**: the bot reply shows, per file: original (read-only), proposed (editable textarea), include checkbox, reviewer note. "Save edits" button | Yes | You can change a proposal, save, reload, and still see your edit. Nothing is written to disk |
| 4 | **Finalize**: write final HTML report (before/after for each selected file) → back up originals → write → if any write fails, restore every file from the backup | No | Happy path: files changed and report saved. Forced failure: every file identical to before, and the chat says "Rolled back" |
| 5 | Demo polish: "simulate failure" checkbox (fails on the last file) to show rollback live, agent log panel, clearer errors | No | 2-minute demo runs cleanly |

Stage 4 also detects **stale files**: if a file changed on disk after the scan,
Finalize refuses to overwrite it, so your review can't silently clobber newer content.

## Demo script (draft)

1. *"Rename the product from Acme Portal to Kai Hub"*: easy, keyword-level.
2. *"We now require Java 21 instead of 17"*: the LLM catches `JDK 17`,
   `temurin:17` and `java.version=17`, which grep would miss.
3. In review, hand-edit one proposal (e.g. change "Temurin" to "any JDK 21"), untick one file.
4. Finalize → open the report → show files changed.
5. Run again with "simulate failure" → show the chat says "Rolled back" and the files are unchanged.
