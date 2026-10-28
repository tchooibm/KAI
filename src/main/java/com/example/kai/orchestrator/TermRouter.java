package com.example.kai.orchestrator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.example.kai.orchestrator.Sectioner.Section;

// Stage 7 routing, no model call: a section is a candidate if it contains a word from the change
// request, and so are the sections next to it (added by the Orchestrator). Recall first: an extra section costs one short
// Scanner call, a missed one is silent. If no useful words are left, every section is checked.
final class TermRouter implements Router {

	private static final Set<String> STOP = Set.of("the", "and", "for", "with", "that", "this", "from", "into",
			"all", "are", "was", "will", "now", "instead", "before", "after", "each", "every", "later", "move",
			"down", "then", "should", "must", "please", "change", "update", "make", "use", "add", "new", "its",
			"not", "but", "you", "can");

	@Override
	public Predicate<Section> matcher(String instruction) {
		List<String> terms = terms(instruction);
		if (terms.isEmpty()) {
			return s -> true;
		}
		Pattern any = wholeWords(terms); // built once per file, not per section
		return s -> any.matcher(s.headingPath() + "\n" + s.text()).find();
	}

	// Whole words only, so "port" does not match "support"; an optional plural "s" still matches
	// ("port" finds "ports"). Letters and digits count as part of a word, so "17" does not match "2017".
	static Pattern wholeWords(List<String> terms) {
		String alternatives = terms.stream().map(Pattern::quote).collect(Collectors.joining("|"));
		return Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + alternatives + ")s?(?![\\p{L}\\p{N}])",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	// "Java 21 instead of 17" -> [java, 21, 17]; "temurin:17" -> [temurin, 17]
	static List<String> terms(String instruction) {
		Set<String> out = new LinkedHashSet<>();
		for (String word : instruction.toLowerCase().split("[^\\p{L}\\p{N}]+")) {
			if ((word.length() >= 3 || word.matches(".*\\d.*")) && !STOP.contains(word)) {
				out.add(word);
			}
		}
		return new ArrayList<>(out);
	}
}
