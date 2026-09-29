package com.example.kai.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

// Documents in a folder on local disk. location = folder path, id = path relative to it.
@Component
public class LocalFileRepository implements DocumentRepository {

	private static final Set<String> EXTENSIONS = Set.of("md", "txt", "properties", "yml", "yaml", "json", "xml");
	private static final long MAX_BYTES = 50_000; // keep prompts small

	@Override
	public String type() {
		return "local";
	}

	// Text files only; skips hidden folders (.git, .kai-backup) and anything too big
	@Override
	public List<String> list(String location) throws IOException {
		Path root = root(location);
		try (Stream<Path> paths = Files.walk(root)) {
			return paths.filter(Files::isRegularFile)
					.filter(p -> EXTENSIONS.contains(extension(p)))
					.filter(p -> !isHidden(root.relativize(p)))
					.filter(p -> p.toFile().length() <= MAX_BYTES)
					.sorted()
					.map(p -> root.relativize(p).toString())
					.toList();
		}
	}

	@Override
	public String read(String location, String id) throws IOException {
		return Files.readString(resolve(location, id));
	}

	@Override
	public void write(String location, String id, String content) throws IOException {
		Files.writeString(resolve(location, id), content);
	}

	private static Path root(String location) {
		return Path.of(location).toAbsolutePath().normalize();
	}

	// Refuse ids like "../../etc/passwd" that escape the chosen folder
	private static Path resolve(String location, String id) {
		Path root = root(location);
		Path file = root.resolve(id).normalize();
		if (!file.startsWith(root)) {
			throw new IllegalArgumentException("Outside " + root + ": " + id);
		}
		return file;
	}

	private static String extension(Path p) {
		String name = p.getFileName().toString();
		return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
	}

	private static boolean isHidden(Path relative) {
		for (Path part : relative) {
			if (part.toString().startsWith(".")) {
				return true;
			}
		}
		return false;
	}
}
