package com.example.kai.orchestrator;

import java.util.List;

// One document's result. Stage 3 will add proposed content + reviewer verdict.
public record Finding(String file, boolean affected, String reason) {

	// The whole run, kept in the HTTP session so the report page can show it
	public record Report(String instruction, String repository, String location, List<Finding> findings) {

		public long affectedCount() {
			return findings.stream().filter(Finding::affected).count();
		}
	}
}
