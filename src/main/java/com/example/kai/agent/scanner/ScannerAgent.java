package com.example.kai.agent.scanner;

import java.util.Arrays;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.example.kai.orchestrator.Finding;

// Agent 1 of 3. Called only by the Orchestrator, never by the user.
// Decides whether one file is affected by the instruction.
// STAGE 1 STUB: keyword match, no LLM. Stage 2 replaces the body with a ChatClient call.
@Component
public class ScannerAgent {

	public Finding assess(String instruction, String file, String content) {
		String text = content.toLowerCase(Locale.ROOT);
		return Arrays.stream(instruction.toLowerCase(Locale.ROOT).split("\\W+"))
				.filter(word -> word.length() >= 4 && text.contains(word))
				.findFirst()
				.map(word -> new Finding(file, true, "stub: mentions '" + word + "'"))
				.orElse(new Finding(file, false, "stub: no keyword match"));
	}
}
