package com.example.kai.config;

import java.nio.file.Path;
import java.util.List;

// Kai's own settings (kai.* keys). Built and checked by KaiConfig before Spring starts,
// then registered as a bean. All paths are already absolute.
public record KaiProperties(Path backupDir, List<Target> targets, int parallel) {

	// One place to scan. type picks the DocumentRepository adapter (local; later box).
	// location: local = absolute folder path, box = folder URL.
	public record Target(String type, String location) {

		public String label() {
			return type + ":" + location;
		}
	}
}
