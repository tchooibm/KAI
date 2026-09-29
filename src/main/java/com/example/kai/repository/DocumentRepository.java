package com.example.kai.repository;

import java.io.IOException;
import java.util.List;

// Adapter for a place documents live. The orchestrator only talks to this interface,
// so adding SharePoint / OneDrive later = one new @Component implementing it.
//   location: where to look (local: a folder path; SharePoint: e.g. a site/library URL)
//   id:       a document inside that location (local: relative path)
public interface DocumentRepository {

	String type(); // shown in the UI dropdown, e.g. "local"

	List<String> list(String location) throws IOException;

	String read(String location, String id) throws IOException;

	void write(String location, String id, String content) throws IOException; // used from stage 4
}
