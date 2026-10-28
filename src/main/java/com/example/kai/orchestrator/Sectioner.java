package com.example.kai.orchestrator;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Cuts a document into sections small enough for one short model prompt, and splits a section
// into numbered paragraphs for the Editor. Reads the text as a stream and hands out one section
// at a time, so a 500 MB file never sits in memory. Section offsets are character positions in
// the text: for text files that is the file itself, so edits land in the right place.
// Cuts at line breaks; only a line longer than MAX_CHARS is cut inside the line.
final class Sectioner {

	static final int MAX_CHARS = 3_000; // about 750 tokens

	private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");

	// headingPath = "Onboarding > Setup > Steps"; start/end = offsets in the full text; line = 1-based line of start
	record Section(String headingPath, int start, int end, int line, String text) {
	}

	// One blank-line separated block. start/end are offsets in the section text and cover the
	// text only, not the blank lines around it
	record Paragraph(int start, int end, String text) {
	}

	private final Reader in;
	private final char[] buf = new char[8192];
	private int bufPos;
	private int bufLen;
	private final List<String> headings = new ArrayList<>(); // heading texts, outermost first
	private String path = "";
	private final StringBuilder seg = new StringBuilder(); // the section being collected
	private int segStart;
	private int segLine = 1;
	private int pos;
	private int line = 1;
	private boolean atLineStart = true;

	Sectioner(Reader in) {
		this.in = in;
	}

	// The next section, or null at the end of the text. Blank runs of text are skipped.
	Section next() throws IOException {
		while (true) {
			String piece = nextPiece();
			if (piece == null) {
				return emit();
			}
			boolean lineStart = atLineStart;
			atLineStart = piece.endsWith("\n");
			// Cheap test first: a 500 MB file has millions of lines
			Matcher m = lineStart && piece.charAt(0) == '#' ? HEADING.matcher(piece.strip()) : null;
			Section out = null;
			if (m != null && m.matches()) {
				out = emit(); // the text before the heading, under the old heading path
				int level = m.group(1).length();
				while (headings.size() >= level) {
					headings.remove(headings.size() - 1);
				}
				headings.add(m.group(2).strip());
				path = String.join(" > ", headings);
			}
			else if (seg.length() + piece.length() > MAX_CHARS) {
				out = emit();
			}
			seg.append(piece);
			pos += piece.length();
			if (atLineStart) {
				line++; // a piece holds at most one '\n', at its end
			}
			if (out != null) {
				return out;
			}
		}
	}

	// The collected text as a section (null if it is blank), and start collecting the next one
	private Section emit() {
		String text = seg.toString();
		Section s = text.isBlank() ? null : new Section(path, segStart, pos, segLine, text);
		seg.setLength(0);
		segStart = pos;
		segLine = line;
		return s;
	}

	// One line including its '\n', or MAX_CHARS characters of a longer line; null at the end
	private String nextPiece() throws IOException {
		StringBuilder p = new StringBuilder();
		while (p.length() < MAX_CHARS) {
			if (bufPos == bufLen) {
				bufLen = Math.max(0, in.read(buf));
				bufPos = 0;
				if (bufLen == 0) {
					break;
				}
			}
			char c = buf[bufPos++];
			p.append(c);
			if (c == '\n') {
				break;
			}
		}
		return p.isEmpty() ? null : p.toString();
	}

	// All sections of a text in memory, for tests
	static List<Section> split(String text) {
		try {
			Sectioner sectioner = new Sectioner(new StringReader(text));
			List<Section> out = new ArrayList<>();
			for (Section s = sectioner.next(); s != null; s = sectioner.next()) {
				out.add(s);
			}
			return out;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static List<Paragraph> paragraphs(Section s) {
		List<Paragraph> out = new ArrayList<>();
		String t = s.text();
		int begin = -1;
		int last = -1;
		int pos = 0;
		while (pos < t.length()) {
			int eol = t.indexOf('\n', pos);
			int lineEnd = eol < 0 ? t.length() : eol;
			if (t.substring(pos, lineEnd).isBlank()) {
				if (begin >= 0) {
					out.add(new Paragraph(begin, last, t.substring(begin, last)));
					begin = -1;
				}
			}
			else {
				if (begin < 0) {
					begin = pos;
				}
				last = lineEnd > pos && t.charAt(lineEnd - 1) == '\r' ? lineEnd - 1 : lineEnd;
			}
			pos = eol < 0 ? t.length() : eol + 1;
		}
		if (begin >= 0) {
			out.add(new Paragraph(begin, last, t.substring(begin, last)));
		}
		return out;
	}
}
