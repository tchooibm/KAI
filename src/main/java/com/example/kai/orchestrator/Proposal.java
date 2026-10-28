package com.example.kai.orchestrator;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

// The proposed edits to one editable, affected file, plus the reviewer's advice. Holds only the
// changed sections (before and after), never the whole file, so a 500 MB file costs a few KB here.
// Kept in the HTTP session; the user can edit each section's text and untick the file until Finalize.
// Nothing here touches disk; rewrite() is what ChangeWriter streams the file through.
public final class Proposal {

	// One changed section. start/end = character offsets in the file; line = where it starts
	public static final class Part {

		private final String heading;
		private final int start;
		private final int end;
		private final int line;
		private final String original;
		private final String kai; // Kai's proposal, to tell whether the user edited it
		private String proposed;

		Part(Sectioner.Section s, String proposed) {
			this.heading = s.headingPath();
			this.start = s.start();
			this.end = s.end();
			this.line = s.line();
			this.original = s.text();
			this.kai = proposed;
			this.proposed = proposed;
		}

		public String label() {
			return "Line " + line + (heading.isBlank() ? "" : " · " + heading);
		}

		public String proposed() {
			return proposed;
		}

		// For the textarea: HTML drops one newline right after <textarea>, so one is added
		public String editText() {
			return "\n" + proposed;
		}

		boolean changed() {
			return !proposed.equals(original);
		}
	}

	private final String version; // the file's version when it was read, for the stale check
	private final List<Part> parts;
	private final String error; // set = "Could not apply": no proposed text
	private final Boolean reviewOk; // null = the reviewer could not check it
	private final String review;
	private boolean include = true;

	private Proposal(String version, List<Part> parts, String error, Boolean reviewOk, String review) {
		this.version = version;
		this.parts = parts;
		this.error = error;
		this.reviewOk = reviewOk;
		this.review = review;
	}

	static Proposal ready(String version, List<Part> parts, Boolean reviewOk, String review) {
		return new Proposal(version, parts, null, reviewOk, review);
	}

	static Proposal failed(String error) {
		return new Proposal(null, List.of(), error, null, null);
	}

	// From the review screen, one text per part (null = not sent). Browsers send textarea lines
	// as \r\n: keep the section's own line endings.
	public void save(List<String> texts, boolean include) {
		for (int k = 0; k < parts.size() && k < texts.size(); k++) {
			Part p = parts.get(k);
			String t = texts.get(k);
			if (t != null) {
				p.proposed = p.original.contains("\r\n") ? t : t.replace("\r\n", "\n");
			}
		}
		this.include = include;
	}

	// Each changed section's diff, under a "Line N · heading" line
	public List<Diff.Line> diff() {
		List<Diff.Line> out = new ArrayList<>();
		for (Part p : parts) {
			List<Diff.Line> d = Diff.lines(p.original, p.proposed);
			if (!d.isEmpty()) {
				out.add(new Diff.Line("gap", p.label()));
				out.addAll(d);
			}
		}
		return out;
	}

	// Copies the original to out, with each changed section replaced. Checks every replaced
	// section still holds the text the proposal was made from, so a changed file is never overwritten.
	public void rewrite(Reader in, Writer out) throws IOException {
		char[] buf = new char[8192];
		long pos = 0;
		for (Part p : parts) {
			pos += copy(in, out, buf, p.start - pos);
			char[] old = new char[p.end - p.start];
			int got = 0;
			for (int n; got < old.length && (n = in.read(old, got, old.length - got)) > 0;) {
				got += n;
			}
			if (got != old.length || !new String(old).equals(p.original)) {
				throw new IOException("the file changed after the scan (at " + p.label() + ")");
			}
			pos += got;
			out.write(p.proposed);
		}
		copy(in, out, buf, Long.MAX_VALUE);
	}

	private static long copy(Reader in, Writer out, char[] buf, long count) throws IOException {
		long done = 0;
		for (int n; done < count && (n = in.read(buf, 0, (int) Math.min(buf.length, count - done))) > 0;) {
			out.write(buf, 0, n);
			done += n;
		}
		if (count != Long.MAX_VALUE && done != count) {
			throw new IOException("the file changed after the scan (it is shorter)");
		}
		return done;
	}

	public boolean ready() {
		return error == null;
	}

	// Something differs from the original
	public boolean changed() {
		return parts.stream().anyMatch(Part::changed);
	}

	public String version() {
		return version;
	}

	public List<Part> parts() {
		return parts;
	}

	public String error() {
		return error;
	}

	public Boolean reviewOk() {
		return reviewOk;
	}

	public String review() {
		return review;
	}

	public boolean include() {
		return include;
	}

	// Changed by the user after the review
	public boolean edited() {
		return parts.stream().anyMatch(p -> !p.proposed.equals(p.kai));
	}
}
