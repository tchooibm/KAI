/**
 * STAGE 4 placeholder: ChangeWriter (no LLM).
 * On Finalize: write the final HTML report, back up the originals to kai.backup-dir,
 * write the approved files, and restore every file from the backup if any write fails.
 * Reads and writes only through DocumentRepository, so rollback works for any adapter.
 */
package com.example.kai.writer;
