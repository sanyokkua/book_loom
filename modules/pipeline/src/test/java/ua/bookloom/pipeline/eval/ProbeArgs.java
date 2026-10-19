package ua.bookloom.pipeline.eval;

import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The probe's command line.
 *
 * @param caseId the case to send, or null with {@code --list}
 * @param list whether to list the cases instead of sending one
 * @param listSuite the suite to list, or null for all
 * @param repeat how many times the case is sent; at least one
 * @param dry whether to print the request and make no call
 * @param provider {@code ollama}, {@code lmstudio} or {@code pseudo}
 * @param url the provider endpoint, or null for the provider's usual local address
 * @param model the model id
 * @param window the context window every request is sized to
 */
record ProbeArgs(
        @Nullable String caseId,
        boolean list,
        @Nullable String listSuite,
        int repeat,
        boolean dry,
        String provider,
        @Nullable String url,
        String model,
        int window) {

    static final String DEFAULT_MODEL = PromptEvalTest.DEFAULT_MODEL;
    /** A constant window, so an Ollama GGUF model is never reloaded between probes. */
    static final int DEFAULT_WINDOW = 16384;

    private static final String OLLAMA = "ollama";
    private static final String LMSTUDIO = "lmstudio";
    private static final String PSEUDO = "pseudo";

    ProbeArgs {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
    }

    /**
     * Reads the arguments.
     *
     * @param args the non-null command line
     * @return the arguments
     * @throws IllegalArgumentException when an argument is unknown or malformed; the message names it
     */
    static ProbeArgs parse(final String[] args) {
        final Builder builder = new Builder();
        for (int i = 0; i < args.length; i++) {
            i = builder.apply(args, i);
        }
        if (!builder.list && builder.caseId == null) {
            throw new IllegalArgumentException("--case <id> or --list is required");
        }
        return new ProbeArgs(
                builder.caseId,
                builder.list,
                builder.listSuite,
                builder.repeat,
                builder.dry,
                builder.provider,
                builder.url,
                builder.model,
                builder.window);
    }

    /** The arguments as they are read, one flag at a time. */
    private static final class Builder {
        private @Nullable String caseId;
        private boolean list;
        private @Nullable String listSuite;
        private int repeat = 1;
        private boolean dry;
        private String provider = OLLAMA;
        private @Nullable String url;
        private String model = DEFAULT_MODEL;
        private int window = DEFAULT_WINDOW;

        /** Reads the flag at {@code index} and its value; returns the index of the last argument it used. */
        int apply(final String[] args, final int index) {
            final String flag = args[index];
            switch (flag) {
                case "--dry" -> dry = true;
                case "--list" -> {
                    list = true;
                    final boolean named = index + 1 < args.length && !args[index + 1].startsWith("--");
                    listSuite = named ? args[index + 1] : null;
                    return named ? index + 1 : index;
                }
                case "--case" -> caseId = value(args, index + 1, flag);
                case "--repeat" -> repeat = number(value(args, index + 1, flag), flag);
                case "--window" -> window = number(value(args, index + 1, flag), flag);
                case "--provider" -> provider = provider(value(args, index + 1, flag));
                case "--url" -> url = value(args, index + 1, flag);
                case "--model" -> model = value(args, index + 1, flag);
                default -> throw new IllegalArgumentException("unknown argument " + flag);
            }
            return "--dry".equals(flag) ? index : index + 1;
        }
    }

    boolean isPseudo() {
        return PSEUDO.equals(provider);
    }

    boolean isLmStudio() {
        return LMSTUDIO.equals(provider);
    }

    /** The endpoint to call: the given one, else the provider's usual local address. */
    String endpoint() {
        if (url != null) {
            return url;
        }
        return isLmStudio() ? "http://localhost:1234/v1" : "http://localhost:11434";
    }

    private static String provider(final String value) {
        final String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.equals(OLLAMA) && !lower.equals(LMSTUDIO) && !lower.equals(PSEUDO)) {
            throw new IllegalArgumentException("--provider takes ollama, lmstudio or pseudo, not " + value);
        }
        return lower;
    }

    private static String value(final String[] args, final int index, final String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException(flag + " needs a value");
        }
        return args[index];
    }

    private static int number(final String text, final String flag) {
        try {
            final int number = Integer.parseInt(text.strip());
            if (number <= 0) {
                throw new IllegalArgumentException(flag + " must be positive, not " + text);
            }
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(flag + " takes a number, not " + text, e);
        }
    }
}
