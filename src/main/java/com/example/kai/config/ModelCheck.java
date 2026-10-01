package com.example.kai.config;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

// Run once at startup (KaiApplication): are the AI key and address set, and do they work?
// Without it, a missing key only shows up later as "Could not check" on every file.
@Component
public class ModelCheck {

	private static final String KEY = "spring.ai.openai.api-key";
	private static final String URL = "spring.ai.openai.base-url";
	private static final String MODEL = "spring.ai.openai.chat.model";
	private static final String ADMIN = "\n\nAsk your Kai admin for the right values, put them in kai.properties, and start Kai again.";

	private final ChatClient.Builder builder;
	private final Environment env;

	public ModelCheck(ChatClient.Builder builder, Environment env) {
		this.builder = builder;
		this.env = env;
	}

	// null = all good, otherwise a message for the user
	public String problem() {
		String key = value(KEY);
		String url = value(URL);
		if (key == null) {
			return "The AI key is not set (" + KEY + ")." + ADMIN;
		}
		if (url == null) {
			return "The AI address is not set (" + URL + ")." + ADMIN;
		}
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			return "The AI address must start with https://  but is: " + url + ADMIN;
		}
		if (!url.replaceAll("/+$", "").endsWith("/v1")) {
			// The OpenAI SDK only appends /chat/completions; without /v1 every call is a 404
			return "The AI address must end with /v1  but is: " + url + ADMIN;
		}
		try {
			builder.build().prompt().user("Reply with the word OK.").call().content();
			return null;
		}
		catch (Exception e) {
			return explain(e, url);
		}
	}

	private String explain(Exception e, String url) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof OpenAIServiceException s) {
				return switch (s.statusCode()) {
					case 401, 403 -> "The AI service refused the key (" + KEY + "). It is wrong or has expired." + ADMIN;
					case 404 -> "The AI service does not know this address or model:\n  " + URL + "=" + url + "\n  " + MODEL
							+ "=" + env.getProperty(MODEL) + ADMIN;
					case 429 -> null; // key and address work, the service is just busy right now
					default -> "The AI service answered with an error (code " + s.statusCode() + "): " + s.getMessage() + ADMIN;
				};
			}
			if (t instanceof OpenAIIoException) {
				return "Kai cannot reach the AI service at " + url
						+ "\nCheck that you are online (and on the company VPN, if you need one), and that the address is right." + ADMIN;
			}
		}
		return "Kai could not talk to the AI service: " + e.getMessage() + ADMIN;
	}

	// Blank, or "${ICA_CODEX_KEY}" with no such environment variable on this computer -> not set
	private String value(String name) {
		try {
			String v = env.getProperty(name);
			return v == null || v.isBlank() || v.contains("${") ? null : v.trim();
		}
		catch (IllegalArgumentException e) { // unresolvable ${...}
			return null;
		}
	}
}
