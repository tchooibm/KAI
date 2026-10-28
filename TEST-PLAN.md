# Kai manual test plan

Run against the files in `sampleDocs/`. Claude refers to this file when planning or reviewing an
enhancement: add a test here whenever a feature is added, and re-run the affected sections before
calling a stage done. Record results in the table at the end.

## Setup

1. Build: `./mvnw -q clean package` (must be clean).
2. Copy the sample folders you need, so Finalize can be tried without touching the originals:
   `cp -r sampleDocs /tmp/kai-test`.
3. In `kai.properties` set `kai.scan.local=/tmp/kai-test/stage7, /tmp/kai-test/office` and a model.
4. Start Kai, open `/start`, press Check, then open the chat.
5. After each text-file test that is finalized, restore with `cp -r sampleDocs/stage7/. /tmp/kai-test/stage7/`.

"Routing N of M" is the progress-log line `Routing: <file> N of M sections to check`. The coverage
text "(N of M sections checked)" appears at the end of each finding's reason in the report.

## A. Routing and coverage (no model quality needed)

Request: `Change the default port from 8080 to 9090`

| # | File | Expected routing | Expected result |
|---|---|---|---|
| A1 | stage7/short-readme.md | 0 of 1 | Not affected, "(0 of 1 sections checked)". "Portal" must not match "port". |
| A2 | stage7/multi-section-guide.md | 3 of 6 (Deployment + its two neighbours) | Affected: Deployment only. |
| A3 | stage7/runbook-crlf.md | 3 of 3 | Affected: step 3 only. Proposal keeps CRLF line endings (check with `file` or a hex view after Finalize). |
| A4 | stage7/braces-config.md | 3 of 3 | Affected: `${PORT:8080}` becomes `${PORT:9090}`; braces survive. |
| A5 | stage7/incident-notes.md | 3 of 3 (the empty title section is dropped; "Ports" matches via the plural) | Not affected: ports there are firewall policy, not the default port. A false "affected" here is a Scanner-quality issue, not a routing one. |
| A6 | stage7/big-handbook.md (211 KB, 117 sections) | Far fewer than 117 | Port line 167 found. Scan finishes in reasonable time. No "Could not apply". |

Request: `Use Java 21 instead of Java 17`

| # | File | Expected |
|---|---|---|
| A7 | stage7/short-readme.md | Affected (1 of 1). |
| A8 | stage7/multi-section-guide.md | Tooling affected. Routing includes Tooling and neighbours; "2017"-style numbers do not match "17". |
| A9 | stage7/big-handbook.md | Line 657 found. |
| A10 | stage7/runbook-crlf.md | `temurin:17` found (term split on ":"). |

## B. Edit, review, finalize (text files)

| # | Steps | Expected |
|---|---|---|
| B1 | Run A2, open the proposal. | Diff starts with "Line N · Deployment" and shows only the Deployment paragraph changed; other lines folded as "N unchanged lines". Reviewer verdict shown. Edit shows one box per changed section, with the same label. |
| B1b | In B1, change the text in the edit box, Save edits, then Finalize. | "Edited by you" shown; the file on disk has your text, everything else unchanged. |
| B2 | Untick one file, Finalize. | Only ticked files change on disk; a backup folder exists with the originals. |
| B3 | Finalize A3 and A4. | Files change; CRLF kept in A3; no other bytes differ (`diff` against the original shows one line). |
| B4 | Make a file read-only or point to a missing file, Finalize. | Failure message; every file restored to the original (rollback). |
| B5 | Request that matches nothing (`Rename the Mars team to Venus`). | All files "No passage needs a change" with coverage text; nothing proposed. |
| B6 | Request with no usable words (`Do it now`). | Routing is "all sections"; scan still completes. |

## C. Office and PDF (read-only)

Every affected finding names where the change is: "Where: Slide 3; Slide 3 > Notes." (pptx), "Where: Page 3." (pdf),
"Where: Setup > Starting the server; Troubleshooting." (docx). Note that a pptx counts each slide's notes as a
separate section, so kai-overview.pptx has 6 sections (5 slides + the Run slide's notes).

Files in `sampleDocs/office/`. These cannot be edited; Kai reports what to change by hand.
Request: `Change the default port from 8080 to 9090`

| # | File | Expected routing | Expected result |
|---|---|---|---|
| C1 | admin-guide.docx | Sections follow Word headings; log shows headings like "Setup > Starting the server" in the Scanner/log output. | Affected: "Starting the server" and "Troubleshooting". Not the "since 2017" or "support" sentences. "Update by hand" note shown. |
| C2 | kai-overview.pptx | Sections are slides in presentation order: Slide 1 Kai Overview, 2 Install, 3 Run, 4 Support, 5 Roadmap. Expect 3 of 5 (Run plus neighbours). | Affected: Slide 3 (Run), including its "## Notes" text. Not Slide 5 (Roadmap). |
| C3 | release-notes.pdf | Sections are pages: 3 of 5 (pages 2-4). | Affected: page 3 only ("Where: Page 3."). Page 4 ("2017-44") not affected. |

Request: `Use Java 21 instead of Java 17`

| # | File | Expected |
|---|---|---|
| C4 | admin-guide.docx | Requirements section affected; "since 2017" not. |
| C5 | kai-overview.pptx | Install slide affected. |
| C6 | release-notes.pdf | Page 2 affected. |

If C1 shows only size-based chunks with no heading names, Word headings are not mapping to `h1`-`h6`
in Tika's output; fix `OfficeText` before anything else.

## D. Robustness

| # | Steps | Expected |
|---|---|---|
| D1 | Put a corrupt file named `bad.docx` (a renamed text file) in the office folder. | That file shows an error finding; the rest of the scan completes. An error is never shown as "No change". |
| D2 | Stop the model server mid-scan. | Affected files show an error with a reason, not "not affected". |
| D3 | Open an Office file in Word (creates `~$name.docx`). | The lock file is not listed or scanned. |
| D4 | Large file: create a ~300-500 MB markdown file (`for i in $(seq 2000); do cat sampleDocs/stage7/big-handbook.md; done > /tmp/kai-test/huge.md`, ~420 MB). Request `Change the default port from 8080 to 9090`. Watch Kai's java process in Activity Monitor. | File is listed; Kai's memory stays roughly flat (well under 1 GB) during the scan, the machine stays responsive; the proposal shows one box per changed section labelled "Line N · heading"; Finalize writes a file identical to the original except the edited lines (`cmp -l` or `diff` shows only those). Expect many sections (each copy has a port line): stop the scan by closing Kai if it takes too long — memory is what this test checks. |
| D6 | Run A2, then edit the file on disk (add a line at the top) before Finalize. | "These files changed after the scan" and nothing is written. |
| D5 | Scan with `kai.scan.parallel=4` and the full folder set. | Report keeps folder and file order. |

## E. Not built yet (enable these when the stage lands)

| Stage | Test |
|---|---|
| 8 | Embedding routing: with the model present, routing finds `Ports`/`listens on` style paraphrases that keywords miss; with the model removed, the start page says Kai uses keywords. |
| 9 | Contextual case: a new first step is added to a workflow. Every document that lists the workflow steps is flagged, and the proposal renumbers the steps correctly. Compare model sizes and with/without embeddings; fill the results table in PLAN.md. | Ripple (moved from stage 7): after the new first step, "go back to step 2" in the runbook and "4-step" in the README are found and flagged.

## Results log

| Date | Build | Model | Tests run | Pass | Notes |
|---|---|---|---|---|---|
| | | | | | |
