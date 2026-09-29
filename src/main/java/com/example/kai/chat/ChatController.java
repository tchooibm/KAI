package com.example.kai.chat;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.kai.config.KaiProperties;
import com.example.kai.orchestrator.Finding;
import com.example.kai.orchestrator.Orchestrator;

// The only user-facing interface. Every message goes to the Orchestrator;
// agents stay behind it and never talk to the user.
@Controller
public class ChatController {

	// report is set on bot replies that carry a scan result, otherwise null
	public record Message(String sender, String text, Finding.Report report) {
	}

	private final Orchestrator orchestrator;
	private final KaiProperties properties;

	public ChatController(Orchestrator orchestrator, KaiProperties properties) {
		this.orchestrator = orchestrator;
		this.properties = properties;
	}

	@GetMapping("/")
	public String chat(HttpSession session, Model model) {
		model.addAttribute("messages", history(session));
		model.addAttribute("target", orchestrator.target());
		model.addAttribute("backupDir", properties.backupPath().toString());
		return "chat"; // -> templates/chat.html
	}

	@PostMapping("/chat")
	public String send(@RequestParam String message, HttpSession session) {
		List<Message> history = history(session);
		history.add(new Message("user", message, null));
		try {
			Finding.Report report = orchestrator.scan(message);
			String summary = report.affectedCount() + " of " + report.findings().size() + " files affected in "
					+ report.repository() + ":" + report.location()
						+ (report.manualCount() > 0 ? ". " + report.manualCount() + " need a manual update (docx/pptx/pdf)" : "")
					+ (report.errorCount() > 0 ? " (" + report.errorCount() + " could not be checked, see Error rows)" : "");
			history.add(new Message("bot", summary, report));
		}
		catch (Exception e) {
			// e.g. folder missing: show it in the chat instead of an error page
			history.add(new Message("error", e.getClass().getSimpleName() + ": " + e.getMessage(), null));
		}
		return "redirect:/";
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
