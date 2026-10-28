# Kai — hackathon plan

Input a change request → agents find affected files and propose edits → **you review
and edit the proposals** → final HTML report → copy of the originals → write → auto-rollback on failure.

## Flow

```
[Scan] ──> [Review & edit] ──> [Finalize] ──────────────────────────────┐
 agents     you: tick/untick     1. final report  <history>/<run>/Report.html
 propose    files, edit the      2. originals     <history>/<run>/Original files/<scan folder>/...
            proposed text,       3. write all selected files
            save as often as     4. any write fails -> restore ALL from backup
            you like             5. chat reply: Applied / Rolled back
```

Nothing touches disk until you click **Finalize**. Until then, proposals and your
edits live in the HTTP session.

## Architecture

```
StartController (/start)  ── start page, first in every browser session: settings only
ChatController (UI: /)  ── where users work; agents never have a UI
      │
      ▼
Orchestrator  ── plain Java, decides the order and passes results between agents.
                 Agents have no UI and never talk to the user.
  ├─ ExtractorAgent "What changed in the updated file?"      (optional, once, before the scan)
  ├─ ScannerAgent   "Is this file affected? why?"            (1 LLM call per file)
  ├─ EditorAgent    "Which passages change, and to what?"    (affected text files only)
  └─ ReviewerAgent  "Does the edit do only what was asked?"  (advice shown to you)
      │
      ▼
DocumentRepository (adapter interface: type / list / read / write)
  ├─ LocalFileRepository   folder on disk              ← MVP
  ├─ SharePointRepository  Graph API                   (future)
  └─ OneDriveRepository    Graph API                   (future)

ChangeWriter ── report → backup → write → rollback-on-failure (no LLM)
               runs are kept in the history folder (kai.backup-dir), with History.html; it reads/writes
               only via DocumentRepository, so rollback works for any adapter
```

Adding a repository = one `@Component implements DocumentRepository` in `repository/`.
Spring injects all of them into the Orchestrator; each `kai.scan.<type>` key in kai.properties
(e.g. `kai.scan.local`) is a target scanned by the adapter whose `type()` matches.

## Project structure

