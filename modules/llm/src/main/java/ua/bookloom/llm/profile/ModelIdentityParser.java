package ua.bookloom.llm.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelIdentity;
import ua.bookloom.api.llm.ModelKind;

/**
 * Reads a pasted or reported model name by grammar v2: links, commands, file names, companion files, publisher and
 * vendor prefixes, fine-tunes and distillations, quant flags and split files, plus the few facts a registry or vendor
 * card proves for an exact name ({@link AliasFacts}). A size the name does not give and the quant behind a moving tag
 * stay empty (the engine's own answer fills them in), and an unknown name keeps its own words as the family. Pure text
 * work: nothing is opened, run or sent.
 *
 * <p>Fallback, decided in design D19: if more than 3 of the first 50 real ids met in the live runs parse to a wrong
 * family or quant, shrink the grammar to the forms the corpus lists and lean on the server's metadata instead.
 */
@Slf4j
public final class ModelIdentityParser {

    private static final String EMBEDDING = "embedding";
    private static final String TEXT = "text";
    private static final String MLX = "mlx";
    private static final String MLX_COMMUNITY = "mlx-community";

    private final PasteReader pasteReader = new PasteReader();
    private final NameSplitter splitter = new NameSplitter();
    private final QuantScanner quantScanner = new QuantScanner();
    private final NameTokenReader tokenReader = new NameTokenReader();
    private final FamilyAssembler familyAssembler = new FamilyAssembler();
    private final AliasFacts aliasFacts = new AliasFacts();

    /**
     * Reads one typed or pasted name.
     *
     * @param name any id, tag, repository id, link, command or file name; may be padded with spaces
     * @return the identity and the packaging facts; fields the name does not give are null or empty
     */
    public ParsedName parse(final String name) {
        Objects.requireNonNull(name, "name");
        log.debug("Parse name={}", name);
        return parseContext(pasteReader.read(name));
    }

    /**
     * Reads a name a GGUF header stores as words ({@code general.base_model.0.name} is "Qwen2.5 1.5B"). Whitespace
     * becomes "-" here only: a typed or pasted id is never rewritten that way.
     *
     * @param name the stored name
     * @return the same result {@link #parse(String)} gives for the hyphenated name
     */
    public ParsedName parseBaseModelName(final String name) {
        Objects.requireNonNull(name, "name");
        log.debug("Parse base model name={}", name);
        final PasteContext ctx = pasteReader.read(name.strip().replaceAll("\\s+", "-"));
        ctx.inputForm = ctx.inputForm == null ? "display-name" : ctx.inputForm;
        return parseContext(ctx);
    }

    private ParsedName parseContext(final PasteContext ctx) {
        if (ctx.notModel && ctx.repo == null) {
            return new ParsedName(
                    identityOnly(ModelKind.NOT_MODEL),
                    emptyFacts(null, null, null),
                    origin(ctx, null, null, null, null));
        }
        final NameShape shape = splitter.split(ctx);
        final Auxiliary aux = shape.auxiliary();
        if (aux == null) {
            return model(shape, ctx);
        }
        if (aux.stem() != null) {
            return model(splitter.split(new PasteContext(aux.stem())).withSidecar("imatrix"), ctx);
        }
        final NameFacts facts = emptyFacts(aux.format(), aux.shard(), aux.sidecar());
        return new ParsedName(identityOnly(aux.kind()), facts, origin(ctx, null, null, null, null));
    }

    private ParsedName model(final NameShape shape, final PasteContext ctx) {
        final QuantScan found = quantScanner.scan(shape.text());
        final QuantScan quant = found.quant() == null && shape.subfolder() != null
                ? quantScanner.scanFolder(shape.subfolder(), found)
                : found;
        final NameWords words = NameWords.of(quant.rest(), shape.sidecar());
        final TokenReading reading = tokenReader.read(words.tokens(), shape.sidecar());
        final boolean reranker = dropReranker(reading);
        final FamilyWords family = familyAssembler.assemble(reading.pending, reading.distillIndex);
        final ModelIdentity identity = identity(shape, ctx, reading, family, quant, reranker);
        log.debug(
                "Parsed text={} family={} version={} quant={} kind={}",
                shape.text(),
                family.family(),
                family.version(),
                quant.quant(),
                identity.kind());
        return new ParsedName(
                identity,
                facts(shape, quant, reading, words, family),
                origin(ctx, shape, words.org(), shape.file(), shape.subfolder()));
    }

    private static boolean dropReranker(final TokenReading reading) {
        final boolean found = reading.pending.removeIf(w -> "reranker".equals(w.text()) || "rerank".equals(w.text()));
        if (found) {
            log.debug("Reranker word dropped from the family");
        }
        return found;
    }

