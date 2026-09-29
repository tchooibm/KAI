package com.example.kai.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Bound from the user's config file (kai.* keys), see kai.properties
@ConfigurationProperties("kai")
public record KaiProperties(String backupDir, Scan scan) {

	// kai.scan.repository / kai.scan.location: what every change request is checked against
	public record Scan(String repository, String location) {

		public Scan {
			repository = isBlank(repository) ? "local" : repository.trim();
			location = isBlank(location) ? "." : location.trim();
		}
	}

	// Fail at startup, not halfway through a write
	public KaiProperties {
		if (isBlank(backupDir)) {
			throw new IllegalStateException("kai.backup-dir is required in the config file");
		}
		if (scan == null) {
			scan = new Scan(null, null);
		}
	}

	public Path backupPath() {
		return Path.of(backupDir).toAbsolutePath().normalize();
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
