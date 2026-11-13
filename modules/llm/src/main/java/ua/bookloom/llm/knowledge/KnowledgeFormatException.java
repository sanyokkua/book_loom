package ua.bookloom.llm.knowledge;

/** A knowledge file is malformed; carries the file and row in its message and never leaves this package. */
final class KnowledgeFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    KnowledgeFormatException(String file, int row, String problem) {
        super(file + " row " + row + ": " + problem);
    }
}
