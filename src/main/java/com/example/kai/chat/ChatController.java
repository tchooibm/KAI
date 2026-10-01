package com.example.kai.chat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.kai.config.KaiProperties;
import com.example.kai.orchestrator.Finding;
import com.example.kai.orchestrator.Orchestrator;
import com.example.kai.orchestrator.Progress;
import com.example.kai.orchestrator.Proposal;
import com.example.kai.writer.ChangeWriter;

// The only user-facing interface. Every message goes to the Orchestrator;
// agents stay behind it and never talk to the user.
@Controller
public class ChatController {

	// report: set on bot replies that carry a scan result, otherwise null
	// applied: the report was finalized successfully, so it can't be finalized again
	// link:    "Open the report" address on Finalize replies, otherwise null
	public record Message(String sender, String text, Finding.Report report, boolean applied, String link) {

		Message(String sender, String text, Finding.Report report) {
			this(sender, text, report, false, null);
		}
	}

	private final Orchestrator orchestrator;
	private final KaiProperties properties;
	private final ExecutorService jobs = Executors.newVirtualThreadPerTaskExecutor(); // background scans

	public ChatController(Orchestrator orchestrator, KaiProperties properties) {
		this.orchestrator = orchestrator;
		this.properties = properties;
	}

	// A scan running in the background for one browser session; the page polls /progress
	record Job(Progress progress, CompletableFuture<Finding.Report> result) {
	}

	@GetMapping("/")
	public String chat(HttpSession session, Model model) {
		collect(session);
		Job job = (Job) session.getAttribute("job");
		model.addAttribute("messages", history(session));
		model.addAttribute("log", job == null ? null : job.progress().since(0)); // set = a scan is running
		model.addAttribute("targets", orchestrator.targets());
		model.addAttribute("backupDir", properties.backupDir().toString());
		return "chat"; // -> templates/chat.html
	}

	// Starts the scan and returns at once; the page shows the agents' progress until it's done.
	// One scan at a time per session.
	@PostMapping("/chat")
	public String send(@RequestParam String message, HttpSession session) {
		collect(session);
		if (session.getAttribute("job") == null) {
			history(session).add(new Message("user", message, null));
			Progress progress = new Progress();
			progress.add("Started");
			session.setAttribute("job", new Job(progress, CompletableFuture.supplyAsync(() -> {
				try {
					return orchestrator.scan(message, progress);
				}
				catch (IOException e) {
					throw new CompletionException(e);
				}
			}, jobs)));
		}
		return "redirect:/";
	}

	// New log lines since index `since`; done = reload the page to see the report
	@GetMapping("/progress")
	@ResponseBody
	public Map<String, Object> progress(@RequestParam int since, HttpSession session) {
		Job job = (Job) session.getAttribute("job");
		return job == null ? Map.of("lines", List.of(), "done", true)
				: Map.of("lines", job.progress().since(since), "done", job.result().isDone());
	}

	// A finished scan becomes the bot's chat message. Only request threads touch the history.
	private synchronized void collect(HttpSession session) {
		Job job = (Job) session.getAttribute("job");
		if (job == null || !job.result().isDone()) {
			return;
		}
		session.removeAttribute("job");
		try {
			Finding.Report report = job.result().join();
			String summary = report.affectedCount() + " of " + report.findings().size() + " files affected"
					+ (report.readyCount() > 0 ? ". " + report.readyCount() + " proposed edits to review below" : "")
					+ (report.unappliedCount() > 0 ? ". " + report.unappliedCount() + " could not be edited automatically" : "")
					+ (report.manualCount() > 0 ? ". " + report.manualCount() + " need a manual update (docx/pptx/pdf)" : "")
					+ (report.errorCount() > 0 ? " (" + report.errorCount() + " could not be checked, see Error rows)" : "");
			history(session).add(new Message("bot", summary, report));
		}
		catch (CompletionException e) {
			// e.g. folder missing: show it in the chat instead of an error page
			Throwable c = e.getCause() == null ? e : e.getCause();
			history(session).add(new Message("error", c.getClass().getSimpleName() + ": " + c.getMessage(), null));
		}
	}

	// "Save edits" on one report: the textarea text and include tick of each proposal.
	// Fields are named text<i> / include<i>, i = the finding's index in the report.
	// Saved in the session only; nothing is written to disk.
	@PostMapping("/save")
	public String save(@RequestParam int report, @RequestParam Map<String, String> form, HttpSession session) {
		Message m = reportMessage(history(session), report);
		if (m != null && !m.applied()) {
			save(m.report(), form);
		}
		return "redirect:/#report-" + report;
	}

	// "Finalize": save the form first (so unsaved edits count), then report -> backup -> write.
	// The outcome is a new chat message with a link to the saved report.
	@PostMapping("/finalize")
	public String finalizeReport(@RequestParam int report, @RequestParam Map<String, String> form, HttpSession session) {
		List<Message> history = history(session);
		Message m = reportMessage(history, report);
		if (m == null || m.applied()) {
			return "redirect:/";
		}
		save(m.report(), form);
		ChangeWriter.Outcome o = orchestrator.apply(m.report());
		if (o.result() == ChangeWriter.Result.APPLIED) {
			history.set(report, new Message(m.sender(), m.text(), m.report(), true, null));
		}
		history.add(new Message(o.result() == ChangeWriter.Result.APPLIED ? "bot" : "error", o.message(), null, false,
				o.run() == null ? null : "/report/" + o.run()));
		return "redirect:/";
	}

	// The saved final report, so the chat can link to it (browsers block file:// links from a web page)
	@GetMapping("/report/{run}")
	public ResponseEntity<String> finalReport(@PathVariable String run) throws IOException {
		Path file = properties.backupDir().resolve(run).resolve("report.html");
		if (!run.matches("[0-9-]+") || !Files.isRegularFile(file)) { // digits only: no ../ tricks
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(Files.readString(file));
	}

	private static void save(Finding.Report report, Map<String, String> form) {
		List<Finding> findings = report.findings();
		for (int i = 0; i < findings.size(); i++) {
			Proposal p = findings.get(i).proposal();
			String text = form.get("text" + i);
			if (p != null && p.ready() && text != null) {
				p.save(text, form.containsKey("include" + i));
			}
		}
	}

	private static Message reportMessage(List<Message> history, int index) {
		return index >= 0 && index < history.size() && history.get(index).report() != null ? history.get(index) : null;
	}

	// Conversation is kept per browser session, so no database is needed
	@SuppressWarnings("unchecked")
	private List<Message> history(HttpSession session) {
		List<Message> history = (List<Message>) session.getAttribute("history");
		if (history == null) {
			history = new ArrayList<>();
			session.setAttribute("history", history);
		}
		return history;
	}
}
