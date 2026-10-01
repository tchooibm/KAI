package com.example.kai.writer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import org.springframework.stereotype.Component;

import com.example.kai.config.KaiProperties;
import com.example.kai.orchestrator.Finding;
import com.example.kai.repository.DocumentRepository;

// Finalize (no LLM). Order matters, so a failure at any step leaves your files as they were:
//   1. stale check: a file changed on disk since the scan -> refuse, nothing written
//   2. report:      <backup-dir>/<run>/report.html (before/after of every ticked file)
//   3. backup:      <backup-dir>/<run>/files/<n>-<folder>/<file>, exact bytes
//   4. write:       every ticked file
//   5. rollback:    any write fails -> restore ALL ticked files from the backup
// Reads and writes documents only through DocumentRepository, so this works for any adapter.
@Component
public class ChangeWriter {

	private static final Logger log = LoggerFactory.getLogger(ChangeWriter.class);
	private static final DateTimeFormatter RUN = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	public enum Result {
		APPLIED, ROLLED_BACK, NOT_WRITTEN
	}

	// run = folder name under backup-dir with the report, or null if no report was saved
	public record Outcome(Result result, String message, String run) {
	}

	// One file in the report: backup = where its original is kept
	public record Row(Finding finding, String backup) {
	}

	private final ITemplateEngine templates;
	private final KaiProperties properties;

	public ChangeWriter(ITemplateEngine templates, KaiProperties properties) {
		this.templates = templates;
		this.properties = properties;
	}

	// Never throws: every problem becomes an Outcome the chat can show
	public Outcome apply(Finding.Report report, Function<String, DocumentRepository> repos) {
		List<Finding> selected = report.findings().stream().filter(Finding::selected).toList();
		if (selected.isEmpty()) {
			return new Outcome(Result.NOT_WRITTEN, "Nothing to write: no ticked file has changes.", null);
		}

		// 1. Stale check: the file must still be exactly what the proposal was made from
		List<String> stale = new ArrayList<>();
		for (Finding f : selected) {
			try {
				if (!repos.apply(f.target().type()).read(f.target().location(), f.file()).equals(f.proposal().original())) {
					stale.add(name(f));
				}
			}
			catch (Exception e) {
				stale.add(name(f) + " (cannot read it: " + e.getMessage() + ")");
			}
		}
		if (!stale.isEmpty()) {
			return new Outcome(Result.NOT_WRITTEN, "Nothing was written. These files changed after the scan, so Kai will not overwrite them:\n  - "
					+ String.join("\n  - ", stale) + "\nScan again to get fresh proposals.", null);
		}

		String run = LocalDateTime.now().format(RUN);
		Path dir = properties.backupDir().resolve(run);
		List<Row> rows = selected.stream().map(f -> new Row(f, backupPath(dir, report, f).toString())).toList();

		// 2. Report, 3. Backup. A failure here: nothing has been written yet
		try {
			saveReport(dir, report, rows, "Not written yet");
			for (Row r : rows) {
				Finding f = r.finding();
				Path backup = Path.of(r.backup());
				Files.createDirectories(backup.getParent());
				Files.write(backup, repos.apply(f.target().type()).readBytes(f.target().location(), f.file()));
			}
		}
		catch (Exception e) {
			log.warn("WRITER {} -> backup failed", run, e);
			return new Outcome(Result.NOT_WRITTEN, "Nothing was written: Kai could not save the report or the backup in " + dir
					+ " (" + e.getMessage() + ")", null);
		}

		// 4. Write
		String failure = null;
		for (Row r : rows) {
			Finding f = r.finding();
			try {
				repos.apply(f.target().type()).write(f.target().location(), f.file(), f.proposal().proposed());
				log.info("WRITER {} wrote {}", run, name(f));
			}
			catch (Exception e) {
				log.warn("WRITER {} -> write failed: {}", run, name(f), e);
				failure = name(f) + ": " + e.getMessage();
				break;
			}
		}
		if (failure == null) {
			return finish(dir, report, rows, new Outcome(Result.APPLIED, "Applied: " + rows.size() + (rows.size() == 1 ? " file" : " files")
					+ " changed. The originals are backed up in " + dir, run));
		}

		// 5. Rollback: restore every ticked file, also those not written yet (restoring them is harmless)
		List<String> notRestored = new ArrayList<>();
		for (Row r : rows) {
			Finding f = r.finding();
			try {
				repos.apply(f.target().type()).writeBytes(f.target().location(), f.file(), Files.readAllBytes(Path.of(r.backup())));
			}
			catch (Exception e) {
				log.error("WRITER {} -> RESTORE FAILED: {}", run, name(f), e);
				notRestored.add(name(f) + "  <-  " + r.backup());
			}
		}
		String message = notRestored.isEmpty()
				? "Rolled back: writing " + failure + "\nAll " + rows.size() + " files were restored from the backup, so nothing changed."
				: "Rollback INCOMPLETE: writing " + failure + "\nThese files could not be restored. Copy them back by hand from the backup:\n  - "
						+ String.join("\n  - ", notRestored);
		return finish(dir, report, rows, new Outcome(Result.ROLLED_BACK, message, run));
	}

	// The report is saved again with the outcome; if that fails, the first copy is still there
	private Outcome finish(Path dir, Finding.Report report, List<Row> rows, Outcome outcome) {
		try {
			saveReport(dir, report, rows, outcome.message());
		}
		catch (Exception e) {
			log.warn("WRITER {} -> could not update the report", dir, e);
		}
		return outcome;
	}

	private void saveReport(Path dir, Finding.Report report, List<Row> rows, String outcome) throws IOException {
		Context ctx = new Context(Locale.ENGLISH);
		ctx.setVariable("report", report);
		ctx.setVariable("rows", rows);
		ctx.setVariable("outcome", outcome);
		ctx.setVariable("run", dir.getFileName().toString());
		ctx.setVariable("notWritten", report.findings().stream().filter(f -> f.affected() && !f.selected()).toList());
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("report.html"), templates.process("final-report", ctx)); // templates/final-report.html
	}

	// files/<n>-<folder name>/<file>: n keeps two scan folders with the same name apart
	private static Path backupPath(Path dir, Finding.Report report, Finding f) {
		Path folder = Path.of(f.target().location()).getFileName();
		String label = (report.targets().indexOf(f.target()) + 1) + "-" + (folder == null ? "root" : folder);
		return dir.resolve("files").resolve(label).resolve(f.file());
	}

	private static String name(Finding f) {
		return f.target().entry() + "/" + f.file();
	}
}
