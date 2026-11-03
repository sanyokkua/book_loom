package ua.bookloom.llm.profile;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ContextPolicy;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelIdentity;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ReasoningControl;
import ua.bookloom.api.llm.SamplingOverride;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.ThinkingOverride;
import ua.bookloom.llm.engine.EngineTraits;

/**
 * Resolves one model's handling from four sources, the later winning over the earlier (design D20): the stored engine's
 * traits decide whether any thinking control can be sent at all; the model's name, read with the server's metadata,
 * picks a built-in row; the server's facts about the model win over the row; the person's overrides on Settings ›
 * Models win over everything. A model no row knows gets {@link ModelProfile#defaults()} and the verdict
 * {@value ModelReport#UNKNOWN_MODEL}, with its own server facts still applied. Pure work over what it is handed: it
 * reads nothing from a server and stores nothing.
 */
@Slf4j
@Singleton
public final class ModelProfiles {

    private static final String THINKING = "thinking";
    private static final String BOOLEAN_OFF = "false";
    private static final Set<String> OFF_WORDS = Set.of(BOOLEAN_OFF, "off", "none");
    private static final Set<String> BOOLEAN_WORDS = Set.of("true", BOOLEAN_OFF, "on", "off");
    private static final Pattern BILLIONS = Pattern.compile("(?<![\\d.])(\\d{1,9}(?:\\.\\d{1,9})?)\\s{0,3}[Bb]\\b");
    private static final ModelReport.Match DEFAULT_MATCH = ModelReport.Match.DEFAULT;

    // The default profile's request half as a row, so a model with no row goes through the same layering.
    private static final ProfileRow DEFAULT_ROW = defaultRow();

    private final ProfileRows rows;
    private final ModelIdentityParser parser = new ModelIdentityParser();
    private final MetadataWins metadataWins = new MetadataWins();

    /** Resolves against the given built-in rows. */
    @Inject
    public ModelProfiles(final ProfileRows rows) {
        this.rows = Objects.requireNonNull(rows, "rows");
    }

