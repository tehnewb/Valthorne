package valthorne.ui.behavior.file;

import java.util.List;

/**
 * Immutable ordered group representing one user action and its reversible entries.
 * Entries themselves are mutated only by the history's serialized worker.
 *
 * @param name human-readable action used by menu captions
 * @param entries completed entries in execution order
 */
record FileHistoryOperation(String name, List<FileHistoryEntry> entries) {
}
