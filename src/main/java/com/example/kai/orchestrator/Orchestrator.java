package com.example.kai.orchestrator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import com.example.kai.agent.scanner.ScannerAgent;
import com.example.kai.config.KaiProperties;
import com.example.kai.repository.DocumentRepository;

// The only thing the chat talks to. Plain Java: decides which agent runs when
// and passes results between them. Agents never talk to the user directly.
// Stage 1: repository -> ScannerAgent -> Report. Later: EditorAgent, ReviewerAgent, ChangeWriter.
@Service
public class Orchestrator {

	private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);

	private final Map<String, DocumentRepository> repositories = new TreeMap<>();
	private final ScannerAgent scanner;
	private final KaiProperties.Scan target;

	// Spring injects every DocumentRepository bean, so new adapters register themselves
	public Orchestrator(List<DocumentRepository> repositories, ScannerAgent scanner, KaiProperties properties) {
		repositories.forEach(r -> this.repositories.put(r.type(), r));
		this.scanner = scanner;
		this.target = properties.scan();
		repository(target.repository()); // typo in kai.scan.repository -> fail at startup
	}

	// Where change requests are checked, from kai.scan.* in the config file
	public KaiProperties.Scan target() {
		return target;
	}

	public Finding.Report scan(String instruction) throws IOException {
		DocumentRepository repo = repository(target.repository());
		List<Finding> findings = new ArrayList<>();
		for (String id : repo.list(target.location())) {
			log.info("SCAN {}:{}", target.repository(), id);
			findings.add(scanner.assess(instruction, id, repo.read(target.location(), id)));
		}
		return new Finding.Report(instruction, target.repository(), target.location(), findings);
	}

	private DocumentRepository repository(String type) {
		DocumentRepository repo = repositories.get(type);
		if (repo == null) {
			throw new IllegalArgumentException("Unknown repository '" + type + "', available: " + repositories.keySet());
		}
		return repo;
	}
}
