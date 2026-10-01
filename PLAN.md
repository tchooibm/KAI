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
Spring injects all of them into the Orchestrator; each `kai.scan.<type>` key in kai.properties
(e.g. `kai.scan.local`) is a target scanned by the adapter whose `type()` matches.

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
└── config/        KaiConfig: finds + checks kai.properties before Spring starts; KaiProperties: the result
src/main/resources/templates/chat.html
kai.properties     user settings; lives next to kai.jar (or project root in the IDE)
README.md          how to run (users first, developers at the bottom)
sampleDocs/        10 cloud-onboarding test documents (git-ignored)
```

## Sample documents (`sampleDocs/`)

The scanner reads every format. Only text files (md, txt, yml, …) can be rewritten by Kai;
docx, pptx and pdf are read with Apache Tika and marked **Update by hand** when affected.

| File | Topic | Acme Portal | Java 17 | Port 8080 | support@ |
|---|---|:-:|:-:|:-:|:-:|
| README.md | Onboarding overview | ✓ | ✓ | | ✓ |
| landing-zone-setup.md | Accounts, OUs, regions | ✓ | | | |
| app-onboarding-runbook.md | Container deploy steps | | ✓ `temurin:17` | ✓ | |
| tagging-and-cost-policy.md | Tags and budgets | | | | |
| iam-access-onboarding.docx | SSO roles, access requests | ✓ | | | ✓ |
| network-vpc-guide.docx | Subnets, security groups | | | ✓ | |
| security-baseline.pdf | Patching, encryption | | ✓ `JDK 17` | | |
| dr-backup-policy.pdf | RPO/RTO tiers | | | | |
| onboarding-checklist.pptx | 5-step checklist deck | ✓ | ✓ | ✓ | |
| cloud-101-training.pptx | New-joiner training | | (says "Java" only) | | |

Expected scan results (LLM may vary slightly):

| Change request | Affected | Of which by hand |
|---|---|---|
| Rename the product from Acme Portal to Kai Hub | 4 | 2 (docx, pptx) |
| We now require Java 21 instead of 17 | 4 | 2 (pdf, pptx) |
| Change the application port from 8080 to 9090 | 3 | 2 (docx, pptx) |
| Support email is now help@kaihub.example | 2 | 1 (docx) |

`tagging-and-cost-policy.md`, `dr-backup-policy.pdf` and `cloud-101-training.pptx` are true
negatives. The training deck mentions Java as a supported language, so it checks that the
scanner doesn't flag a file just for naming a related topic.

## Configuration

`kai.properties` is required and is checked before Spring starts (`KaiConfig`); any problem
is listed in plain words and the app exits. Where it is looked for: `--config=<path>` →
`./kai.properties` in the working directory. Relative paths start from
the file's folder. Full rules and run instructions: `README.md`.

| Key | Default | Purpose |
|---|---|---|
| `kai.scan.local` | | Folders to scan, comma-separated. Each must exist |
| `kai.scan.box` | | Box **file** shared links, comma-separated (stage 2b). Folder links need an API token: not supported yet |
| `kai.scan.parallel` | `4` | Files checked at the same time. Lower it on 429 rate-limit errors. Must be ≥ 1 |
| `kai.backup-dir` | **required** | Where originals are backed up before writing. Created if missing |
| `spring.ai.openai.*`, `server.port`, … | from application.properties | Any Spring Boot / Spring AI key |

Unknown `kai.*` keys are errors, so typos don't get silently ignored.

Build: `./mvnw package` → `target/kai-0.0.1-SNAPSHOT.jar`.

## Stages (review each before moving on)

| # | Stage | LLM? | Done when |
|---|-------|------|-----------|
| 1 | Skeleton: sample docs, `DocumentRepository` + `LocalFileRepository`, orchestrator, **stub** scanner, report table | No | Typing a change request in the chat at `/` returns a table of every file with affected yes/no from a keyword stub ✅ built |
| 2 | ScannerAgent: real LLM, structured output `Verdict(affected, reason)`; per-file Error status | Yes | On `sampleDocs/`, the four requests above give the expected counts; docx/pptx/pdf are read (Tika) and shown as "Update by hand"; bad model → "Could not check" rows, never "No change" ✅ built |
| 2b | **Box (cloud) + local together**: `BoxRepository` reads Box files by public shared link, read-only | No (scan uses LLM as before) | Two sample docs moved from `sampleDocs/` to Box: the Java 21 request still finds 4 affected / 2 by hand, Box rows show their source and open in Box. Bad or folder link → plain startup message. ✅ Done instead through a Box Drive folder in `kai.scan.local` (see below) |
| 3 | EditorAgent + ReviewerAgent → **review in the chat**: the bot reply shows, per file: a **diff** of original vs proposed, an Edit toggle (editable textarea of the proposed text), include checkbox, reviewer note. "Save edits" button | Yes | You can change a proposal, save, reload, and still see your edit (diff updated). Editor + Reviewer run in parallel. Nothing is written to disk |
| 4 | **Finalize**: write final HTML report (before/after for each selected file) → back up originals → write → if any write fails, restore every file from the backup | No | Happy path: files changed and report saved. Forced failure: every file identical to before, and the chat says "Rolled back" |
| 5 | Demo polish: "simulate failure" checkbox (fails on the last file) to show rollback live, agent log panel, clearer errors | No | 2-minute demo runs cleanly |

Stage 2b outcome (2026-10-01): **done through Box Drive, no Box code.** The hackathon has no
access to Box or SharePoint through developer tokens or APIs: no developer account, and public
links need a login. Box Drive shows Box as a local folder, so it goes in `kai.scan.local` like
any other folder, e.g. `/Users/<you>/Library/CloudStorage/Box-Box/kaitest`. Kai reads it
locally. Only files that Box Drive shows in the folder (`ls`) are scanned. `kai.scan.box` stays
a placeholder that is rejected at startup. The plan below is parked.

Parked plan (shared links, needs links that download without login):

- **Spike first (5 min, on your machine):** confirm a public file link downloads without
  login: `curl -L -o test.pdf https://app.box.com/shared/static/<id>` (same `<id>` as the
  `https://app.box.com/s/<id>` link). If it doesn't, stop and get a developer token instead.
