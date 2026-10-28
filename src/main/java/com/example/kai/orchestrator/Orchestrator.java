package com.example.kai.orchestrator;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import com.example.kai.agent.editor.EditorAgent;
import com.example.kai.agent.extractor.ExtractorAgent;
import com.example.kai.agent.reviewer.ReviewerAgent;
import com.example.kai.agent.scanner.ScannerAgent;
import com.example.kai.config.KaiProperties;
import com.example.kai.config.Setup;
import com.example.kai.config.KaiProperties.Target;
import com.example.kai.orchestrator.Sectioner.Paragraph;
import com.example.kai.orchestrator.Sectioner.Section;
import com.example.kai.repository.DocumentRepository;
import com.example.kai.writer.ChangeWriter;

// The only thing the chat talks to. Plain Java: decides which agent runs when
// and passes results between them. Agents never talk to the user directly.
// Optional first step, when the user picked an updated file: ExtractorAgent -> user confirms.
// Per file: ScannerAgent -> (affected + editable) EditorAgent -> ReviewerAgent.
// Finalize: ChangeWriter (no LLM) writes the ticked proposals.
@Service
public class Orchestrator {

	private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);

	private final Map<String, DocumentRepository> repositories = new TreeMap<>();
	private final ExtractorAgent extractor;
	private final ScannerAgent scanner;
	private final EditorAgent editor;
	private final ReviewerAgent reviewer;
	private final ChangeWriter writer;
	private final Setup setup;
	private final Router router = new TermRouter(); // Stage 7; stage 8 adds embeddings here

	// Spring injects every DocumentRepository bean, so new adapters register themselves
	public Orchestrator(List<DocumentRepository> repositories, ExtractorAgent extractor, ScannerAgent scanner,
			EditorAgent editor, ReviewerAgent reviewer, ChangeWriter writer, Setup setup) {
		repositories.forEach(r -> this.repositories.put(r.type(), r));
		this.extractor = extractor;
		this.scanner = scanner;
		this.editor = editor;
		this.reviewer = reviewer;
		this.writer = writer;
		this.setup = setup;
	}

	// Where change requests are checked, from kai.scan.* (as set on the start page)
	public List<Target> targets() {
		return setup.properties().targets();
	}

	// One scan location and its files, for the chat's "Updated file" list
	public record Folder(Target target, List<String> files) {
	}

	// Every file in every location, in kai.properties order. A location that can't be listed
	// stays in with no files, so a position always matches targets().
	public List<Folder> folders() {
		List<Folder> folders = new ArrayList<>();
		for (Target target : targets()) {
			List<String> files;
			try {
				files = repository(target.type()).list(target.location());
			}
			catch (Exception e) {
				files = List.of();
			}
			folders.add(new Folder(target, files));
		}
		return folders;
	}

	// The updated file from the chat's list: "<location position>:<file>". Only files Kai would
	// scan are accepted, so the value can't point anywhere else.
	public Finding.Source source(String key) throws IOException {
		int colon = key.indexOf(':');
		List<Target> targets = targets();
		int i = key.matches("\\d{1,4}:.+") ? Integer.parseInt(key.substring(0, colon)) : -1;
		String file = key.substring(colon + 1);
		if (i < 0 || i >= targets.size() || !repository(targets.get(i).type()).list(targets.get(i).location()).contains(file)) {
			throw new IOException("The updated file is no longer in the scan folders: " + file);
		}
		return new Finding.Source(targets.get(i), file);
	}

	// Step 1 for an updated file: what changed in it, guided by the user's summary.
	// A small file goes to the Extractor whole; from a big one only the sections that match the
	// summary's words, up to EXTRACT_CHARS, so the prompt and memory stay small.
	public Extraction extract(String summary, Finding.Source source, Progress progress) throws IOException {
		Target target = source.target();
		progress.add("Extractor: reading " + source.name());
		StringBuilder whole = new StringBuilder();
		StringBuilder matched = new StringBuilder();
		Predicate<Section> match = router.matcher(summary);
		boolean truncated = false;
		try (Reader in = repository(target.type()).open(target.location(), source.file())) {
			Sectioner sectioner = new Sectioner(in);
			for (Section s = sectioner.next(); s != null; s = sectioner.next()) {
				if (whole != null) {
					whole.append(s.text());
					if (whole.length() > EXTRACT_CHARS) {
						whole = null; // too big to send whole
					}
				}
				if (match.test(s)) {
					if (matched.length() + s.text().length() > EXTRACT_CHARS) {
						truncated = true;
					}
					else {
						matched.append(s.text());
					}
				}
			}
		}
		if (whole == null) {
			progress.add("Extractor: " + source.name() + " is large, so only the sections that mention the summary's words are read"
					+ (truncated ? " (the first " + EXTRACT_CHARS + " characters of them)" : ""));
		}
		ExtractorAgent.Changes c = extractor.extract(summary, source.file(), whole != null ? whole.toString() : matched.toString());
		progress.add("Extractor: found " + c.changes().size() + " changes"
				+ (c.notFound().isEmpty() ? "" : ", " + c.notFound().size() + " not found in the file"));
		return new Extraction(source, summary, c.changes(), c.notFound());
	}

	private static final int EXTRACT_CHARS = 60_000; // about 15k tokens

	// Handles up to kai.scan.parallel files at the same time, across all targets. Each file runs
	// scan -> edit -> review in one task, so edits for one file overlap with scans of others.
	// The report keeps the target and file-list order. Each step is added to progress for the chat page.
	// source = the updated file the change came from (skipped: it is already right), or null.
	public Finding.Report scan(String instruction, Finding.Source source, Progress progress) throws IOException {
		KaiProperties properties = setup.properties(); // one set of settings from start to end
		List<Future<Finding>> futures = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(properties.parallel())) { // close() waits for all
			for (Target target : properties.targets()) {
				DocumentRepository repo = repository(target.type());
				List<String> ids = repo.list(target.location());
				progress.add("Found " + ids.size() + " files in " + target.entry());
				for (String id : ids) {
					if (source != null && source.target().equals(target) && source.file().equals(id)) {
						progress.add("Skipping " + source.name() + ": it is the updated file");
						continue;
					}
					futures.add(pool.submit(() -> check(target, repo, instruction, id, progress)));
				}
			}
			progress.add("Checking " + futures.size() + " files, up to " + properties.parallel() + " at a time");
		}
		List<Finding> findings = futures.stream().map(Future::resultNow).toList();
		Finding.Report report = new Finding.Report(instruction, source, properties.targets(), findings);
		progress.add("Done: " + report.affectedCount() + " of " + findings.size() + " files affected");
		return report;
	}

	// Finalize: report -> backup -> write the ticked files -> restore all on any failure
	public ChangeWriter.Outcome apply(Finding.Report report) {
		return writer.apply(report, this::repository);
	}

	// Never throws: one bad file or model call must not stop the scan, and must not look like "not affected".
	// Stage 7: the file is read one section at a time; a section that matches the router is checked,
	// and so are the sections either side of it. The Scanner asks one yes/no question per checked section.
	// Only the affected sections are kept, so memory does not grow with the file size.
	private Finding check(Target target, DocumentRepository repo, String instruction, String id, Progress progress) {
		boolean editable = repo.canWrite(id);
		String name = target.entry() + "/" + id;
		try {
			String version = repo.version(target.location(), id);
			Predicate<Section> match = router.matcher(instruction);
			List<Section> affected = new ArrayList<>();
			String reason = "No passage needs a change.";
			int total = 0;
			int checked = 0;
			try (Reader in = repo.open(target.location(), id)) {
				Sectioner sectioner = new Sectioner(in);
				Section prev = null;
				boolean prevChecked = false;
				boolean prevMatched = false;
				for (Section s = sectioner.next(); s != null; s = sectioner.next()) {
					total++;
					boolean matched = match.test(s);
					if (matched && prev != null && !prevChecked) { // the section before a match
						checked++;
						reason = assess(instruction, id, prev, affected, reason);
					}
					boolean take = matched || prevMatched; // a match, or the section after one
					if (take) {
						checked++;
						reason = assess(instruction, id, s, affected, reason);
					}
					prev = s;
					prevChecked = take;
					prevMatched = matched;
				}
			}
			progress.add("Routing: " + name + " " + checked + " of " + total + " sections checked");
			// Coverage is always shown, so "not affected" can be told apart from "not looked at"
			String where = affected.stream().map(Section::headingPath).filter(p -> !p.isBlank()).distinct()
					.collect(Collectors.joining("; "));
			String coverage = (where.isEmpty() ? "" : " Where: " + where + ".") + " (" + checked + " of " + total
					+ " sections checked)";
			if (affected.isEmpty()) {
				progress.add("Scanner: " + name + " needs no change");
				return new Finding(target, id, Finding.Status.NOT_AFFECTED, reason + coverage, editable, null);
			}
			progress.add("Scanner: " + name + " must change in " + affected.size() + " section(s)"
					+ (editable ? "" : " (update by hand)"));
			Proposal proposal = editable ? propose(instruction, id, name, version, affected, progress) : null;
			return new Finding(target, id, Finding.Status.AFFECTED, reason + coverage, editable, proposal);
		}
		catch (Exception e) {
			log.warn("SCANNER {} {} -> ERROR", target.label(), id, e);
			progress.add("Scanner: could not check " + name + ": " + message(e));
			return new Finding(target, id, Finding.Status.ERROR, message(e), editable, null);
		}
	}

	// One Scanner call; an affected section is kept. Returns the reason to show.
	private String assess(String instruction, String id, Section s, List<Section> affected, String reason) {
		ScannerAgent.Verdict v = scanner.assess(instruction, id, s.headingPath(), s.text());
		if (!v.affected()) {
			return reason;
		}
		affected.add(s);
		return v.reason();
	}

	// Per affected section: Editor edits numbered paragraphs (one retry with the problems), then the
	// Reviewer checks the changed sections before and after. Never throws.
	private Proposal propose(String instruction, String id, String name, String version, List<Section> affected,
			Progress progress) {
		try {
			progress.add("Editor: drafting edits for " + name);
			List<Proposal.Part> parts = new ArrayList<>();
			for (Section s : affected) {
				parts.add(new Proposal.Part(s, Patch.apply(s.text(), editSection(instruction, id, s, name, progress))));
			}
			String before = join(affected.stream().map(Section::text).toList());
			String after = join(parts.stream().map(Proposal.Part::proposed).toList());
			// A reviewer failure keeps the proposal: the note says it wasn't checked
			try {
				progress.add("Reviewer: checking the edits for " + name);
				ReviewerAgent.Review r = reviewer.review(instruction, id, before, after);
				progress.add("Reviewer: " + name + (r.ok() ? " looks right" : " please check"));
				return Proposal.ready(version, parts, r.ok(), r.note());
			}
			catch (Exception e) {
				log.warn("REVIEWER {} -> ERROR", id, e);
				progress.add("Reviewer: could not check " + name + ": " + message(e));
				return Proposal.ready(version, parts, null, "The reviewer could not check this proposal: " + message(e));
			}
		}
		catch (Patch.NoMatch e) {
			log.warn("EDITOR {} -> could not apply:\n{}", id, e.getMessage());
			progress.add("Editor: could not apply edits to " + name);
			return Proposal.failed("Kai's edits could not be applied, so nothing was proposed.\n" + e.getMessage());
		}
		catch (Exception e) {
			log.warn("EDITOR {} -> ERROR", id, e);
			progress.add("Editor: failed on " + name + ": " + message(e));
			return Proposal.failed("The editor failed: " + message(e));
		}
	}

	// One section: Editor -> spots in the section text. One retry, with the problems, if a paragraph number was wrong.
	private List<Patch.Spot> editSection(String instruction, String id, Section s, String name, Progress progress)
			throws Patch.NoMatch {
		List<Paragraph> paragraphs = Sectioner.paragraphs(s);
		List<String> texts = paragraphs.stream().map(Paragraph::text).toList();
		String problems = null;
		for (int attempt = 1;; attempt++) {
			try {
				return Patch.spots(s.text(), paragraphs, editor.propose(instruction, id, s.headingPath(), texts, problems));
			}
			catch (Patch.NoMatch e) {
				if (attempt >= 2) {
					throw e;
				}
				log.info("EDITOR {} -> retry:\n{}", id, e.getMessage());
				progress.add("Editor: edits for " + name + " did not fit, retrying once");
				problems = e.getMessage();
			}
		}
	}

	private static String join(List<String> parts) {
		return String.join("\n...\n", parts);
	}

	private static String message(Exception e) {
		return e.getClass().getSimpleName() + ": " + e.getMessage();
	}

	private DocumentRepository repository(String type) {
		DocumentRepository repo = repositories.get(type);
		if (repo == null) {
			throw new IllegalArgumentException("Unknown repository '" + type + "', available: " + repositories.keySet());
		}
		return repo;
	}
}
