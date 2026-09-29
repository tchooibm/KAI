package com.example.kai;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Plain-text endpoint for testing from the terminal:
//   curl -X POST localhost:8080/api/chat -H "Content-Type: text/plain" -d "hello"
@RestController
public class ChatApiController {

	private final ChatService chatService;

	public ChatApiController(ChatService chatService) {
		this.chatService = chatService;
	}

	@PostMapping(value = "/api/chat", produces = "text/plain")
	public ResponseEntity<String> chat(@RequestBody String message) {
		try {
			return ResponseEntity.ok(chatService.reply("curl", message));
		}
		catch (Exception e) {
			return ResponseEntity.internalServerError().body(chatService.describe(e));
		}
	}
}
