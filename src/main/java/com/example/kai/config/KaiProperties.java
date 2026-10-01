package com.example.kai.config;

import java.nio.file.Path;
import java.util.List;

// Kai's own settings (kai.* keys). Built and checked by KaiConfig before Spring starts,
// then registered as a bean. All paths are already absolute.
public record KaiProperties(Path backupDir, List<Target> targets, int parallel) {

	// One place to scan. type picks the DocumentRepository adapter (local; later box).
	// location: local = absolute folder path, box = folder URL.
	// entry: the value as typed in kai.properties (e.g. "./sampleDocs"), shown in the report.
	public record Target(String type, String location, String entry) {

		public String label() {
			return type + ":" + location;
		}
	}
}
