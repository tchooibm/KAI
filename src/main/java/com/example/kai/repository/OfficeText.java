package com.example.kai.repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

// Office/PDF text with the structure kept as markdown headings, so the Sectioner cuts it the
// same way as a .md file: "# Page 3", "# Slide 2" (+ "## Notes"), and real headings in Word files.
// Streams from disk to a Writer (no character limit, no images); slide masters are left out.
final class OfficeText {

	private OfficeText() {
	}

	// Writes the text to out as it is parsed, so a big file is never held in memory
	static void extract(Path file, Writer out) throws IOException {
		String name = file.getFileName().toString().toLowerCase();
		if (name.endsWith(".pptx")) {
			readSlides(file, out);
			return;
		}
		// A PDF starts with its title/metadata (e.g. "(anonymous)"): not document text, so it is
		// dropped until the first page starts
		Handler h = new Handler(out, name.endsWith(".pdf"));
		ParseContext context = new ParseContext();
		PDFParserConfig pdf = new PDFParserConfig();
		pdf.setMaxMainMemoryBytes(PDF_MEMORY); // above this, PDFBox buffers in a temp file
		pdf.setExtractInlineImages(false);
		context.set(PDFParserConfig.class, pdf);
		try (TikaInputStream in = TikaInputStream.get(file)) {
			new AutoDetectParser().parse(in, h, new Metadata(), context);
		}
		catch (TikaException | SAXException e) {
			if (e.getCause() instanceof IOException io && e instanceof SAXException) {
				throw io; // writing the text failed (e.g. disk full)
			}
			throw new IOException("Cannot extract text from " + file.getFileName() + ": " + e.getMessage(), e);
		}
	}

	private static final long PDF_MEMORY = 64L * 1024 * 1024;

	// Tika lists slides in file order, which is not always the order PowerPoint shows them. So a
	// pptx is read straight from its zip: presentation.xml gives the slide order, each slide's
	// .rels gives its notes. Only the text (a:t) is taken; masters and layouts are never read.
	private static void readSlides(Path file, Writer out) throws IOException {
		try (ZipFile zip = new ZipFile(file.toFile())) {
			Map<String, String> targets = relationships(zip, "ppt/_rels/presentation.xml.rels");
			Document pres = xml(zip, "ppt/presentation.xml");
			NodeList ids = pres.getElementsByTagNameNS("*", "sldId");
			for (int i = 0; i < ids.getLength(); i++) {
				String rid = ((Element) ids.item(i)).getAttributeNS(REL_NS, "id");
				String slide = resolve("ppt/presentation.xml", targets.get(rid));
				StringBuilder text = new StringBuilder(); // one slide at a time
				text.append("\n\n# Slide ").append(i + 1).append("\n\n");
				appendText(text, xml(zip, slide));
				for (String target : relationships(zip, relsOf(slide)).values()) {
					String path = resolve(slide, target);
					if (path.contains("/notesSlides/")) {
						StringBuilder notes = new StringBuilder();
						appendText(notes, xml(zip, path));
						if (!notes.isEmpty()) {
							text.append("## Notes\n\n").append(notes);
						}
					}
				}
				out.append(text);
			}
		}
		catch (ParserConfigurationException | SAXException | RuntimeException e) {
			throw new IOException("Cannot extract text from " + file.getFileName() + ": " + e.getMessage(), e);
		}
	}

	private static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

	private static Document xml(ZipFile zip, String name) throws IOException, ParserConfigurationException, SAXException {
		ZipEntry entry = zip.getEntry(name);
		if (entry == null) {
			throw new IOException("Missing " + name);
		}
		DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
		f.setNamespaceAware(true);
		f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // no external entities
		try (InputStream in = zip.getInputStream(entry)) {
			return f.newDocumentBuilder().parse(in);
		}
	}

	// relationship id -> target; a missing .rels file means no relationships
	private static Map<String, String> relationships(ZipFile zip, String name)
			throws IOException, ParserConfigurationException, SAXException {
		Map<String, String> out = new LinkedHashMap<>();
		if (zip.getEntry(name) != null) {
			NodeList rels = xml(zip, name).getElementsByTagNameNS("*", "Relationship");
			for (int i = 0; i < rels.getLength(); i++) {
				Element r = (Element) rels.item(i);
				out.put(r.getAttribute("Id"), r.getAttribute("Target"));
			}
		}
		return out;
	}

