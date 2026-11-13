package ua.bookloom.llm.profile;

import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Finds the model id in a pasted {@code ollama}, {@code lms}, {@code llama-server}, {@code hf download},
 * {@code mlx_lm} or {@code vllm} command. The text is only split into words; a second command after ";", "&" or "|"
 * is dropped unread.
 */
@Slf4j
final class CommandReader {

    private static final Pattern TOOL = Pattern.compile(
            "^(\\$\\s*)?(ollama|lms|llama-server|llama-cli|llama-bench|huggingface-cli|hf|mlx_lm\\.server"
                    + "|mlx_lm\\.generate|vllm)\\b.*",
            Pattern.DOTALL);
    private static final Pattern DEAD_SECOND_COMMAND = Pattern.compile("[;&|].*", Pattern.DOTALL);
    private static final List<String> HF_VALUE_FLAGS =
            List.of("--include", "--exclude", "--local-dir", "--revision", "--token", "--cache-dir", "--repo-type");
    private static final String HUB_PREFIX = "hf.co/";

    /** Returns the id the command names, or the line itself when it is not a command this reader knows. */
    String read(final String line, final PasteContext ctx) {
        if (!TOOL.matcher(line).matches()) {
            return line;
        }
        final String bare =
                DEAD_SECOND_COMMAND.matcher(line.replaceFirst("^[$\\s]+", "")).replaceFirst("");
        final List<String> words = ShellWords.split(bare);
        if (words.isEmpty()) {
            return line;
        }
        final String tool = words.getFirst();
        final List<String> rest = words.subList(1, words.size());
        final String sub = !rest.isEmpty() && !rest.getFirst().startsWith("-") ? rest.getFirst() : null;
        final String id = pick(tool, sub, rest, ctx);
        log.debug("Command tool={} sub={} id={} form={}", tool, sub, id, ctx.inputForm);
        return id == null ? line : id;
    }

    private @Nullable String pick(
            final String tool, final @Nullable String sub, final List<String> rest, final PasteContext ctx) {
        return switch (tool) {
            case "ollama" -> verb(rest, sub, List.of("run", "pull", "show", "rm", "cp", "create"), "ollama", ctx);
            case "lms" -> verb(rest, sub, List.of("get", "load", "import", "unload"), "lms", ctx);
            case "llama-server", "llama-cli", "llama-bench" -> llamaServer(rest, ctx);
            case "huggingface-cli", "hf" -> "download".equals(sub) ? hfDownload(rest, ctx) : null;
            case "mlx_lm.server", "mlx_lm.generate" -> flagged(rest, ctx);
            case "vllm" ->
                "serve".equals(sub) ? form(firstArgument(rest.subList(1, rest.size())), "cmd:vllm serve", ctx) : null;
            default -> null;
        };
    }

    private static @Nullable String verb(
            final List<String> rest,
            final @Nullable String sub,
            final List<String> verbs,
            final String tool,
            final PasteContext ctx) {
        return sub != null && verbs.contains(sub)
                ? form(firstArgument(rest.subList(1, rest.size())), "cmd:" + tool + " " + sub, ctx)
                : null;
    }

    private static @Nullable String form(final @Nullable String id, final String inputForm, final PasteContext ctx) {
        if (id != null) {
            ctx.inputForm = inputForm;
        }
        return id;
    }

    private static @Nullable String flagged(final List<String> rest, final PasteContext ctx) {
        return form(flagValue(rest, List.of("--model")), "cmd:mlx_lm", ctx);
    }

    private static @Nullable String llamaServer(final List<String> rest, final PasteContext ctx) {
        final String hf = flagValue(rest, List.of("-hf", "-hfr", "--hf-repo"));
        final String file = flagValue(rest, List.of("-hff", "--hf-file"));
        final String model = flagValue(rest, List.of("-m", "--model"));
        ctx.serverAlias = flagValue(rest, List.of("-a", "--alias"));
        if (hf != null) {
            ctx.inputForm = "cmd:llama-server -hf";
            if (file == null) {
                return HUB_PREFIX + hf;
            }
            ctx.repo = hf.split(":", 2)[0];
            return ctx.repo + "/" + file;
        }
        return form(model, "cmd:llama-server -m", ctx);
    }

    private static @Nullable String hfDownload(final List<String> rest, final PasteContext ctx) {
        final List<String> args = new java.util.ArrayList<>();
        boolean skip = false;
        for (final String word : rest.subList(1, rest.size())) {
            if (skip) {
                skip = false;
            } else if (word.startsWith("-")) {
                skip = !word.contains("=") && HF_VALUE_FLAGS.contains(word);
            } else {
                args.add(word);
            }
        }
        if (args.isEmpty()) {
            return null;
        }
        return form(args.size() == 1 ? args.getFirst() : args.get(0) + "/" + args.get(1), "cmd:hf download", ctx);
    }

    private static @Nullable String firstArgument(final List<String> words) {
        return words.stream().filter(w -> !w.startsWith("-")).findFirst().orElse(null);
    }

    private static @Nullable String flagValue(final List<String> words, final List<String> names) {
        for (int i = 0; i < words.size(); i++) {
            final String word = words.get(i);
            if (names.contains(word) && i + 1 < words.size()) {
                return words.get(i + 1);
            }
            for (final String name : names) {
                if (word.startsWith(name + "=")) {
                    return word.substring(name.length() + 1);
                }
            }
        }
        return null;
    }
}