    private ModelIdentity identity(
            final NameShape shape,
            final PasteContext ctx,
            final TokenReading reading,
            final FamilyWords family,
            final QuantScan quant,
            final boolean reranker) {
        final boolean embedding = reading.variants.contains(EMBEDDING);
        final List<String> variants = new ArrayList<>(reading.variants);
        if (embedding) {
            variants.remove(EMBEDDING);
            variants.remove(TEXT);
        }
        final Double active = reading.active != null
                ? reading.active
                : aliasFacts.activeSize(family.family(), family.version(), family.line(), reading.size);
        return new ModelIdentity(
                family.family(),
                family.version(),
                family.line(),
                reading.size,
                reading.effective,
                active,
                variants,
                kind(shape, family, embedding, reranker),
                aliasFacts.baseFamily(family.family(), family.distillOf(), family.version(), reading.size),
                family.distillOf(),
                quant.label(),
                needsShow(shape, ctx, quant, reading));
    }

    private static ModelKind kind(
            final NameShape shape, final FamilyWords family, final boolean embedding, final boolean reranker) {
        if (shape.sidecar() != null) {
            return ModelKind.SIDECAR;
        }
        if (reranker) {
            return ModelKind.RERANKER;
        }
        if (embedding) {
            return ModelKind.EMBEDDING;
        }
        final ModelKind byFamily = family.family() == null ? null : NameVocabulary.KIND_BY_FAMILY.get(family.family());
        return byFamily == null ? ModelKind.LLM : byFamily;
    }

    private static List<String> needsShow(
            final NameShape shape, final PasteContext ctx, final QuantScan quant, final TokenReading reading) {
        final List<String> reasons = new ArrayList<>();
        final boolean noQuant = quant.quant() == null;
        if (shape.unspecified() != null) {
            reasons.add("latest");
        }
        if (MLX.equals(reading.format) && noQuant) {
            reasons.add("mlx-default-quant");
        }
        if (reading.qat && noQuant) {
            reasons.add("qat-container-quant");
        }
        if (ctx.inputForm != null && ctx.inputForm.startsWith("url:hf") && !shape.ggufFile() && noQuant) {
            reasons.add("repo-file-choice");
        } else if (shape.hubDefaultQuant() && noQuant) {
            reasons.add("hf-default-quant");
        }
        log.debug("Needs the engine's answer for {}", reasons);
        return reasons;
    }

    private static NameFacts facts(
            final NameShape shape,
            final QuantScan quant,
            final TokenReading reading,
            final NameWords words,
            final FamilyWords family) {
        final String format = shape.ggufFile() ? "gguf" : shape.format() != null ? shape.format() : reading.format;
        final boolean implied = MLX_COMMUNITY.equals(shape.quantizer()) && reading.format == null && !shape.ggufFile();
        return new NameFacts(
                shape.namespace(),
                format,
                quant.quant(),
                quant.extra(),
                quant.dynamic() || reading.dynamic,
                reading.qat,
                reading.unquantized,
                reading.date,
                reading.context,
                reading.spec,
                reading.release,
                words.shard(),
                shape.instance(),
                shape.unspecified(),
                reading.experts,
                reading.expertSize,
                QuantTypes.of(quant.quant()),
                reading.imatrix || "imatrix".equals(shape.sidecar()),
                reading.dwq,
                implied ? MLX : null,
                shape.sidecar(),
                reading.dateEight,
                reading.monthDay,
                family.finetune(),
                words.finetuner());
    }

    private static NameFacts emptyFacts(
            final @Nullable String format, final @Nullable String shard, final @Nullable String sidecar) {
        return new NameFacts(
                null,
                format,
                null,
                List.of(),
                false,
                false,
                false,
                null,
                null,
                List.of(),
                null,
                shard,
                null,
                null,
                null,
                null,
                null,
                "imatrix".equals(sidecar),
                false,
                null,
                sidecar,
                null,
                null,
                null,
                null);
    }

    private static ModelIdentity identityOnly(final ModelKind kind) {
        return new ModelIdentity(null, null, null, null, null, null, List.of(), kind, null, null, null, List.of());
    }

    private static NameOrigin origin(
            final PasteContext ctx,
            final @Nullable NameShape shape,
            final @Nullable String org,
            final @Nullable String file,
            final @Nullable String subfolder) {
        return new NameOrigin(
                ctx.inputForm,
                shape == null ? null : shape.quantizer(),
                org,
                shape == null ? ctx.repo : shape.repo(),
                file,
                subfolder,
                ctx.revision,
                ctx.routerVariants,
                ctx.serverAlias,
                ctx.alias);
    }
}
