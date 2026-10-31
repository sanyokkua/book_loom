package ua.bookloom.llm.profile;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelKind;

/**
 * Takes the file and hub wrapping off an id: instance copy, "@variant", companion and document files, hub prefix,
 * tag, file path, publisher. The result is the words still to be read plus what the wrapping said.
 */
@Slf4j
final class NameSplitter {

    private static final String GGUF_EXTENSION = ".gguf";
    private static final String LATEST = "latest";
    private static final String IMATRIX = "imatrix";
    private static final String SAFETENSORS = "safetensors";
    private static final List<String> HUB_PREFIXES = List.of("hf.co/", "huggingface.co/");
    private static final List<String> OLLAMA_PREFIXES = List.of("registry.ollama.ai/library/", "ollama.com/library/");
    private static final Pattern CACHE_FILE =
            Pattern.compile("([A-Za-z0-9.-]+)_([A-Za-z0-9._-]*?-GGUF)_(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHARD_IN_NAME = Pattern.compile("(\\d{5})-of-(\\d{5})");
    private static final Pattern MMPROJ_WORD = Pattern.compile(".*(?:^|[-_.])mmproj(?:$|[-_.]).*");
    private static final Pattern IMATRIX_WORD = Pattern.compile(".*(?:^|[-_.])imatrix(?:$|[-_.]).*");
    private static final Pattern IMATRIX_DOT = Pattern.compile(".*\\.imatrix-.*");
    private static final Pattern LEAD_SPLIT = Pattern.compile("[-_.]");
    private static final int GGUF_REPO_DEPTH = 3;

    /** Mutable state of one split. */
    private static final class Work {
        String text;

        @Nullable
        String source;

        @Nullable
        String tag;

        @Nullable
        String unspecified;

        @Nullable
        String file;

        @Nullable
        String subfolder;

        boolean gguf;

        Work(final String text) {
            this.text = text;
        }
    }

    NameShape split(final PasteContext ctx) {
        String rest = ctx.text;
        Integer instance = null;
        final Matcher copy = NameVocabulary.INSTANCE.matcher(rest);
        if (copy.matches()) {
            instance = Integer.valueOf(copy.group(2));
            rest = copy.group(1);
        }
        String variant = null;
        final int at = rest.lastIndexOf('@');
        if (at >= 0) {
            variant = rest.substring(at + 1);
            rest = rest.substring(0, at);
        }
        final Stem stem = stemOf(rest);
        rest = stem.name();
        final String format = stem.format();
        final Auxiliary aux = rest.toLowerCase(Locale.ROOT).endsWith(GGUF_EXTENSION)
                ? null
                : auxiliaryOf(rest, stem.base(), stem.ext());
        if (aux != null) {
            log.debug("Auxiliary name={} kind={}", rest, aux.kind());
            return NameShape.ofAuxiliary(rest, aux);
        }
        return splitPath(rest, ctx, instance, variant, format);
    }

    /** The name with a weights extension taken off, plus the lower-case file name and extension the checks read. */
    private record Stem(
            String name, String base, String ext, @Nullable String format) {}

    private static Stem stemOf(final String name) {
        final String base = lastSegment(name.toLowerCase(Locale.ROOT));
        final String ext = base.contains(".") ? base.substring(base.lastIndexOf('.') + 1) : "";
        if (NameVocabulary.WEIGHT_EXTENSIONS.contains(ext) && !isModelShard(base)) {
            final String bare = lastSegment(name.substring(0, name.length() - ext.length() - 1));
            return new Stem(bare, bare.toLowerCase(Locale.ROOT), "", SAFETENSORS.equals(ext) ? SAFETENSORS : null);
        }
        return new Stem(name, base, ext, null);
    }

    private static @Nullable Auxiliary auxiliaryOf(final String name, final String base, final String ext) {
        if ("gguf_file".equals(ext)) {
            return new Auxiliary(ModelKind.SIDECAR, IMATRIX, null, null, null);
        }
        if (!NameVocabulary.AUXILIARY_NAMES.contains(base)
                && !NameVocabulary.AUXILIARY_EXTENSIONS.contains(ext)
                && !NameVocabulary.WEIGHT_EXTENSIONS.contains(ext)) {
            return null;
        }
        if (IMATRIX.equals(ext) || ("dat".equals(ext) && base.contains(IMATRIX))) {
            final String stem = IMATRIX.equals(ext) ? name.substring(0, name.length() - IMATRIX.length() - 1) : null;
            return new Auxiliary(ModelKind.SIDECAR, IMATRIX, null, null, stem);
        }
        if (SAFETENSORS.equals(ext)) {
            final Matcher shard = SHARD_IN_NAME.matcher(base);
            final String text =
                    shard.find() ? Integer.parseInt(shard.group(1)) + "/" + Integer.parseInt(shard.group(2)) : null;
            return new Auxiliary(ModelKind.WEIGHTS_SHARD, null, SAFETENSORS, text, null);
        }
        return new Auxiliary(ModelKind.NOT_MODEL, null, null, null, null);
    }

    private static boolean isModelShard(final String base) {
        return NameVocabulary.WEIGHT_STEMS.stream().anyMatch(base::startsWith);
    }

    private NameShape splitPath(
            final String name,
            final PasteContext ctx,
            final @Nullable Integer instance,
            final @Nullable String variant,
            final @Nullable String format) {
        final Work work = new Work(name);
        stripSources(work);
        hubTag(work, ctx);
        readFile(work, ctx);
        if (work.tag == null
                && !"hf".equals(work.source)
                && lastSegment(work.text).contains(":")) {
            work.tag = work.text.substring(work.text.lastIndexOf(':') + 1);
            work.text = work.text.substring(0, work.text.lastIndexOf(':'));
        }
        return finish(work, ctx, instance, variant, format);
    }

    private static String lastSegment(final String text) {
        return text.substring(text.lastIndexOf('/') + 1);
    }

    private static void stripSources(final Work work) {
        for (final String prefix : HUB_PREFIXES) {
            if (work.text.startsWith(prefix)) {
                work.text = work.text.substring(prefix.length());
                work.source = "hf";
            }
        }
        for (final String prefix : OLLAMA_PREFIXES) {
            if (work.text.startsWith(prefix)) {
                work.text = work.text.substring(prefix.length());
                work.source = "ollama";
            }
        }
    }

    private static void hubTag(final Work work, final PasteContext ctx) {
        if (!"hf".equals(work.source) || !lastSegment(work.text).contains(":")) {
            return;
        }
        final int colon = work.text.lastIndexOf(':');
        final String head = work.text.substring(0, colon);
        final String tail = work.text.substring(colon + 1);
        if (tail.toLowerCase(Locale.ROOT).endsWith(GGUF_EXTENSION)) {
            ctx.repo = head;
            work.text = head + "/" + tail;
        } else if (LATEST.equalsIgnoreCase(tail)) {
            work.text = head;
            work.unspecified = LATEST;
        } else {
            work.text = head;
            work.tag = tail;
        }
    }

    private static void readFile(final Work work, final PasteContext ctx) {
        if (!work.text.toLowerCase(Locale.ROOT).endsWith(GGUF_EXTENSION)) {
            return;
        }
        work.gguf = true;
        work.text = work.text.substring(0, work.text.length() - GGUF_EXTENSION.length());
        final Matcher cached = CACHE_FILE.matcher(work.text);
        if (!work.text.contains("/") && cached.matches()) {
            ctx.repo = cached.group(1) + "/" + cached.group(2);
            work.text = ctx.repo + "/" + cached.group(3);
        }
        if (!work.text.contains("/")) {
            return;
        }
        locateFile(work, ctx);
    }

    private static void locateFile(final Work work, final PasteContext ctx) {
        String rest = work.text;
        if (ctx.repo != null && rest.startsWith(ctx.repo + "/")) {
            rest = rest.substring(ctx.repo.length() + 1);
        }
        List<String> dirs = List.of(rest.split("/"));
        if (dirs.size() == GGUF_REPO_DEPTH
                && ctx.repo == null
                && dirs.get(1).toLowerCase(Locale.ROOT).endsWith("gguf")) {
            ctx.repo = dirs.get(0) + "/" + dirs.get(1);
            dirs = dirs.subList(2, dirs.size());
        }
        work.file = dirs.getLast() + GGUF_EXTENSION;
        final String parent = dirs.size() >= 2 ? dirs.get(dirs.size() - 2) : null;
        final boolean folder = parent != null
                && (ctx.repo != null
                        || NameVocabulary.QUANT
                                .matcher(parent.toLowerCase(Locale.ROOT))
                                .matches());
        work.subfolder = folder ? parent : null;
        work.text = dirs.getLast();
    }

    private NameShape finish(
            final Work work,
            final PasteContext ctx,
            final @Nullable Integer instance,
            final @Nullable String variant,
            final @Nullable String format) {
        final int slash = work.text.lastIndexOf('/');
        final String rest = work.text.substring(slash + 1);
        String namespace = slash >= 0 ? work.text.substring(0, slash) : null;
        if (namespace == null && ctx.repo != null) {
            namespace = ctx.repo.split("/", 2)[0];
        }
        final String unspecified = isMovingTag(work) ? LATEST : work.unspecified;
        final String text = textOf(work, rest, variant);
        final String subfolder = work.subfolder != null ? work.subfolder : ctx.subfolder;
        final boolean noTag = "hf".equals(work.source) && work.tag == null && !work.gguf;
        log.debug("Split text={} namespace={} unspecified={} file={}", text, namespace, unspecified, work.file);
        return new NameShape(
                namespace,
                quantizerOf(namespace),
                text,
                instance,
                unspecified,
                noTag,
                work.gguf,
                sidecarOf(work),
                subfolder,
                work.file,
                ctx.repo,
                format,
                null);
    }

    private static boolean isMovingTag(final Work work) {
        final boolean hubTag = "hf".equals(work.source) && work.tag != null;
        return !hubTag && work.tag != null && LATEST.equalsIgnoreCase(work.tag);
    }

    private static String textOf(final Work work, final String rest, final @Nullable String variant) {
        final String tagged = work.tag == null ? rest : rest + "-" + work.tag;
        final String text = NameVocabulary.LATEST_TAIL.matcher(tagged).replaceFirst("");
        return variant == null ? text : text + "-" + variant.toLowerCase(Locale.ROOT);
    }

    private static @Nullable String quantizerOf(final @Nullable String namespace) {
        final String lower = namespace == null ? null : namespace.toLowerCase(Locale.ROOT);
        return lower != null && NameVocabulary.QUANTIZERS.contains(lower) ? lower : null;
    }

    private static @Nullable String sidecarOf(final Work work) {
        if (!work.gguf) {
            return null;
        }
        final String low = work.text.toLowerCase(Locale.ROOT);
        final String lead = LEAD_SPLIT.split(low, 2)[0];
        if (NameVocabulary.SIDECAR_LEAD.contains(lead)) {
            return lead;
        }
        if (MMPROJ_WORD.matcher(low).matches()) {
            return "mmproj";
        }
        return IMATRIX_WORD.matcher(low).matches() && !IMATRIX_DOT.matcher(low).matches() ? IMATRIX : null;
    }
}
