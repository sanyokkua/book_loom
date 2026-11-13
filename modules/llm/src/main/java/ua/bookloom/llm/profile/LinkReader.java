package ua.bookloom.llm.profile;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Finds the model id in a pasted Hugging Face, Ollama, LM Studio or OpenRouter link. The link is only split into
 * its parts; it is never opened.
 */
@Slf4j
final class LinkReader {

    private static final String MODELS = "models";

    private static final Set<String> HUB_HOSTS = Set.of("huggingface.co", "www.huggingface.co", "hf.co");
    private static final Set<String> OLLAMA_HOSTS = Set.of("ollama.com", "www.ollama.com", "registry.ollama.ai");
    private static final Set<String> NOT_REPOS =
            Set.of("datasets", "spaces", "docs", MODELS, "collections", "papers", "api");
    private static final Set<String> REVISION_MARKERS = Set.of("tree", "blob", "resolve", "raw");
    private static final String MAIN = "main";
    private static final String GGUF = ".gguf";
    private static final int REPO_PARTS = 2;
    private static final int PATH_WITH_REVISION = 4;

    /** Returns the id the link names; the context receives the repository, revision and form. */
    String read(final String link, final PasteContext ctx) {
        final URI uri;
        try {
            uri = URI.create(link.replace(" ", "%20"));
        } catch (IllegalArgumentException _) {
            log.debug("Not a link: {}", link);
            return link;
        }
        final String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        final List<String> parts = pathParts(uri.getRawPath());
        if (HUB_HOSTS.contains(host)) {
            return hub(link, uri, parts, ctx);
        }
        if (OLLAMA_HOSTS.contains(host)) {
            return ollama(link, parts, ctx);
        }
        if ("lmstudio.ai".equals(host) || "www.lmstudio.ai".equals(host)) {
            return twoParts(dropFirst(parts, MODELS), "url:lmstudio", ctx);
        }
        if ("openrouter.ai".equals(host)) {
            return twoParts(dropFirst(parts, MODELS), "url:openrouter", ctx);
        }
        return link;
    }

    private static List<String> pathParts(final @Nullable String rawPath) {
        return rawPath == null
                ? List.of()
                : Arrays.stream(rawPath.split("/"))
                        .filter(p -> !p.isEmpty())
                        .map(p -> URLDecoder.decode(p.replace("+", "%2B"), StandardCharsets.UTF_8))
                        .toList();
    }

    private static List<String> dropFirst(final List<String> parts, final String word) {
        return !parts.isEmpty() && parts.getFirst().equals(word) ? parts.subList(1, parts.size()) : parts;
    }

    private static String twoParts(final List<String> parts, final String form, final PasteContext ctx) {
        ctx.inputForm = form;
        return String.join("/", parts.subList(0, Math.min(REPO_PARTS, parts.size())));
    }

    private static String ollama(final String link, final List<String> pathParts, final PasteContext ctx) {
        final List<String> parts = dropFirst(pathParts, "library").stream()
                .filter(p -> !p.equals("tags") && !p.equals("blobs"))
                .toList();
        ctx.inputForm = "url:ollama";
        if (parts.isEmpty()) {
            return link;
        }
        return parts.size() >= REPO_PARTS ? parts.get(0) + "/" + parts.get(1) : parts.getFirst();
    }

    private String hub(final String link, final URI uri, final List<String> parts, final PasteContext ctx) {
        if (parts.size() < REPO_PARTS || NOT_REPOS.contains(parts.getFirst())) {
            ctx.inputForm = "url:hf:non-model";
            ctx.notModel = true;
            return link;
        }
        final String repo = parts.get(0) + "/" + parts.get(1);
        String file = null;
        if (parts.size() >= PATH_WITH_REVISION && REVISION_MARKERS.contains(parts.get(2))) {
            file = filePath(parts, ctx);
        }
        if (file == null) {
            file = query(uri.getRawQuery(), "show_file_info");
        }
        ctx.inputForm = "url:hf" + (file != null ? ":file" : "");
        ctx.repo = repo;
        return repo + (file != null ? "/" + file : "");
    }

    private static @Nullable String filePath(final List<String> parts, final PasteContext ctx) {
        final String revision = parts.get(3);
        if (!MAIN.equals(revision)) {
            ctx.revision = revision;
        }
        final String sub = parts.size() > PATH_WITH_REVISION
                ? String.join("/", parts.subList(PATH_WITH_REVISION, parts.size()))
                : null;
        if ("tree".equals(parts.get(2))
                && sub != null
                && !sub.toLowerCase(Locale.ROOT).endsWith(GGUF)) {
            ctx.subfolder = sub;
            return null;
        }
        return sub;
    }

    private static @Nullable String query(final @Nullable String rawQuery, final String key) {
        if (rawQuery == null) {
            return null;
        }
        for (final String pair : rawQuery.split("&", -1)) {
            if (pair.startsWith(key + "=")) {
                return URLDecoder.decode(pair.substring(key.length() + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
