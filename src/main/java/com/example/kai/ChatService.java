package com.example.kai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

	private static final Logger log = LoggerFactory.getLogger(ChatService.class);

	private final ChatClient chatClient;
	private final String baseUrl;
	private final String model;

	// ChatClient.Builder is pre-configured by Spring AI from application.properties
	// (base-url, api-key, model). ChatMemory is Spring AI's default in-memory store.
	public ChatService(ChatClient.Builder builder, ChatMemory chatMemory, KaiTools kaiTools,
			@Value("${spring.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
			@Value("${spring.ai.openai.chat.model:}") String model) {
		this.chatClient = builder
				.defaultSystem("You are Kai, a helpful assistant. Keep answers concise. "
						+ "Use the available tools when they can answer the question; never guess order statuses.")
				.defaultTools(kaiTools) // every @Tool method in KaiTools becomes available to the model
				.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
				.build();
		this.baseUrl = baseUrl;
		this.model = model;
	}

	// conversationId keeps each user's history separate, so the model remembers earlier turns
	public String reply(String conversationId, String userMessage) {
		return chatClient.prompt()
				.user(userMessage)
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
				.call()
				.content();
	}

	// Human-readable error details for debugging. Also logs the full stack trace to the console.
	public String describe(Exception e) {
		log.error("Model call failed", e);
		StringBuilder sb = new StringBuilder();
		sb.append("Request URL: ").append(baseUrl.replaceAll("/+$", "")).append("/chat/completions\n");
		sb.append("Model:       ").append(model).append("\n\n");
		for (Throwable t = e; t != null; t = t.getCause()) {
			sb.append(t == e ? "" : "Caused by: ")
					.append(t.getClass().getName()).append(": ").append(t.getMessage()).append("\n");
			if (t.getCause() == t) {
				break;
			}
		}
		return sb.toString();
	}
}
