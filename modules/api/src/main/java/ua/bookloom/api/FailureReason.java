package ua.bookloom.api;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Why a failure happened, where its {@link ErrorCode} alone cannot tell a person what to fix.
 *
 * <p>Services word every {@link AppError} in English and know no display language, so the interface words a failure
 * in another language by its code. A full disk and a missing folder share the code {@link ErrorCode#validation} but
 * need different advice; the reason travels in {@link AppError#details()} through {@link SafeDetails#withReason} as
 * a fixed token, never as text, so the interface can choose the advice in the display language.
 */
public enum FailureReason {

    /** The folder a file was to be written into does not exist. */
    FOLDER_MISSING("folder-missing"),

    /** The operating system refused access to the folder or the file. */
    ACCESS_DENIED("access-denied"),

    /** The folder, the file or the whole disk is read-only. */
    READ_ONLY("read-only"),

    /** The disk ran out of free space while the file was written. */
    DISK_FULL("disk-full"),

    /** A text file is not in the UTF-8 encoding it is read in. */
    NOT_UTF8("not-utf8"),

    /** A CSV file's header names columns that are missing, unknown or repeated. */
    CSV_HEADER("csv-header"),

    /**
     * A provider answered with an HTTP success whose body is only an error: the address reaches a server that does not
     * serve this path, most often an OpenAI-compatible address missing its {@code /v1}.
     */
    WRONG_PATH("wrong-path"),

    /** Ollama does not have the model installed; the person pulls it or picks another. */
    MODEL_NOT_PULLED("model-not-pulled"),

    /** The model has no thinking capability, so the reasoning control that was sent is refused. */
    THINKING_UNSUPPORTED("thinking-unsupported"),

    /** The server does not accept the reasoning level that was sent for this model. */
    THINK_LEVEL_INVALID("think-level-invalid"),

    /** The server's queue is full; waiting and sending again can succeed. */
    SERVER_BUSY("server-busy"),

    /** The server rejected an option BookLoom sent with the wrong JSON type; a BookLoom fault, not the person's. */
    OPTION_TYPE("option-type"),

    /** Ollama refused the address the request was sent to (its host guard); the person uses 127.0.0.1 or lets it listen on the network. */
    HOST_FORBIDDEN("host-forbidden"),

    /** Ollama's cloud models are switched off on this machine. */
    CLOUD_DISABLED("cloud-disabled"),

    /** A cloud model needs the person to sign in to Ollama first. */
    CLOUD_SIGNIN("cloud-signin"),

    /** Ollama could not reach its cloud service. */
    CLOUD_UNREACHABLE("cloud-unreachable"),

    /** The model's runner process stopped unexpectedly; the same request may succeed once. */
    RUNNER_CRASHED("runner-crashed"),

    /** The machine has too little free memory to load the model. */
    OUT_OF_MEMORY("out-of-memory"),

    /** The model looped on the same tokens and the server aborted; another seed can succeed. */
    REPEAT_LIMIT("repeat-limit"),

    /**
     * A translated text, saved by the person or restored from the model, would add Markdown structure its source did
     * not have, or lose structure it had.
     */
    MARKDOWN_STRUCTURE("markdown-structure"),

    /**
     * A provider said the prompt does not fit the model's window, in any word order: that passage is flagged and the
     * book goes on, because sending it again unchanged cannot fit.
     */
    CONTEXT_OVERFLOW("context-overflow"),

    /** The server closed the connection while it was answering; the same request can be sent once more. */
    TERMINATED("terminated"),

    /** The server has no model loaded, so there is nothing to answer yet; loading one fixes it. */
    NO_MODEL_LOADED("no-model-loaded"),

    /** The model was unloaded while the call was waiting or running (idle timeout, eviction, the person). */
    MODEL_UNLOADED("model-unloaded"),

    /** The structured-output grammar is too large or cannot be parsed; sending it again cannot help. */
    GRAMMAR_TOO_LARGE("grammar-too-large"),

    /** The server cannot use the structured-output schema BookLoom sent; sending it again cannot help. */
    SCHEMA_INVALID("schema-invalid"),

    /** The model's runtime has no structured output at all. */
    STRUCTURED_UNAVAILABLE("structured-unavailable"),

    /** The server's own engine could not be reached (crashed or reloading); the same request can be sent once more. */
    ENGINE_UNREACHABLE("engine-unreachable"),

    /** The model's chat template refused the message layout BookLoom sent. */
    TEMPLATE_REJECTED("template-rejected"),

    /** The server is still loading the model and answers nothing else until it is ready. */
    MODEL_LOADING("model-loading"),

    /** The server could not apply the schema to the model's reply; the reply is not the shape that was asked for. */
    SCHEMA_NOT_APPLIED("schema-not-applied"),

    /** The server refused the sign-in: it needs an API key, or the one given is wrong. */
    AUTH("auth"),

    /** The server wrote more tokens than the answer cap allowed and stopped by itself, so it ignores the cap. */
    CAP_IGNORED("cap-ignored"),

    /**
     * The server stopped the reply at the answer cap before any text was written, as a thinking model does when
     * reasoning uses the whole cap; the cap was honoured, the reply is empty.
     */
    CAP_SPENT("cap-spent"),

    /**
     * A server that answers any model id with whatever model it has loaded wrote the reply with another model than the
     * one asked for; the reply is not kept, and the person checks the model id.
     */
    WRONG_MODEL_SERVED("wrong-model-served"),

    /**
     * A server that answers any model id with whatever model it has loaded does not list the model id in its own list,
     * so no chat request is sent; the details carry the engine and the id, so the window can say which server lacks it.
     */
    MODEL_NOT_LISTED("model-not-listed"),

    /**
     * The address belongs to an app whose server refuses the answer schema every translation call needs (GPT4All), so
     * the connection test stops it before any book text is sent.
     */
    NOT_SUITABLE_STRUCTURED("not-suitable-structured"),

    /**
     * The address chats over the person's documents rather than with a model (AnythingLLM's OpenAI path), so the
     * connection test stops it before any book text is sent.
     */
    NOT_SUITABLE_DOCUMENTS("not-suitable-documents"),

    /**
     * A provider saved as Ollama's own connection is answered by another engine (LM Studio, llama-server, …), whose
     * name the details carry through {@link SafeDetails#withEngine}, so the test can say which one it is.
     */
    ENGINE_MISMATCH("engine-mismatch"),

    /** A provider's failure that no catalogue row explains; the error's code alone decides what happens next. */
    UNCLASSIFIED("unclassified");

    private static final String PREFIX = "reason=";

    private final String token;

    FailureReason(final String token) {
        this.token = token;
    }

    /**
     * The fixed word this reason is written as in a failure's details.
     *
     * @return the token, lower case with hyphens
     */
    public String token() {
        return token;
    }

    /**
     * Reads the reason back from a failure's rendered details.
     *
     * @param details the rendered details, or {@code null}
     * @return the reason if the details start with one, or empty if they do not
     */
    public static Optional<FailureReason> in(final @Nullable String details) {
        if (details == null || !details.startsWith(PREFIX)) {
            return Optional.empty();
        }
        final int end = details.indexOf(',');
        final String found = details.substring(PREFIX.length(), end < 0 ? details.length() : end);
        return Arrays.stream(values())
                .filter(reason -> Objects.equals(reason.token, found))
                .findFirst();
    }

    /**
     * The word a log line writes as its {@code reason=} field, read from typed details only, so that no reply body or
     * exception message can reach the line.
     *
     * @param details the rendered details, or {@code null}
     * @return the reason's token if the details start with one, or {@code none} if they do not
     */
    public static String tokenIn(final @Nullable String details) {
        return in(details).map(FailureReason::token).orElse("none");
    }
}
