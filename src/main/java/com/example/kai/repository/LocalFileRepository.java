package com.example.kai.repository;

import java.io.FilterReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

// Documents in a folder on local disk. location = folder path, id = path relative to it.
@Component
public class LocalFileRepository implements DocumentRepository {

	// Read and rewritten as plain text
	private static final Set<String> TEXT = Set.of("md", "txt", "properties", "yml", "yaml", "json", "xml");
	// Text extracted with Tika for scanning only; Kai never rewrites these
	private static final Set<String> OFFICE = Set.of("docx", "pptx", "pdf", "doc", "ppt");

	// Files are streamed, so this is not a memory limit: it keeps scan times and temp files sane
	private static final long MAX_BYTES = 500_000_000L;

	private static final Object EXTRACTING = new Object();

	@Override
	public String type() {
		return "local";
	}

	// Supported files only; skips hidden folders (.git, .kai-backup) and anything too big
	@Override
	public List<String> list(String location) throws IOException {
		Path root = root(location);
		try (Stream<Path> paths = Files.walk(root)) {
			return paths.filter(Files::isRegularFile)
					.filter(p -> !isHidden(root.relativize(p)))
					.filter(LocalFileRepository::supported)
					.sorted()
					.map(p -> root.relativize(p).toString())
					.toList();
		}
	}

	// A text file is read in place. Office/PDF text is extracted to a temp file first (not into
	// memory); the temp file is deleted when the reader is closed.
	@Override
	public Reader open(String location, String id) throws IOException {
		Path file = resolve(location, id);
		if (canWrite(id)) {
			return Files.newBufferedReader(file);
		}
		Path text = Files.createTempFile("kai-", ".txt");
		// One extraction at a time: the parsers keep some of each file in memory (e.g. a big
		// slide or embedded part), so parallel scans must not stack them
		synchronized (EXTRACTING) {
			try (Writer out = Files.newBufferedWriter(text)) {
				OfficeText.extract(file, out);
			}
			catch (IOException | RuntimeException e) {
				Files.deleteIfExists(text);
				throw e;
			}
		}
		return new FilterReader(Files.newBufferedReader(text)) {
			@Override
			public void close() throws IOException {
				try {
					super.close();
				}
				finally {
					Files.deleteIfExists(text);
				}
			}
		};
	}

	@Override
	public boolean canWrite(String id) {
		return TEXT.contains(extension(Path.of(id)));
	}

	@Override
	public String version(String location, String id) throws IOException {
		Path file = resolve(location, id);
		return Files.size(file) + ":" + Files.getLastModifiedTime(file).toMillis();
	}

	// Streams original -> a temp file, and only when that worked copies it over the file. The copy
	// writes into the existing file, so its permissions stay as they are.
	@Override
	public void write(String location, String id, Rewrite rewrite) throws IOException {
		Path file = resolve(location, id);
		if (!canWrite(id) || !Files.isWritable(file)) {
			throw new IOException(id + " is read-only" + (canWrite(id) ? "" : " in Kai (update it by hand)"));
		}
		Path next = Files.createTempFile("kai-", ".new");
		try {
			try (Reader in = Files.newBufferedReader(file); Writer out = Files.newBufferedWriter(next)) {
				rewrite.apply(in, out);
			}
			overwrite(next, file);
		}
		finally {
			Files.deleteIfExists(next);
		}
	}

	@Override
	public void copyTo(String location, String id, Path backup) throws IOException {
		Files.copy(resolve(location, id), backup);
	}

	@Override
	public void restore(String location, String id, Path backup) throws IOException {
		if (!canWrite(id)) {
			throw new IOException(id + " is read-only in Kai (update it by hand)");
		}
		overwrite(backup, resolve(location, id));
	}

	private static void overwrite(Path from, Path to) throws IOException {
		try (OutputStream out = Files.newOutputStream(to)) { // truncates
			Files.copy(from, out);
		}
	}

	private static boolean supported(Path p) {
		String ext = extension(p);
		long size = p.toFile().length();
		return (TEXT.contains(ext) || OFFICE.contains(ext)) && size <= MAX_BYTES;
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

	// ".git", ".DS_Store", and Office lock files like "~$guide.docx" (present while a file is open in Word)
	private static boolean isHidden(Path relative) {
		for (Path part : relative) {
			if (part.toString().startsWith(".") || part.toString().startsWith("~$")) {
				return true;
			}
		}
		return false;
	}
}
