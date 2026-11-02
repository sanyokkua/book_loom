package ua.bookloom.llm.profile;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.ModelKind;

/** The words and shapes the name grammar knows; one place to grow when a model family brings a new one. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NameVocabulary {

    private static final String CODER = "coder";
    private static final String EMBEDDING = "embedding";
    private static final String INSTRUCT = "instruct";
    private static final String MODIFIED = "modified";
    private static final String SAFETENSORS = "safetensors";
    private static final String THINKING = "thinking";
    private static final String VISION = "vision";

    /** Words some repositories put in front of the model name to repeat its publisher. */
    static final Set<String> VENDOR_TOKENS =
            Set.of("qwen", "mistralai", "google", "meta", "nvidia", "ibm", "openai", "insait");

    /** Named lines inside a generation. */
    static final Set<String> LINES = Set.of(
            "nano",
            "small",
            "lightning",
            "mini",
            "medium",
            "large",
            "flash",
            "tiny",
            "micro",
            "omni",
            "cascade",
            "scout",
            "maverick");

    static final Map<String, String> FAMILY_ALIAS = Map.of("magistral-small", "magistral", "gemma-", "gemma");

    static final Map<String, String> VARIANTS = Map.ofEntries(
            Map.entry(INSTRUCT, INSTRUCT),
            Map.entry("it", INSTRUCT),
            Map.entry("chat", INSTRUCT),
            Map.entry("base", "base"),
            Map.entry("pt", "base"),
            Map.entry(THINKING, THINKING),
            Map.entry("reasoning", "reasoning"),
            Map.entry(CODER, CODER),
            Map.entry("coding", CODER),
            Map.entry("vl", VISION),
            Map.entry(VISION, VISION),
            Map.entry("omni", "omni"),
            Map.entry("abliterated", MODIFIED),
            Map.entry("uncensored", MODIFIED),
            Map.entry("heretic", MODIFIED),
            Map.entry("h", "hybrid"),
            Map.entry("embed", EMBEDDING),
            Map.entry(EMBEDDING, EMBEDDING),
            Map.entry("text", "text"),
            Map.entry("dev", "dev"));

    static final Map<String, String> FORMATS =
            Map.of("gguf", "gguf", "mlx", "mlx", SAFETENSORS, SAFETENSORS, "awq", "awq", "gptq", "gptq");

    static final Set<String> SPEC = Set.of("mtp", "dflash", "dflash2", "assistant", "draft");

    /** Words that carry no model fact: calibration and importance-matrix markers. */
    static final Set<String> IGNORED = Set.of("dwq", "i1", "imatrix", "kquant", "split");

    static final Pattern QUANT = Pattern.compile("(?:^|(?<=[-_.:@]))"
            + "((?:ud-)?(?:iq\\d(?:_[a-z]+)?|tq\\d_0|q\\d(?:_k)?(?:_[sml]|_xl|_[01]|_nl)?(?:_\\d_\\d)?|bf16|fp16|f16"
            + "|fp32|f32|fp8|nvfp4|mxfp4(?:_moe)?|mxfp8|int[48]|[2-8]bit|w4a16(?:-ct)?|w8a8))(?=$|[-_.:@])");
    static final Pattern SHARD = Pattern.compile("-?(\\d{5})-of-(\\d{5})");
    static final Pattern SIZE = Pattern.compile("(\\d+(?:\\.\\d+)?)b");
    static final Pattern EFFECTIVE = Pattern.compile("e(\\d+(?:\\.\\d+)?)b");
    static final Pattern ACTIVE = Pattern.compile("a(\\d+(?:\\.\\d+)?)b");
    static final Pattern EXPERTS = Pattern.compile("(\\d+)x(\\d+(?:\\.\\d+)?)b");
    static final Pattern MILLIONS = Pattern.compile("(\\d+)m");
    static final Pattern CONTEXT = Pattern.compile("\\d+[km]");
    static final Pattern EXPERT_COUNT = Pattern.compile("(\\d+)e");
    static final Pattern GROUP_SIZE = Pattern.compile("gs\\d+");
    static final Pattern DATE_EIGHT = Pattern.compile("20[2-3]\\d(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])");
    static final Pattern MONTH_DAY = Pattern.compile("(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])");
    static final Pattern DATE = Pattern.compile("2[4-7](?:0[1-9]|1[0-2])");
    static final Pattern RELEASE = Pattern.compile("v(\\d{1,9}(?:\\.\\d{1,9}){0,6})");
    static final Pattern VERSION_GLUED = Pattern.compile("([a-z]+?)(\\d+(?:\\.\\d+)?n?)");
    static final Pattern VERSION_ONLY = Pattern.compile("\\d+(?:\\.\\d+)?n?");
    static final Pattern INSTANCE = Pattern.compile("([^:]+):(\\d{1,2})");
    static final Pattern ORG_MODEL = Pattern.compile("([a-z][a-z0-9.-]*)_([a-z].*)");
    static final Pattern LATEST_TAIL = Pattern.compile("-latest$", Pattern.CASE_INSENSITIVE);

    /** Smallest "NNNm" read as millions of parameters; below it the letters are a context size. */
    static final int MILLIONS_FLOOR = 100;

    static final int MILLIONS_PER_BILLION = 1000;

    /** Publishers of quantised copies; a repository under one of them is a repack, not the model's own. */
    static final Set<String> QUANTIZERS = Set.of(
            "unsloth",
            "bartowski",
            "lmstudio-community",
            "mlx-community",
            "mradermacher",
            "ggml-org",
            "maziyarpanahi",
            "redhatai",
            "cyankiwi",
            "quanttrio",
            "nvidia-quant",
            "thebloke",
            "quantfactory",
            "mungert",
            "second-state",
            "gpustack",
            "nightmedia",
            "city96");

    /** Leading words of the companion files of a model (vision projector, speculative drafts). */
    static final Set<String> SIDECAR_LEAD = Set.of("mmproj", "mtp", "dflash", "eagle3");

    static final Set<String> AUXILIARY_EXTENSIONS = Set.of(
            "md", "txt", "json", "jinja", "py", "dat", "imatrix", "gguf_file", "png", "mp4", "log", "yml", "yaml");
    static final Set<String> AUXILIARY_NAMES = Set.of(
            "license",
            "license.md",
            "notice",
            "use_policy.md",
            "usage_policy",
            "params",
            "template",
            "system",
            "readme.md",
            ".gitattributes",
            ".src_sha");
    static final Set<String> WEIGHT_EXTENSIONS = Set.of(SAFETENSORS, "bin", "pt", "pth");
    static final Set<String> WEIGHT_STEMS = Set.of("model-", "model.", "pytorch_model", "consolidated");

    /** Variant words a router such as OpenRouter appends to an id after a colon. */
    static final Set<String> ROUTER_VARIANTS =
            Set.of("free", "batch", THINKING, "extended", "nitro", "floor", "exacto", "online");

    /** The families a distillation is trained from. */
    static final Set<String> DISTILL_BASES =
            Set.of("qwen", "llama", "gemma", "mistral", "phi", "deepseek", "glm", "granite");

    /** Model-tuner prefixes that precede the base model's name. */
    static final Set<String> FINETUNER_PREFIX = Set.of("huihui");

    /** Families that are not chat models, recognised by name alone. */
    static final Map<String, ModelKind> KIND_BY_FAMILY = Map.of(
            "ltx", ModelKind.VIDEO,
            "flux", ModelKind.IMAGE,
            "sdxl", ModelKind.IMAGE,
            "whisper", ModelKind.SPEECH,
            "bge", ModelKind.EMBEDDING);
}