```
src/main/java/com/example/kai/
├── KaiApplication.java
├── chat/          StartController: start page (/start, settings); ChatController: the chat (/ and /chat)
├── orchestrator/  Orchestrator, Finding (+ Report), Proposal, Patch (applies edits), Diff (line diff)
├── agent/
│   ├── extractor/ ExtractorAgent: changes found in an updated file (stage 6)
│   ├── scanner/   ScannerAgent: stage 1 stub, stage 2 LLM
│   ├── editor/    EditorAgent: passage edits (original -> replacement)
│   └── reviewer/  ReviewerAgent: ok + note on the edited result
├── repository/    DocumentRepository + all adapters (LocalFileRepository; later SharePoint, OneDrive)
├── writer/        ChangeWriter: stale check, report, originals, write, rollback; History: the history folder
└── config/        KaiConfig: finds, reads, checks, writes kai.properties; KaiProperties: checked kai.* values;
                   Setup: holds them, start-page logic; ModelProvider: the AI connection
src/main/resources/templates/start.html, chat.html
kai.properties.template  settings template (version controlled); copied to kai.properties for dev and packaging
kai.properties     user settings, not version controlled; lives next to kai.jar (or project root in the IDE)
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

`kai.properties` never stops Kai. Every browser session opens on the **start page**, which reads
the file fresh (blank fields if it is missing), checks each setting as the user edits it (with
each problem under its field), always lists the models from the AI service, and saves working
values back on **Start Kai** (creating the file if needed). Where it is looked for:
`--config=<path>` → `./kai.properties` in the working directory. Relative paths start from
the file's folder. Full rules and run instructions: `README.md`.

| Key | Default | Purpose |
|---|---|---|
| `kai.scan.local` | | Folders to scan, comma-separated. Each must exist |
| `kai.scan.box` | | Box **file** shared links, comma-separated (stage 2b). Folder links need an API token: not supported yet |
| `kai.scan.parallel` | `4` | Files checked at the same time. Lower it on 429 rate-limit errors. Must be ≥ 1 |
| `kai.backup-dir` | **required** | The history folder: a folder per Finalize (`Report.html`, `Original files/`), plus `History.html`. Created if missing. Must not be inside a scanned folder |
| `spring.ai.openai.api-key`, `.base-url`, `.chat.model` | | The AI connection (OpenAI-compatible); set on the start page |
| `server.port`, other `spring.*` | from application.properties | Any Spring Boot key |

Unknown `kai.*` keys are errors, so typos don't get silently ignored.

Build: `./mvnw package` → `target/kai-0.0.1-SNAPSHOT.jar`; `packaging/build.sh` runs it and builds the packages.

## Stages (review each before moving on)

| # | Stage | LLM? | Done when |
|---|-------|------|-----------|
| 1 | Skeleton: sample docs, `DocumentRepository` + `LocalFileRepository`, orchestrator, **stub** scanner, report table | No | Typing a change request in the chat at `/` returns a table of every file with affected yes/no from a keyword stub ✅ built |
| 2 | ScannerAgent: real LLM, structured output `Verdict(affected, reason)`; per-file Error status | Yes | On `sampleDocs/`, the four requests above give the expected counts; docx/pptx/pdf are read (Tika) and shown as "Update by hand"; bad model → "Could not check" rows, never "No change" ✅ built |
| 2b | **Box (cloud) + local together**: `BoxRepository` reads Box files by public shared link, read-only | No (scan uses LLM as before) | Two sample docs moved from `sampleDocs/` to Box: the Java 21 request still finds 4 affected / 2 by hand, Box rows show their source and open in Box. Bad or folder link → plain startup message. ✅ Done instead through a Box Drive folder in `kai.scan.local` (see below) |
| 3 | EditorAgent + ReviewerAgent → **review in the chat**: the bot reply shows, per file: a **diff** of original vs proposed, an Edit toggle (editable textarea of the proposed text), include checkbox, reviewer note. "Save edits" button | Yes | You can change a proposal, save, reload, and still see your edit (diff updated). Editor + Reviewer run in parallel. Nothing is written to disk ✅ built |
| 4 | **Finalize**: write final HTML report (before/after for each selected file) → back up originals → write → if any write fails, restore every file from the backup | No | Happy path: files changed and report saved. Forced failure: every file identical to before, and the chat says "Rolled back" ✅ built (forced-failure test deferred, see below) |
| 4b | **docx/pptx write-back** (after stage 4, decided 2026-10-01): Apache POI, edits only inside one paragraph; anything else stays "Could not apply" / "Update by hand". PDF stays by hand | No (edits come from the Editor as now) | Sample docx/pptx edited, open in Word/PowerPoint without a repair prompt, rollback restores them byte-for-byte |
| 6 | **Updated file as the change** (2026-10-08): pick the updated file + type a summary → ExtractorAgent lists the changes → user confirms/edits → scan other files (updated file skipped) | Yes | See "Stage 6" below ✅ built (uncompiled) |
| 7 | **Large files + small model** (built and user-tested 2026-10-28; ripple pass moved to stage 9): extract once, split into small sections, route by search terms, yes/no scan per section, edit by paragraph number, Java checks + Reviewer | Yes | See "Stage 7" below. A 500 MB pptx/pdf scans without truncation or running out of memory on an 8 GB laptop; `sampleDocs/` counts unchanged on a small local model |
| 8 | **Embeddings for routing, optional** (planned 2026-10-28): a small bundled embedding model finds sections that paraphrase the change; without it Kai falls back to Stage 7's text search | No (embedding model only) | See "Stage 8" below. With the model present, paraphrased references are routed; with it missing, Kai still starts and says routing is keywords only |
| 9 | **Contextual test case + model choice** (planned 2026-10-28, do later): a workflow gains a new pre-step; compare small models, with and without embeddings | Yes | See "Stage 9" below. Results table filled in; smallest good-enough model picked as the bundled default; embeddings kept only if they help |
| 5 | Demo polish: "simulate failure" checkbox (fails on the last file) to show rollback live, agent log panel (✅ built early: live progress log), clearer errors | No | 2-minute demo runs cleanly |

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

Stage 3 requirements (built 2026-10-01):

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

How stage 3 is built:

- `Orchestrator.check` runs scan → `propose` in one pool task per file, so edits overlap with
  other files' scans. `propose` = `EditorAgent` → `Patch.apply` → on `NoMatch`, one retry with
  the problems listed → `ReviewerAgent`. A reviewer failure keeps the proposal ("Not reviewed").
- `Patch.apply` locates every original in the *unchanged* file (exactly once, no overlaps),
  then splices the replacements in. Edits written with `\n` are matched against `\r\n` files.
- `Proposal` (mutable, in the session) holds original, proposed, include tick, edited flag and
  the review. `Diff` is a plain LCS line diff with 2 context lines; the rest is folded.
- The report table is grouped by target (`Report.groups()`): a Location column (the
  `kai.scan.*` entry as typed, `Target.entry`, plus "N of M affected") spans its files' rows,
  followed by one folded "No change needed" row per location.
- `POST /save` takes `report=<message index>`, `text<i>` and `include<i>` (i = finding index).
  Textarea `\r\n` is turned back into `\n` unless the file uses `\r\n`.

How stage 4 is built (2026-10-01):

- **Finalize** button next to Save edits (same form, `formaction=/finalize`), so unsaved edits
  count. Browser confirm first. `ChatController` → `Orchestrator.apply` → `ChangeWriter.apply`.
- Selected = ready proposal, ticked, and different from the original (`Finding.selected()`).
- Order: stale check (current file text must equal the scanned original, else refuse ALL,
  nothing written) → `Report.html` (status "Not written yet") → byte copies via
  `DocumentRepository.readBytes` to `<history>/<run>/Original files/<scan folder>/<file>` →
  write → on the first failure, restore every selected file with `writeBytes`. The report is
  saved again with the outcome. Run folder = `yyyy-MM-dd HH.mm <start of the request>` (was
  `yyyyMMdd-HHmmss` before 2026-10-08); `.kai-run.properties` and `History.html` are updated last.
- The chat reply links to `GET /report/<run folder>`, which serves the saved report (browsers block
  `file://` links from a web page). An applied report hides its buttons; after a rollback
  you can Finalize again.
