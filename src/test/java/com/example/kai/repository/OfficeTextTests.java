package com.example.kai.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

// Reads the sample Office files and checks the text lands under the right slide / page / heading.
// A failure prints the whole extracted text, and the text is also saved to target/office-dump/.
class OfficeTextTests {

	private static final Path DIR = Path.of("sampleDocs/office");

	private static String read(String name) throws Exception {
		StringWriter out = new StringWriter();
		OfficeText.extract(DIR.resolve(name), out);
		String text = out.toString();
		Path dump = Path.of("target/office-dump");
		Files.createDirectories(dump);
		Files.writeString(dump.resolve(name + ".txt"), text);
		return text;
	}

	// the text that follows a heading line, up to the next "# " heading
	private static String under(String text, String heading) {
		int i = text.indexOf(heading);
		assertTrue(i >= 0, "no heading " + heading + " in:\n" + text);
		int next = text.indexOf("\n# ", i + heading.length());
		return text.substring(i, next < 0 ? text.length() : next);
	}

	@Test
	void pptxTextIsUnderItsOwnSlide() throws Exception {
		String t = read("kai-overview.pptx");
		assertTrue(under(t, "# Slide 3").contains("8080"), "slide 3 should hold port 8080:\n" + t);
		assertTrue(!under(t, "# Slide 5").contains("8080"), "slide 5 must not hold port 8080:\n" + t);
	}

	@Test
	void pdfTextIsUnderItsOwnPage() throws Exception {
		String t = read("release-notes.pdf");
		assertTrue(under(t, "# Page 3").contains("8080"), t);
		assertTrue(!under(t, "# Page 4").contains("8080"), t);
	}

	@Test
	void pdfMetadataIsDropped() throws Exception {
		assertTrue(read("release-notes.pdf").strip().startsWith("# Page 1"));
	}

	@Test
	void docxTextIsUnderItsHeading() throws Exception {
		String t = read("admin-guide.docx");
		assertTrue(under(t, "# Requirements").contains("Java 17"), t);
		assertTrue(under(t, "Starting the server").contains("8080"), t);
	}
}
