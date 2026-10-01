package com.example.kai.orchestrator;

import java.io.IOException;
import java.util.ArrayList;
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
import com.example.kai.config.KaiProperties.Target;
import com.example.kai.repository.DocumentRepository;

// The only thing the chat talks to. Plain Java: decides which agent runs when
// and passes results between them. Agents never talk to the user directly.
// Stage 2: repositories -> ScannerAgent (LLM) -> Report. Later: EditorAgent, ReviewerAgent, ChangeWriter.
@Service
public class Orchestrator {

	private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);

	private final Map<String, DocumentRepository> repositories = new TreeMap<>();
	private final ScannerAgent scanner;
	private final KaiProperties properties;

	// Spring injects every DocumentRepository bean, so new adapters register themselves
	public Orchestrator(List<DocumentRepository> repositories, ScannerAgent scanner, KaiProperties properties) {
		repositories.forEach(r -> this.repositories.put(r.type(), r));
		this.scanner = scanner;
		this.properties = properties;
		properties.targets().forEach(t -> repository(t.type())); // no adapter for a target -> fail at startup
	}

	// Where change requests are checked, from kai.scan.* in the config file
	public List<Target> targets() {
		return properties.targets();
	}

	// Checks up to kai.scan.parallel files at the same time, across all targets;
	// the report keeps the target and file-list order
	public Finding.Report scan(String instruction) throws IOException {
		List<Future<Finding>> futures = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(properties.parallel())) { // close() waits for all
			for (Target target : properties.targets()) {
				DocumentRepository repo = repository(target.type());
				for (String id : repo.list(target.location())) {
					futures.add(pool.submit(() -> check(target, repo, instruction, id)));
				}
			}
		}
		List<Finding> findings = futures.stream().map(Future::resultNow).toList();
		return new Finding.Report(instruction, properties.targets(), findings);
	}

	// Never throws: one bad file or model call must not stop the scan, and must not look like "not affected"
	private Finding check(Target target, DocumentRepository repo, String instruction, String id) {
		boolean editable = repo.canWrite(id);
		try {
			ScannerAgent.Verdict v = scanner.assess(instruction, id, repo.read(target.location(), id));
			Finding.Status status = v.affected() ? Finding.Status.AFFECTED : Finding.Status.NOT_AFFECTED;
			return new Finding(target, id, status, v.reason(), editable);
		}
		catch (Exception e) {
			log.warn("SCANNER {} {} -> ERROR", target.label(), id, e);
			return new Finding(target, id, Finding.Status.ERROR, e.getClass().getSimpleName() + ": " + e.getMessage(), editable);
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
