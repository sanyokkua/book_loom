package ua.bookloom.llm.profile;

import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelInput;
import ua.bookloom.api.llm.ModelInputForm;

/**
 * Finds the model id in what the person pasted and hands it on as a {@link ModelInput}. The reading itself is done by
 * {@link PasteReader}, the same reader the name parser uses, so a link or command is understood one way only. The text
 * is only read as text: a link is never opened and a command never run.
 */
@Slf4j
public final class ModelInputNormalizer {

    private static final String HUB_PREFIX = "hf.co/";
    private static final String GGUF_EXTENSION = ".gguf";

    private final PasteReader reader = new PasteReader();
    private final QuantScanner quantScanner = new QuantScanner();

    /**
     * Reads one paste.
     *
     * @param pasted what the person typed or pasted; may be padded or wrapped in quotes
     * @return the id to use and the repository, file and quant the paste named; never null
     */
    public ModelInput normalise(final String pasted) {
        Objects.requireNonNull(pasted, "pasted");
        final PasteContext ctx = reader.read(pasted);
        final String raw = pasted.strip();
        final String form = ctx.inputForm == null ? "" : ctx.inputForm;
        final String text = ctx.text;
        final ModelInput input;
        if (form.startsWith("url:hf")) {
            input = fromHubLink(raw, ctx);
        } else if (form.startsWith("cmd:")) {
            input = fromId(
                    raw,
                    ModelInputForm.COMMAND,
                    text.startsWith(HUB_PREFIX) && form.endsWith("-hf") ? text.substring(HUB_PREFIX.length()) : text,
                    ctx);
        } else if (text.toLowerCase(Locale.ROOT).endsWith(GGUF_EXTENSION)) {
            input = fromFile(raw, text);
        } else {
            final boolean ollama = form.startsWith("url:ollama");
            input = fromId(raw, ollama ? ModelInputForm.OLLAMA_URL : ModelInputForm.MODEL_ID, text, ctx);
        }
        log.debug("Normalised form={} id={} repo={}", input.form(), input.modelId(), input.repo());
        return input;
    }

    private ModelInput fromHubLink(final String raw, final PasteContext ctx) {
        final String repo = ctx.repo == null ? ctx.text : ctx.repo;
        final String file =
                ctx.repo != null && ctx.text.startsWith(repo + "/") ? ctx.text.substring(repo.length() + 1) : null;
        final QuantScan quant = quantScanner.scan(file == null ? "" : stripExtension(file));
        return new ModelInput(
                raw, ModelInputForm.HUGGING_FACE_URL, repo, ctx.repo, file, quant.quant(), quant.dynamic());
    }

    private ModelInput fromFile(final String raw, final String path) {
        final String file = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
        final QuantScan quant = quantScanner.scan(stripExtension(file));
        return new ModelInput(raw, ModelInputForm.FILE, file, null, file, quant.quant(), quant.dynamic());
    }

    private ModelInput fromId(final String raw, final ModelInputForm form, final String id, final PasteContext ctx) {
        final QuantScan quant = quantScanner.scan(id);
        return new ModelInput(raw, form, id, repoOf(id, ctx), null, quant.quant(), quant.dynamic());
    }

    private static @Nullable String repoOf(final String id, final PasteContext ctx) {
        if (ctx.inputForm != null && ctx.inputForm.endsWith("llama-server -hf")) {
            return id.split(":", 2)[0];
        }
        return id.startsWith(HUB_PREFIX) ? id.substring(HUB_PREFIX.length()).split(":", 2)[0] : null;
    }

    private static String stripExtension(final String file) {
        return file.toLowerCase(Locale.ROOT).endsWith(GGUF_EXTENSION)
                ? file.substring(0, file.length() - GGUF_EXTENSION.length())
                : file;
    }
}