	private static String relsOf(String part) {
		int slash = part.lastIndexOf('/');
		return part.substring(0, slash + 1) + "_rels/" + part.substring(slash + 1) + ".rels";
	}

	// A relationship target is relative to the part that owns it ("../notesSlides/notesSlide1.xml")
	private static String resolve(String owner, String target) {
		return URI.create(owner).resolve(URI.create(target)).getPath();
	}

	// One block per paragraph; field text (slide numbers, dates) is skipped
	private static void appendText(StringBuilder out, Document doc) {
		NodeList paragraphs = doc.getElementsByTagNameNS("*", "p");
		for (int i = 0; i < paragraphs.getLength(); i++) {
			StringBuilder line = new StringBuilder();
			for (Node n = paragraphs.item(i).getFirstChild(); n != null; n = n.getNextSibling()) {
				if ("r".equals(n.getLocalName())) {
					line.append(n.getTextContent());
				}
				else if ("br".equals(n.getLocalName())) {
					line.append('\n');
				}
			}
			if (!line.toString().isBlank()) {
				out.append(line).append("\n\n");
			}
		}
	}

	private static final class Handler extends DefaultHandler {

		private final Writer out;
		private boolean waitForPage; // PDF: nothing is written before the first page
		private int page;
		private int slide;
		private boolean inUnit; // inside a page or slide: headings there are plain text
		private int skip; // depth inside an ignored block (slide master)
		private final java.util.ArrayDeque<String> kinds = new java.util.ArrayDeque<>();

		Handler(Writer out, boolean waitForPage) {
			this.out = out;
			this.waitForPage = waitForPage;
		}

		private void write(String s) throws SAXException {
			write(s.toCharArray(), 0, s.length());
		}

		private void write(char[] ch, int start, int length) throws SAXException {
			if (skip > 0 || waitForPage) {
				return;
			}
			try {
				out.write(ch, start, length);
			}
			catch (IOException e) {
				throw new SAXException(e);
			}
		}

		@Override
		public void startElement(String uri, String local, String qName, Attributes atts) throws SAXException {
			String name = local.isEmpty() ? qName : local;
			String cls = atts.getValue("class");
			String kind = "";
			if (name.equals("div") && cls != null) {
				switch (cls) {
					case "page" -> {
						kind = "unit";
						inUnit = true;
						waitForPage = false;
						write("\n\n# Page " + ++page + "\n\n");
					}
					case "slide-content" -> {
						kind = "unit";
						inUnit = true;
						write("\n\n# Slide " + ++slide + "\n\n");
					}
					case "slide-notes" -> {
						kind = "notes";
						write("\n\n## Notes\n\n");
					}
					case "slide-master-content" -> {
						kind = "skip";
						skip++;
					}
					default -> {
					}
				}
			}
			else if (!inUnit && name.length() == 2 && name.charAt(0) == 'h' && Character.isDigit(name.charAt(1))) {
				kind = "h";
				write("\n\n" + "#".repeat(Math.max(1, Math.min(6, name.charAt(1) - '0'))) + " ");
			}
			else if (name.equals("br")) {
				write("\n");
			}
			kinds.push(kind);
		}

		@Override
		public void endElement(String uri, String local, String qName) throws SAXException {
			String name = local.isEmpty() ? qName : local;
			String kind = kinds.isEmpty() ? "" : kinds.pop();
			switch (kind) {
				case "skip" -> skip--;
				case "unit" -> inUnit = false;
				default -> {
				}
			}
			if (name.equals("p") || name.equals("li") || name.equals("tr") || kind.equals("h") || name.matches("h[1-6]")) {
				write("\n\n");
			}
			else if (name.equals("td") || name.equals("th")) {
				write(" ");
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			write(ch, start, length);
		}

		@Override
		public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
			characters(ch, start, length);
		}
	}
}
