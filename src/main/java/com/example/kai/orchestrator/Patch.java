package com.example.kai.orchestrator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.kai.agent.editor.EditorAgent.Edit;
import com.example.kai.orchestrator.Sectioner.Paragraph;

// Applies the Editor's paragraph edits to one section. Paragraph numbers are 1-based within the section.
// Text outside the edited paragraphs stays byte-for-byte identical. Nothing is guessed:
// a wrong number or a paragraph edited twice is reported, and nothing is applied.
final class Patch {

	static class NoMatch extends Exception {

		NoMatch(String problems) {
			super(problems);
		}
	}

	// Replace text[start, end) with replacement; offsets in the section text
	record Spot(int start, int end, String replacement) {
	}

	private Patch() {
	}

	// Turns one section's paragraph edits into spots. All problems are reported together.
	static List<Spot> spots(String text, List<Paragraph> paragraphs, List<Edit> edits) throws NoMatch {
		if (edits == null || edits.isEmpty()) {
			throw new NoMatch("The editor proposed no edits.");
		}
		boolean crlf = text.contains("\r\n");
		List<String> problems = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();
		List<Spot> spots = new ArrayList<>();
		for (Edit e : edits) {
			int n = e.paragraph();
			if (n < 1 || n > paragraphs.size()) {
				problems.add("- Paragraph " + n + " does not exist (numbers run from 1 to " + paragraphs.size() + ").");
			}
			else if (!seen.add(n)) {
				problems.add("- Paragraph " + n + " is edited more than once; give one edit per paragraph.");
			}
			else {
				Paragraph p = paragraphs.get(n - 1);
				spots.add(new Spot(p.start(), p.end(), eol(e.text(), crlf)));
			}
		}
		if (!problems.isEmpty()) {
			throw new NoMatch(String.join("\n", problems));
		}
		return spots;
	}

	// The section's new text
	static String apply(String text, List<Spot> spots) {
		List<Spot> sorted = spots.stream().sorted(Comparator.comparingInt(Spot::start)).toList();
		StringBuilder out = new StringBuilder();
		int pos = 0;
		for (Spot s : sorted) {
			out.append(text, pos, s.start()).append(s.replacement());
			pos = s.end();
		}
		return out.append(text.substring(pos)).toString();
	}

	// The model writes \n; a Windows file has \r\n
	private static String eol(String text, boolean crlf) {
		String t = text == null ? "" : text.replace("\r\n", "\n");
		return crlf ? t.replace("\n", "\r\n") : t;
	}
}
