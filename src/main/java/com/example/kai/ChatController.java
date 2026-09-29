package com.example.kai;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ChatController {

	public record Message(String sender, String text) {
	}

	private final ChatService chatService;

	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	// Show the chat page with the conversation so far
	@GetMapping("/")
	public String chat(HttpSession session, Model model) {
		model.addAttribute("messages", history(session));
		return "chat"; // -> templates/chat.html
	}

	// Receive the user's message, add the bot's reply, then reload the page
	@PostMapping("/chat")
	public String send(@RequestParam String message, HttpSession session) {
		List<Message> history = history(session);
		history.add(new Message("user", message));
		try {
			history.add(new Message("bot", chatService.reply(session.getId(), message)));
		}
		catch (Exception e) {
			// e.g. bad API key, wrong base URL, model unavailable: show it in the chat instead of an error page
			history.add(new Message("error", chatService.describe(e)));
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