    /**
     * Resolves a model's profile and the half of its handling a person reads.
     *
     * @param engine the provider's effective engine; non-null
     * @param modelId the model id as the provider lists it; non-null
     * @param facts what the server said about the model this session; non-null, unknown when nothing was read
     * @param settings the model's stored settings, which hold the person's overrides and check verdicts; non-null
     * @return the resolution; never null
     */
    public ResolvedProfile resolve(
            final ServerEngine engine, final String modelId, final ModelFacts facts, final ModelSettings settings) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(settings, "settings");
        final ModelIdentity identity = identity(modelId, facts);
        final Optional<ProfileRows.Picked> picked = rows.pick(identity, facts);
        final ProfileRow row = picked.map(ProfileRows.Picked::row).orElse(DEFAULT_ROW);
        final ReasoningControl reasoning =
                engineGate(engine, settings.probeVerdicts(), withFacts(row.reasoning(), facts));
        final ModelReport.Match match = isOverridden(settings)
                ? ModelReport.Match.OVERRIDE
                : picked.map(ProfileRows.Picked::match).orElse(DEFAULT_MATCH);
        final ResolvedProfile resolved = new ResolvedProfile(
                profile(row, reasoning, facts, settings),
                identity,
                match,
                row.verdict(),
                List.of(),
                quant(identity, facts));
        logResolved(engine, modelId, picked.isPresent() ? row.name() : "none", resolved);
        return resolved;
    }

    private static void logResolved(
            final ServerEngine engine, final String modelId, final String rowName, final ResolvedProfile resolved) {
        final ModelProfile profile = resolved.profile();
        log.debug(
                "Profile resolved model={} engine={} row={} match={} reasoning={} thinking={} sampling={} batch={}",
                modelId,
                engine,
                rowName,
                resolved.match(),
                profile.reasoning().kind(),
                profile.thinking(),
                profile.sampling().mode(),
                profile.batchSize());
    }

    private static ProfileRow defaultRow() {
        final ModelProfile d = ModelProfile.defaults();
        return new ProfileRow(
                "default",
                d.reasoning(),
                d.markupFilters(),
                d.systemRole(),
                d.quirks(),
                d.context().hardCap(),
                d.context().outputMax(),
                d.templateOverhead(),
                d.batchSize(),
                d.tier(),
                ModelReport.UNKNOWN_MODEL);
    }

    private ModelIdentity identity(final String modelId, final ModelFacts facts) {
        final ModelIdentity named = parser.parse(modelId).identity();
        return metadataWins.apply(named, new MetadataWins.Facts(billions(facts.parameterSize()), null));
    }

    private static ModelProfile profile(
            final ProfileRow row,
            final ReasoningControl reasoning,
            final ModelFacts facts,
            final ModelSettings settings) {
        return new ModelProfile(
                reasoning,
                row.markupFilters(),
                row.systemRole(),
                row.quirks(),
                settings.samplingOverride(),
                settings.thinkingOverride(),
                new ContextPolicy(row.hardCap(), row.outputMax(), facts.contextLength(), facts.loadedContext()),
                row.templateOverhead(),
                row.batchSize(),
                row.tier());
    }

    private static boolean isOverridden(final ModelSettings settings) {
        return settings.thinkingOverride() != ThinkingOverride.RECOMMENDED
                || settings.samplingOverride().mode() == SamplingOverride.Mode.CUSTOM;
    }

    // The server knows how its copy of the model is templated: a listed capability set without "thinking" means no
    // switch is read, and with one the server's own values replace the row's.
    static ReasoningControl withFacts(final ReasoningControl row, final ModelFacts facts) {
        if (facts.reasoningMandatory() && facts.thinkingOptions().isEmpty()) {
            log.debug("Reasoning is mandatory and offers no level: the model thinks on every call row={}", row.kind());
            return new ReasoningControl(ReasoningControl.Kind.UNSWITCHABLE, List.of(), null, row.serverDefault());
        }
        final boolean listsCapabilities = !facts.capabilities().isEmpty();
        final boolean thinks = facts.capabilities().contains(THINKING)
                || !facts.thinkingOptions().isEmpty();
        if (listsCapabilities && !thinks) {
            return withoutThinkingCapability(row, facts);
        }
        if (!thinks) {
            log.debug("Reasoning from the row: the server said nothing about thinking kind={}", row.kind());
            return row;
        }
        final List<String> offered = facts.thinkingOptions().isEmpty() ? row.offered() : facts.thinkingOptions();
        final String serverDefault = facts.thinkingDefault() == null ? row.serverDefault() : facts.thinkingDefault();
        final ReasoningControl.Kind kind =
                switch (row.kind()) {
                    case NONE, UNKNOWN -> kindOf(offered);
                    case BOOLEAN, LEVELS, ALWAYS, UNSWITCHABLE -> row.kind();
                };
        final String offValue = row.offValue() != null && row.kind() == kind ? row.offValue() : offValueOf(offered);
        log.debug("Reasoning from the server's facts row={} kind={} offered={}", row.kind(), kind, offered);
        return new ReasoningControl(kind, offered, offValue, serverDefault);
    }

    // A switch the row expects but the server's template does not read: a model that thinks by default then thinks on
    // every call and cannot be stopped, else it is sent no control at all.
    private static ReasoningControl withoutThinkingCapability(final ReasoningControl row, final ModelFacts facts) {
        if (row.kind() != ReasoningControl.Kind.BOOLEAN && row.kind() != ReasoningControl.Kind.LEVELS) {
            return row;
        }
        final boolean unswitchable = facts.renderer() == null && isOn(row.serverDefault());
        log.debug(
                "Server lists no thinking capability row={} renderer={} rowDefault={} unswitchable={}",
                row.kind(),
                facts.renderer(),
                row.serverDefault(),
                unswitchable);
        return unswitchable
                ? new ReasoningControl(ReasoningControl.Kind.UNSWITCHABLE, List.of(), null, row.serverDefault())
                : ReasoningControl.NONE;
    }

    // A server whose off switch is only known once a check proved it gets no control until a verdict says honoured.
    private static ReasoningControl engineGate(
            final ServerEngine engine, final ProbeVerdicts verdicts, final ReasoningControl reasoning) {
        final boolean switchable =
                reasoning.kind() == ReasoningControl.Kind.BOOLEAN || reasoning.kind() == ReasoningControl.Kind.LEVELS;
        if (!switchable || EngineTraits.of(engine).offSwitch() != EngineTraits.OffSwitch.PROBED) {
            return reasoning;
        }
        final ProbeVerdicts.ThinkingVerdict checked = verdicts.thinkingSwitch();
        final boolean honoured = checked != null && checked.verdict().outcome() == ProbeVerdicts.Outcome.HONOURED;
        log.debug("Engine gate engine={} thinkingCheckHonoured={}", engine, honoured);
        return honoured
                ? reasoning
                : new ReasoningControl(
                        ReasoningControl.Kind.UNKNOWN,
                        reasoning.offered(),
                        reasoning.offValue(),
                        reasoning.serverDefault());
    }

    private static ReasoningControl.Kind kindOf(final List<String> offered) {
        return offered.isEmpty() || BOOLEAN_WORDS.containsAll(lower(offered))
                ? ReasoningControl.Kind.BOOLEAN
                : ReasoningControl.Kind.LEVELS;
    }

    private static String offValueOf(final List<String> offered) {
        return lower(offered).stream()
                .filter(OFF_WORDS::contains)
                .findFirst()
                .orElseGet(() -> offered.isEmpty() || BOOLEAN_WORDS.containsAll(lower(offered))
                        ? BOOLEAN_OFF
                        : lower(offered).getFirst());
    }

    private static boolean isOn(final @Nullable String serverDefault) {
        return serverDefault != null && !OFF_WORDS.contains(serverDefault.toLowerCase(Locale.ROOT));
    }

    private static List<String> lower(final List<String> values) {
        return values.stream().map(v -> v.toLowerCase(Locale.ROOT)).toList();
    }

    private static @Nullable String quant(final ModelIdentity identity, final ModelFacts facts) {
        final String found = facts.quant() != null ? facts.quant() : identity.quantLabel();
        return found == null ? null : found.toLowerCase(Locale.ROOT);
    }

    private static @Nullable Double billions(final @Nullable String parameterSize) {
        if (parameterSize == null) {
            return null;
        }
        final Matcher matcher = BILLIONS.matcher(parameterSize);
        return matcher.find() ? Double.valueOf(matcher.group(1)) : null;
    }
}
