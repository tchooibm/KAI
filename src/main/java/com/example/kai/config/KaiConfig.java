package com.example.kai.config;

import java.io.Console;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.example.kai.config.KaiProperties.Target;

// Finds, reads and checks kai.properties BEFORE Spring starts, so a mistake gives a
// plain-language message instead of a stack trace. Where the file is looked for:
//   1. --config=<path> program argument, if given (for developers)
//   2. otherwise: the working directory (IntelliJ default: the project root)
// Relative paths inside the file start from the file's own folder, not the working
// directory, so they mean the same thing wherever Kai is started from.
public final class KaiConfig {

	public static final String FILE = "kai.properties";

	private static final Set<String> KEYS = Set.of("kai.backup-dir", "kai.scan.local", "kai.scan.box", "kai.scan.parallel");

	// springFile: the copy of kai.properties that Spring reads (see load)
	public record Loaded(Path file, Path springFile, KaiProperties properties) {
	}

	public static class Invalid extends Exception {

		Invalid(String message) {
			super(message);
		}
	}

	private KaiConfig() {
	}

	public static Loaded load(String[] args) throws Invalid {
		Path file = locate(args);
		if (!Files.isRegularFile(file)) {
			throw new Invalid("Kai could not find its settings file:\n  " + file + "\n\n"
					+ "Pass --config=<path to kai.properties>, or run Kai from the folder that has it.");
		}
		// A \ is kept as typed, so Windows paths can be pasted as-is (C:\Team\Docs).
		// Normal .properties files treat \ as an escape, so double every one before parsing.
		// Spring gets the same doubled copy, else e.g. C:\\users would fail as a bad \\u escape.
		Path springFile;
		Properties p = new Properties();
		try {
			String text = Files.readString(file).replace("\\", "\\\\");
			p.load(new StringReader(text));
			springFile = Files.createTempFile("kai-", ".properties");
			springFile.toFile().deleteOnExit();
			Files.writeString(springFile, text);
		}
		catch (IOException | IllegalArgumentException e) {
			throw new Invalid("Kai could not read its settings file:\n  " + file + "\n\n" + e.getMessage());
		}
		Path home = file.getParent();
		List<String> problems = new ArrayList<>();

		// Typos like "kai.scan.locl" would otherwise be silently ignored
		for (String key : new TreeSet<>(p.stringPropertyNames())) {
			if (key.startsWith("kai.") && !KEYS.contains(key)) {
				problems.add("Unknown setting \"" + key + "\". Check the spelling. Known settings: " + String.join(", ", new TreeSet<>(KEYS)));
			}
		}

		Path backupDir = null;
		String backup = value(p, "kai.backup-dir");
		if (backup == null) {
			problems.add("kai.backup-dir is missing. Add a line like:  kai.backup-dir=./kai-backups");
		}
		else {
			backupDir = path(home, backup);
			try {
				Files.createDirectories(backupDir);
			}
			catch (IOException e) {
				problems.add("Kai cannot create the backup folder " + backupDir + " (" + e.getMessage() + ")");
			}
		}

		List<Target> targets = new ArrayList<>();
		List<String> local = list(p, "kai.scan.local");
		List<String> box = list(p, "kai.scan.box");
		for (String folder : local) {
			Path dir = path(home, folder);
			if (Files.isDirectory(dir)) {
				targets.add(new Target("local", dir.toString(), folder));
			}
			else {
				problems.add("Folder to scan not found: " + dir);
			}
		}
		// Else backups would be scanned (and edited) as if they were documents
		for (Target t : targets) {
			if (backupDir != null && backupDir.startsWith(Path.of(t.location()))) {
				problems.add("The backup folder " + backupDir + " is inside a folder Kai scans (" + t.entry()
						+ "). Choose a backup folder outside it, e.g.  kai.backup-dir=~/kai-backups");
			}
		}
		if (!box.isEmpty()) {
			// Placeholder: the Box adapter is not built yet
			problems.add("kai.scan.box: Box folders are not supported yet. Put a # in front of that line.");
		}
		if (local.isEmpty() && box.isEmpty()) {
			problems.add("Nothing to scan. Add at least one folder, for example:  kai.scan.local=./docs");
		}

		int parallel = 4;
		String par = value(p, "kai.scan.parallel");
		if (par != null) {
			try {
				parallel = Integer.parseInt(par);
			}
			catch (NumberFormatException e) {
				parallel = 0;
			}
			if (parallel < 1) {
				problems.add("kai.scan.parallel must be a whole number of 1 or more, but is \"" + par + "\"");
			}
		}

		if (!problems.isEmpty()) {
			throw new Invalid("Kai's settings file has a problem:\n  " + file + "\n\n  - " + String.join("\n  - ", problems));
		}
		return new Loaded(file, springFile, new KaiProperties(backupDir, List.copyOf(targets), parallel));
	}

	// Program arguments for Spring: drop --config, and load the same file for the spring.* keys
	// (additional-location: its keys override application.properties)
	public static String[] springArgs(String[] args, Path file) {
		return Stream.concat(Arrays.stream(args).filter(a -> !a.startsWith("--config=")),
				Stream.of("--spring.config.additional-location=file:" + file.toString().replace('\\', '/')))
				.toArray(String[]::new);
	}

	// Print the reason and stop. In a real terminal (not the IDE console), wait for Enter first
	// so the message stays on screen if the window would otherwise close.
	public static void exit(String message) {
		System.err.println();
		System.err.println(message);
		System.err.println();
		Console console = System.console();
		if (console != null && console.isTerminal()) {
			System.err.println("Press Enter to close this window.");
			console.readLine();
		}
		System.exit(1);
	}

	private static Path locate(String[] args) {
		for (String arg : args) {
			if (arg.startsWith("--config=")) {
				return Path.of(arg.substring("--config=".length())).toAbsolutePath().normalize();
			}
		}
		return Path.of(FILE).toAbsolutePath();
	}

	private static Path path(Path home, String value) {
		// Windows "Copy as path" adds quotes: "C:\Team\Docs"
		String v = value.length() > 1 && value.startsWith("\"") && value.endsWith("\"") ? value.substring(1, value.length() - 1).trim() : value;
		v = v.startsWith("~/") ? System.getProperty("user.home") + v.substring(1) : v;
		return home.resolve(v).toAbsolutePath().normalize();
	}

	private static String value(Properties p, String key) {
		String v = p.getProperty(key);
		return v == null || v.isBlank() ? null : v.trim();
	}

	// "a, b ,c" -> [a, b, c]; several folders on one line, separated by commas
	private static List<String> list(Properties p, String key) {
		String v = value(p, key);
		return v == null ? List.of() : Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}
}
