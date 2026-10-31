package ua.bookloom.api.llm;

/** The shape of what the person pasted. */
public enum ModelInputForm {
    /** A bare model id, such as {@code gemma4:e4b} or {@code org/name}. */
    MODEL_ID,
    /** A Hugging Face link. */
    HUGGING_FACE_URL,
    /** An Ollama library link. */
    OLLAMA_URL,
    /** A command such as {@code ollama run}, {@code lms get} or {@code llama-server -hf}. */
    COMMAND,
    /** A model file name or path. */
    FILE
}
