package com.example.kai.orchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.kai.agent.editor.EditorAgent.Edit;
import com.example.kai.orchestrator.Sectioner.Paragraph;
import com.example.kai.orchestrator.Sectioner.Section;

class SectionPatchTests {

	@Test
	void splitsOnHeadingsWithPath() {
		List<Section> s = Sectioner.split("intro\n# A\none\n## B\ntwo\n# C\nthree\n");
		assertEquals(List.of("", "A", "A > B", "C"), s.stream().map(Section::headingPath).toList());
	}

	@Test
	void longSectionIsCutAtLineBreaks() {
		String text = ("x".repeat(99) + "\n").repeat(100);
		List<Section> s = Sectioner.split(text);
		assertTrue(s.size() > 1);
		assertTrue(s.stream().allMatch(x -> x.text().length() <= Sectioner.MAX_CHARS && x.text().endsWith("\n")));
		assertEquals(text, String.join("", s.stream().map(Section::text).toList()));
	}

	@Test
	void editReplacesOnlyTheNumberedParagraph() throws Exception {
		String content = "# T\n\nPort 8080.\n\nOther.\n";
		Section sec = Sectioner.split(content).get(0);
		List<Paragraph> ps = Sectioner.paragraphs(sec);
		var spots = Patch.spots(content, ps, List.of(new Edit(2, "Port 9090.")));
		assertEquals("# T\n\nPort 9090.\n\nOther.\n", Patch.apply(content, spots));
	}

	@Test
	void crlfIsKept() throws Exception {
		String content = "# T\r\n\r\nPort 8080.\r\n\r\nOther.\r\n";
		List<Paragraph> ps = Sectioner.paragraphs(Sectioner.split(content).get(0));
		String out = Patch.apply(content, Patch.spots(content, ps, List.of(new Edit(2, "a\nb"))));
		assertEquals("# T\r\n\r\na\r\nb\r\n\r\nOther.\r\n", out);
	}

	@Test
	void unknownParagraphIsRejected() {
		String content = "# T\n\nOne.\n";
		List<Paragraph> ps = Sectioner.paragraphs(Sectioner.split(content).get(0));
		assertThrows(Patch.NoMatch.class, () -> Patch.spots(content, ps, List.of(new Edit(9, "x"))));
	}

	@Test
	void sectionsKnowTheirLine() {
		List<Section> s = Sectioner.split("intro\n\n# A\none\n# B\ntwo\n");
		assertEquals(List.of(1, 3, 5), s.stream().map(Section::line).toList());
	}

	@Test
	void aVeryLongLineIsCutToo() {
		List<Section> s = Sectioner.split("y".repeat(Sectioner.MAX_CHARS * 2 + 5));
		assertEquals(3, s.size());
		assertTrue(s.stream().allMatch(x -> x.text().length() <= Sectioner.MAX_CHARS));
	}

	// Finalize streams the file: only the changed section is replaced, the rest is copied as it is
	@Test
	void rewriteReplacesOnlyTheChangedSection() throws Exception {
		String content = "# A\n\nKeep.\n\n# B\n\nPort 8080.\n\n# C\n\nKeep too.\n";
		Section b = Sectioner.split(content).get(1);
		var spots = Patch.spots(b.text(), Sectioner.paragraphs(b), List.of(new Edit(2, "Port 9090.")));
		Proposal p = Proposal.ready("v1", List.of(new Proposal.Part(b, Patch.apply(b.text(), spots))), true, "ok");
		StringWriter out = new StringWriter();
		p.rewrite(new StringReader(content), out);
		assertEquals(content.replace("8080", "9090"), out.toString());
		assertTrue(p.changed());
	}

	@Test
	void rewriteRefusesAFileThatChanged() throws Exception {
		String content = "# A\n\nKeep.\n\n# B\n\nPort 8080.\n";
		Section b = Sectioner.split(content).get(1);
		Proposal p = Proposal.ready("v1", List.of(new Proposal.Part(b, "# B\n\nPort 9090.\n")), true, "ok");
		assertThrows(IOException.class, () -> p.rewrite(new StringReader(content.replace("8080", "8081")), new StringWriter()));
	}

	@Test
	void routerMatchesWholeWordsOnly() {
		assertTrue(!TermRouter.wholeWords(List.of("17")).matcher("since 2017").find());
		assertTrue(TermRouter.wholeWords(List.of("port")).matcher("two ports").find());
		assertTrue(!TermRouter.wholeWords(List.of("port")).matcher("Support").find());
	}
}
