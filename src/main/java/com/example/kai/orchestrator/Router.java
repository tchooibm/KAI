package com.example.kai.orchestrator;

import java.util.function.Predicate;

import com.example.kai.orchestrator.Sectioner.Section;

// Decides which sections of a file the Scanner reads: a section is checked if it matches, and so
// are the sections next to it (the Orchestrator adds those while it streams the file).
// Stage 7 has one implementation (TermRouter). Stage 8 adds embeddings behind this same interface.
interface Router {

	Predicate<Section> matcher(String instruction);
}
