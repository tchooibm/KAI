package com.example.kai.orchestrator;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import com.example.kai.agent.scanner.ScannerAgent;
import com.example.kai.config.KaiProperties;
import com.example.kai.repository.DocumentRepository;

// The only thing the chat talks to. Plain Java: decides which agent runs when
// and passes results between them. Agents never talk to the user directly.
// Stage 2: repository -> ScannerAgent (LLM) -> Report. Later: EditorAgent, ReviewerAgent, ChangeWriter.
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

	// Checks up to kai.scan.parallel files at the same time; the report keeps the file-list order
	public Finding.Report scan(String instruction) throws IOException {
		DocumentRepository repo = repository(target.repository());
		List<Future<Finding>> futures;
		try (ExecutorService pool = Executors.newFixedThreadPool(target.parallel())) { // close() waits for all
			futures = repo.list(target.location()).stream()
					.map(id -> pool.submit(() -> check(repo, instruction, id)))
					.toList();
		}
		List<Finding> findings = futures.stream().map(Future::resultNow).toList();
		return new Finding.Report(instruction, target.repository(), target.location(), findings);
	}

	// Never throws: one bad file or model call must not stop the scan, and must not look like "not affected"
	private Finding check(DocumentRepository repo, String instruction, String id) {
		boolean editable = repo.canWrite(id);
		try {
			ScannerAgent.Verdict v = scanner.assess(instruction, id, repo.read(target.location(), id));
			Finding.Status status = v.affected() ? Finding.Status.AFFECTED : Finding.Status.NOT_AFFECTED;
			return new Finding(id, status, v.reason(), editable);
		}
		catch (Exception e) {
			log.warn("SCANNER {} -> ERROR", id, e);
			return new Finding(id, Finding.Status.ERROR, e.getClass().getSimpleName() + ": " + e.getMessage(), editable);
		}
	}

	private DocumentRepository repository(String type) {
		DocumentRepository repo = repositories.get(type);
		if (repo == null) {
			throw new IllegalArgumentException("Unknown repository '" + type + "', available: " + repositories.keySet());
		}
		return repo;
	}
}