- `KaiConfig` rejects a history folder inside a scanned folder, else reports and originals would be scanned.
- Forced-failure test: deferred to stage 5's "simulate failure" checkbox (user, 2026-10-01).

Live progress log (added after stage 4, 2026-10-01): `POST /chat` starts the scan on a virtual
thread and returns at once; the session holds a `Job(Progress, CompletableFuture<Report>)`.
The Orchestrator adds one line per agent step to `Progress`. The page polls
`GET /progress?since=n` every second and reloads when done; `GET /` turns a finished job into
the bot message (only request threads touch the history). One scan at a time per session.

Stage 4b notes (not started; text files only until then): read docx/pptx with POI per paragraph
(not Tika) so passages match what is written back; replace only the text pieces (runs) inside
the match so the rest keeps its formatting; cover body + tables (docx) and shapes + tables
(pptx), headers/footers/notes later; for Office files the Edit toggle edits each replacement
passage, not the whole text. Needs byte-level read/write in `DocumentRepository`, which
stage 4's backup should already provide. Estimate ~1 day (docx ~½ day, pptx +2–3 h).

Stage 4 also detects **stale files**: if a file changed on disk after the scan,
Finalize refuses to overwrite it, so your review can't silently clobber newer content.

Stage 6: updated file as the change (built 2026-10-08, uncompiled):

- **Why:** users often update one document first and want the rest to follow. There is no
  old version to diff (no snapshots, by choice), so the user's summary says what matters.
- **UI:** optional **Updated file** dropdown above the chat box (default "None", one group per
  `kai.scan.*` location). Picked → the box is the summary (required), the button reads
  **Find changes**. "None" → exactly the old flow.
- **Flow:** `POST /chat` with `source=<location position>:<file>` → `Orchestrator.source` (only
  files Kai would scan are accepted) → job runs `Orchestrator.extract` → `ExtractorAgent`
  returns `Changes(changes, notFound)` → bot message with an editable list (one change per
  line) and the not-found items as a warning. **Scan other files** (`POST /confirm`) makes
  the edited list the change request (`Extraction.instruction`) and runs the normal
  `scan(instruction, source, progress)`, which skips the updated file. Report and saved
  report show "Checked against <file>".
- **Why the pause:** the extracted list steers every proposed edit, so a misread is cheaper
  to fix here than after the whole scan. One click.
- Size: the updated file is read once, with the usual `LocalFileRepository` limits.

Stage 7: large files + small model (planned 2026-10-28; BUILT and user-tested 2026-10-28):

