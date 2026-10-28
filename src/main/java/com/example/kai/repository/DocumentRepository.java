package com.example.kai.repository;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;

// Adapter for a place documents live. The orchestrator only talks to this interface,
// so adding SharePoint / OneDrive later = one new @Component implementing it.
//   location: where to look (local: a folder path; SharePoint: e.g. a site/library URL)
//   id:       a document inside that location (local: relative path)
// Documents are streamed, never held whole in memory, so a 500 MB file does not freeze the machine.
public interface DocumentRepository {

	String type(); // matches kai.scan.<type> in kai.properties, e.g. "local"

	List<String> list(String location) throws IOException;

	// The document as plain text, also for docx/pptx/pdf. The caller closes it.
	Reader open(String location, String id) throws IOException;

	// false = Kai can read it but not rewrite it (e.g. docx/pptx/pdf): the user updates it by hand
	boolean canWrite(String id);

	// Changes whenever the file changes (local: size + modified time), for the stale check before writing
	String version(String location, String id) throws IOException;

	// Writes a new version of the file: rewrite copies the original text to out with the edits applied.
	// The file is only replaced once rewrite has finished without an error.
	void write(String location, String id, Rewrite rewrite) throws IOException;

	interface Rewrite {

		void apply(Reader original, Writer out) throws IOException;
	}

	// Exact bytes, for backup and rollback: a restore must give back the identical file
	void copyTo(String location, String id, Path backup) throws IOException;

	void restore(String location, String id, Path backup) throws IOException;
}
