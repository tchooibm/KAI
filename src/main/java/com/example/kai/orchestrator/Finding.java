package com.example.kai.orchestrator;

import java.util.List;

// One document's result. Stage 3 will add proposed content + reviewer verdict.
// editable = false for formats Kai reads but never rewrites (docx, pptx, pdf).
public record Finding(String file, Status status, String reason, boolean editable) {

	// ERROR = the agent could not decide (timeout, bad model, unreadable file).
	// Never shown as "No", so a failure can't hide an affected file.
	public enum Status {
		AFFECTED, NOT_AFFECTED, ERROR
	}

	public boolean affected() {
		return status == Status.AFFECTED;
	}

	// Affected, but Kai can't rewrite it: the user has to update it by hand
	public boolean manual() {
		return affected() && !editable;
	}

	// The whole run, kept in the chat history
	public record Report(String instruction, String repository, String location, List<Finding> findings) {

		public long affectedCount() {
			return count(Status.AFFECTED);
		}

		public long errorCount() {
			return count(Status.ERROR);
		}

		public long manualCount() {
			return findings.stream().filter(Finding::manual).count();
		}

		private long count(Status status) {
			return findings.stream().filter(f -> f.status() == status).count();
		}
	}
}