- **Config:** `kai.scan.box=<link>, <link>` takes **file** shared links (`/s/<id>` or
  `/shared/static/<id>`), comma-separated like `kai.scan.local`. Links must be shared with
  "People with the link" and allow download.
- **Startup check (KaiConfig):** each link must be https on a box.com host and actually
  download; otherwise a plain message ("This Box link can't be downloaded: is it a folder, or
  is download turned off?"). Folder links can't be listed without an API token, so they are
  reported as "not supported yet — share the files one by one".
- **`BoxRepository`** (`type()` = `box`): `list(link)` returns the file name (from the
  download's `Content-Disposition`); `read()` downloads with `java.net.http.HttpClient` and
  extracts text with Tika, same size limits as local; `canWrite()` = false.
- **Read-only:** affected Box files show as "Update by hand", with the file name linking to the
  Box page. Writing back to Box (and rollback through Box) is a later stage; it needs a token
  with edit rights.
- **Report:** local and Box files in one table; each row shows its source (`local` / `box`).
- **Later, with a token:** folder links via the Box API (`/2.0/shared_items` + folder items),
  then write-back. Same adapter, no changes elsewhere.

Stage 3 requirements (agreed, not started):

- **Parallel:** Editor + Reviewer run per file in parallel, in the same `kai.scan.parallel`
  pool as the scan (Reviewer runs after the Editor for the same file). A failure on one file
  shows as an error on that file only and never blocks the others.
- **Diff, not two boxes:** each file shows a line diff of original → proposed (removed lines
  red, added lines green). "Edit" opens the proposed text in a textarea; after "Save edits"
  the diff is recalculated from your edited text.
- Only editable (text) files get a proposal; affected docx/pptx/pdf stay "Update by hand".
- **Passage edits, any size:** the Editor returns a list of `(original passage → new passage)`
  edits, not the whole file. A passage can be a sentence, a paragraph or a whole section, and
  the new passage can be fully rewritten, longer, or add new paragraphs (anchored to a
  neighbouring passage). Changes are not limited to word swaps: e.g. a feature release that
  changes how other features work → the affected sections are rewritten properly. Everything
  not in an edit stays byte-for-byte identical, so the diff shows only real changes. If the
  whole document needs rewriting, that is one edit whose passage is the entire file.
- **Applying edits:** Kai applies an edit only if its original passage appears **exactly once**
  in the file. If any edit can't be placed, Kai retries the Editor **once automatically**,
  telling it which passages didn't match. Still failing → that file shows "Could not apply"
  (no guessing); other files are unaffected.
- **Reviewer** checks the edited result against the change request: does it do what was
  asked, nothing unrelated, and was anything affected missed?
- **Multi-line request box:** the chat input becomes a textarea, so users can paste release
  notes or a feature description several paragraphs long. Enter only adds a new line (never
  sends); the only way to send is clicking **Scan documents**. No keyboard shortcut.

Stage 4 also detects **stale files**: if a file changed on disk after the scan,
Finalize refuses to overwrite it, so your review can't silently clobber newer content.

## Demo script (draft)

1. *"Rename the product from Acme Portal to Kai Hub"*: easy, keyword-level.
2. *"We now require Java 21 instead of 17"*: the LLM catches `Java 17`, `JDK 17`
   (in a PDF) and `temurin:17`, but skips the training deck that only says "Java".
3. In review, hand-edit one proposal (e.g. change "Temurin" to "any JDK 21"), untick one file.
4. Finalize → open the report → show files changed.
5. Run again with "simulate failure" → show the chat says "Rolled back" and the files are unchanged.