**Built and verified (TEST-PLAN.md A6, C1-C3 pass):** `Sectioner`, `Router` + `TermRouter`
(whole words, optional plural, neighbours), `ScannerAgent` per section with quote check,
`EditorAgent` by paragraph number, `Patch` by offset, Reviewer on changed sections only,
`OfficeText` (docx/pdf via Tika XHTML: headings -> `#`, pages -> `# Page N`, leading PDF
metadata dropped; pptx read from the zip in presentation order, notes as `## Notes`; Tika
listed slides out of order), coverage + location in each finding ("Where: Slide 3; Slide 3 >
Notes. (4 of 6 sections checked)"), `Diff` guard (no n×m table above 25M cells), launcher
`-XX:MaxRAMPercentage=70`, unit tests (`SectionPatchTests`, `OfficeTextTests`).
**Dropped 2026-10-28:** `kai.edit=propose|flag` and `kai.review=on|off` (6b and the switch in
8 below). User: "Kai should be a consistent experience" — one behaviour for every setup:
always propose (text files), always review. Stage 9 picks the model, not the mode.
**Moved to stage 9:** ripple pass (item 7 below): references to changed step numbers and counts
("go back to step 2"), built only after the stage 9 baseline shows which misses it fixes.
Related-phrasing model call (4) deferred to stage 8 (embeddings do the same job). Per-section `Diff` not needed (guard + small edits).

**Streaming (2026-10-28, after user testing):** Kai must not freeze the machine, so no document
is held whole in memory. `DocumentRepository.open` returns a `Reader` (text files read in place;
Office/PDF text extracted to a temp file, one extraction at a time, PDFBox buffered on disk above
64 MB). `Sectioner` hands out one section at a time; the Orchestrator checks a match plus its
neighbours as it streams, and keeps only the affected sections. `Proposal` holds those sections
(before/after, offsets, line) instead of the whole file; the review screen shows and edits one
box per changed section. Finalize: stale check by size + modified time, backup by `Files.copy`,
write by streaming the original through `Proposal.rewrite` into a temp file (each replaced section
is re-checked against the scanned text), then copied over the file; rollback copies the backup back.
The Extractor gets a big updated file's matching sections only (up to 60k chars). No JVM heap flag:
memory stays flat, so the default heap is enough. The extract-once cache (item 1) is dropped:
re-reading from disk is cheap and keeps memory flat.

- **Why:** files can be up to ~500 MB (md, docx, pptx, pdf), and Kai should run on any
  laptop, so the model should be as small as possible (target ~1–4B at Q4, CPU only).
  Today every agent gets the whole file in one prompt. Also, `tika.parseToString` stops at
  100,000 characters, so long Office/PDF files are silently cut off and the rest is
  reported as "No change needed". The line diff (`Diff`) uses an n×m table, which runs out
  of memory on very large text files.
- **Design rule:** the pipeline does the heavy lifting, the model only answers narrow
  questions about short passages. Anything Java can check, Java checks.
- **1. Extract once.** `LocalFileRepository.read` sets no character limit and skips images.
  The extracted text is cached by path + size + modified time (in memory for the session;
  a disk cache is a later option), so a second scan of the same file does not re-parse it.
- **2. Split into small sections.** A new `orchestrator/Section(id, headingPath, start, end, text)`.
  The split follows the document's own structure, never a fixed size: md by headings,
  docx by headings (Tika XHTML `h1..h6`), pptx one slide per section, pdf one page per
  section (Tika XHTML `div.page` / `div.slide`). A numbered list, table or procedure is never
  cut in two. Target ~500–1,000 tokens per section (small models lose accuracy on long
  prompts); a larger section is split at paragraph boundaries and each part keeps the
  heading path, e.g. "Onboarding > Setup > Steps". For text files `start`/`end` are offsets
  in the file, so the edits still land in the original text.
- **3. Router seam.** Routing sits behind a small interface (`Router`: change request +
  sections of a file → candidate sections). Stage 7 ships one implementation, the text
  search in (4). Stage 8 adds a second signal behind the same interface; nothing else changes.
- **4. Route: which sections might be affected (text search).** One model call turns the
  change request into search terms *and* related phrasings, e.g. "Java 17, JDK 17,
  temurin:17" or, for a workflow change, "onboarding steps, first step, step numbers,
  N-step process, prerequisites". A plain search (no model) finds them in each file;
  neighbouring sections of a hit are added. Lean toward recall: an extra section costs one
  cheap Scanner call, a missed one is silent. No model reading of outlines (too unreliable
  at 1–2B). Expected weak spot: references that paraphrase the change and share no words
  with it; stage 9 measures that, stage 8 is the planned fix.
- **5. Scan per section: a yes/no question.** `ScannerAgent` gets the change request, the
  file name, the heading path and one section, and answers only "must this passage change
  because of the request?". `Verdict(reason, quote, affected)` — reason first, so the model
  explains before it decides. `quote` must appear in the section (checked in Java); a quote
  that does not appear → retried once, then counted as an error, never as "No change".
  A file is affected if any section is.
- **6. Edit by paragraph number.** For an affected section the Editor sees the section (the
  whole procedure/list if the hit is inside one) with numbered paragraphs, and returns
  `Edits(List<Edit(paragraph, newText)>)`; `newText` may hold several paragraphs (to insert,
  rewrite or renumber). Kai swaps the paragraphs by offset, so an edit cannot fail to match;
  the "Could not apply" retry is no longer needed for these. Office/PDF stay "Update by
  hand" (stage 4b unchanged).
- **7. Ripple pass (moved to stage 9).** After the edits (or flags) for a file are known, Kai searches that file
  and the other scanned files for references to what changed: step numbers, counts
  ("three steps"), the procedure's heading or name. Hits not already checked go through the
  Scanner. This catches "go back to step 2" and "the 4-step process" elsewhere.
- **8. Review: Java checks first, model optional.** Always, in Java: every edit's paragraph
  exists, edits don't overlap, text outside the edited paragraphs is unchanged, each quote
  is real. The model Reviewer (diff of the changed sections only, not both whole files) always
  runs (no switch: one consistent experience). `Diff` runs per section, which also
  removes the memory problem.
- **9. Show coverage.** The report shows per file "N of M sections checked" with a folded
  list of skipped sections, so a miss is visible and the user can make the call.
- **Model settings:** temperature 0; `spec.useProviderStructuredOutput()` so llama-server /
  Ollama enforce the JSON schema while generating (essential at this size: tiny models
  break free-form JSON); fixed text (system prompt + change request) first and the section
  last, so the server can reuse its prompt cache; llama-server parallel slots (`-np`) equal
  to `kai.scan.parallel`. On CPU the cost is mostly reading the prompt, so routing (4) and
  small sections (2) are the main speed levers.
- **Progress log:** per file "Routing: 12 of 840 sections to check", then per section as now.
- **Not in this stage:** embeddings (stage 8), disk cache of extracted text, Office
  write-back (4b).
- **Open (check before coding):**
  - Exact Tika XHTML markers for slides/pages in Tika 4.0.0 (sources jar).
  - Section size: fixed target, or a key `kai.scan.section-tokens`?

Stage 8: embeddings for routing, optional (planned 2026-10-28, not started; needs stage 7):

- **Covers paraphrases, not numbering.** Embeddings find sections on the same topic that share no
  words with the request. They do not find "go back to step 2" when step 2 is renumbered: that
  needs the ripple pass (stage 9), which is plain Java and works without the embedding model.
- **Why:** text search (stage 7) misses references that paraphrase the change and share no
  words with it ("Before you deploy, you first need portal access…"). Similarity between
  the change request and each section finds those.
- **Optional by design.** The embedding model is one more bundled file (~25–100 MB,
  MiniLM class; the Java runtime is ~190 MB, the chat model 1–2.5 GB), but Kai does not
  depend on it. Present → routing uses similarity **and** text search (either is enough to
  make a section a candidate). Missing or failing to load → text search only; the start
  page says "Routing: keywords only, paraphrased references may be missed" and the report's
  "N of M sections checked" line shows the gap. Kai starts either way (config problems never
  make it exit).
- **Shipping:** a file in the package next to the program (like `models/`), found by its
  presence, so no new key is needed (proposed). Works air-gapped. The online package could
  use the gateway's `/v1/embeddings` if it has one; not assumed.
- **How it works:** each section (heading path + text) is embedded once at extraction and
  cached in memory with the text (a disk cache is a later option). The change request is
  embedded per scan; per file, the top-N sections above a threshold join the candidates.
  Plugs in as a second `Router` signal (stage 7, item 3).
- **Progress log / start page:** "Routing: 12 of 840 sections (9 by keywords, 3 by meaning)";
  start page shows whether the embedding model is loaded.
- **Open (check before coding):**
  - How to run it from Java without a second runtime: Spring AI's ONNX
    `TransformersEmbeddingModel` (check it exists in 2.0.1 and runs offline with a bundled
    model), or llama-server `--embedding` with a GGUF embedding model behind the
    OpenAI-compatible `/v1/embeddings`. Pick one, not both.
  - Similarity threshold and top-N per file (tune in stage 9).
- **Keep or drop:** decided by stage 9. If recall with embeddings is not clearly better than
  without, leave the model out of the package and keep Kai dependency-free.

Stage 9: contextual test case + model choice (planned 2026-10-28, do later):

- **Ripple pass (built here, after the baseline).** Run the current pipeline on the workflow case first and
  record misses. Then build the ripple pass: after edits, search the file and the other scanned files for
  the changed step numbers, counts and procedure names; unchecked hits go through the Scanner. Add it as a
  third column (with/without embeddings, with/without ripple) to show which misses each one fixes.
- **Why:** the four existing requests are fact changes with a literal hook ("8080"). A new
  step in a workflow has no hook: affected text says "the 4-step process", "after step 2"
  or "first, sign in to the portal". This stage measures how well Kai finds those, and picks
  the **smallest model that is good enough** to bundle.
- **Test documents** (added to `sampleDocs/`):
  - `app-onboarding-runbook.md` gets (or already has) a numbered deploy procedure, plus a
    later paragraph "If the build fails, go back to step 2".
  - `onboarding-checklist.pptx` ("5-step checklist deck") is the same workflow in slides.
  - `README.md` overview mentions "five steps" in prose, without listing them.
  - New `team-faq.md`: paraphrases the workflow ("Before you deploy, you first need
    portal access…") with no step numbers and none of the procedure's words.
  - New negative: `incident-process.md`, a different numbered procedure that must not change.
  - New large file: a generated ~300 MB pdf or pptx with the workflow on one page/slide near
    the end (past the old 100,000-character limit).
- **Change request:** "Add a new first step to app onboarding: request a cost-centre code
  before anything else. All later steps move down by one."
- **Expected spots** (fill in exact lines once the docs are written): the procedure in the
  runbook (new step + renumbering), the "go back to step 2" line, the checklist deck,
  "five steps" in README, the FAQ paraphrase, the page in the large file. Not affected:
  `incident-process.md` and the true negatives above.
- **Also re-run** the four fact-change requests from "Sample documents" (expected counts
  above) on each model, so a small model is not chosen on one test alone.
- **With vs without embeddings:** every model is run twice, embeddings on and off, to see
  whether the extra file earns its place (stage 8's keep-or-drop).
- **Models to compare** (Q4, CPU only; names to be checked when downloading): ~1–1.7B
  (e.g. Qwen3-1.7B, Qwen2.5-1.5B-Instruct, Llama-3.2-1B), ~3–4B (e.g. Qwen3-4B), plus the
  online model as the reference. Not below ~1B and not Q2 (structured output breaks).
  Each run is a change on the start page; no code changes.
- **Machine:** run on a modest laptop (8 GB RAM, no GPU) and write down its spec, since
  "runs easily on any laptop" is about speed as much as accuracy.
- **Measure per run:** spots found / expected (recall), wrongly flagged files, edits that
  were correct after review, scan time, model calls, peak RAM.
  Run on the current pipeline (if possible, as a baseline), after stage 7, and after stage 8.
- **Decide:** the smallest model with acceptable recall and few wrong flags becomes the
  bundled default; and whether the
  embedding model ships (see "With vs without embeddings").

| Run | Model | Found / expected | Wrongly flagged | Correct edits | Scan time | Model calls | Peak RAM |
|---|---|---|---|---|---|---|---|---|
| current pipeline | online | propose / on | | | | | | |
| after stage 7 | online | propose / on | | | | | | |
| after stage 7 | ~4B | propose / on | | | | | | |
| after stage 7 | ~1.5B | propose / off | | | | | | |
| after stage 7 | ~1.5B | flag / off | | | | | | |
| after stage 8 (embeddings on) | ~4B | propose / on | | | | | | |
| after stage 8 (embeddings on) | ~1.5B | propose / off | | | | | | |
| after stage 8 (embeddings on) | ~1.5B | flag / off | | | | | | |

## Demo script (draft)

1. *"Rename the product from Acme Portal to Kai Hub"*: easy, keyword-level.
2. *"We now require Java 21 instead of 17"*: the LLM catches `Java 17`, `JDK 17`
   (in a PDF) and `temurin:17`, but skips the training deck that only says "Java".
3. In review, hand-edit one proposal (e.g. change "Temurin" to "any JDK 21"), untick one file.
4. Finalize → open the report → show files changed.
4b. Updated file: edit the support email in `README.md` by hand, pick it under Updated file,
   type "support email changed", confirm the list → the docx shows "Update by hand".
5. Run again with "simulate failure" → show the chat says "Rolled back" and the files are unchanged.
