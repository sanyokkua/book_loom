package ua.bookloom.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.MeasuredSpeed;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.api.llm.SamplingOverride;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.api.llm.ThinkingOverride;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Address;
import ua.bookloom.api.project.AddressForm;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.BriefField;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.ExportRecord;
import ua.bookloom.api.project.FootnotePolicy;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.MeaningChoice;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.PromptFrame;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.QuoteStyle;
import ua.bookloom.api.project.Register;
import ua.bookloom.api.project.RenderingOrigin;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;
import ua.bookloom.api.project.TargetState;
import ua.bookloom.api.project.TermType;
import ua.bookloom.api.project.TmEntry;
import ua.bookloom.api.project.UnitPolicy;

class JsonCodecTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:15:30.123Z");
    private static final String UK = "Привіт, світе: «ґанок» — їжак";

    private final JsonCodec codec = new JsonCodec();

    private static ExportRecord exportWithSideFiles() {
        final List<Path> sideFiles = List.of(Path.of("/tmp/out/glossary.csv"), Path.of("/tmp/out/log.txt"));
        return new ExportRecord(T0, Path.of("/tmp/out/книга.epub"), 10, 1, 2, 3, 4, 5, 6, sideFiles);
    }

    static Stream<Object> storedRecords() {
        return Stream.of(
                new GlossaryDocument(List.of(glossary()), List.of("milton")),
                new LexiconDocument(List.of(lexicon()), List.of("master")),
                segment(true),
                segment(false),
                frame(),
                glossary(),
                lexicon(),
                new RollingSummary("p1", "u1", "Source " + UK, "Target " + UK, 3, "seg-9", 120),
                new RollingSummary("p1", null, "s", "t", 0, null, 0),
                new Deferral("d1", "p1", "s1", DeferralReason.GENDER_UNKNOWN, "Nia", "old", "new " + UK, "⟦g1⟧"),
                new Deferral("d2", "p1", "s2", DeferralReason.TERM, null, null, null, null),
                running(),
                finished(),
                new TmEntry("t1", "p1", "hash", "ctx", "src " + UK, "tgt " + UK),
                brief(),
                provider(),
                modelSettings(),
                exportWithSideFiles(),
                new ExportRecord(T0, Path.of("out/книга.epub"), 1, 0, 0, 0, 0, 0, 1, List.of(Path.of("side.csv"))));
    }

    @Test
    void write_relativePath_isStoredAsWritten() {
        final ExportRecord record = new ExportRecord(T0, Path.of("out/book.epub"), 1, 0, 0, 0, 0, 0, 1, List.of());

        assertThat(codec.write(record)).contains("\"destination\":\"out/book.epub\"");
    }

    @ParameterizedTest
    @MethodSource("storedRecords")
    void roundTrip_storedRecord_readsBackEqual(final Object stored) {
        final String json = codec.write(stored);

        assertThat(codec.read(json, stored.getClass())).isEqualTo(stored);
    }

    @Test
    void write_providerWithEnvReference_keepsReferenceText() {
        final ProviderConfig config = provider();

        assertThat(codec.write(config)).contains("${env:LMS_KEY}").doesNotContain("secret-value");
    }

    @Test
    void write_instant_isIsoString() {
        assertThat(codec.write(running())).contains("2026-03-01T10:15:30.123Z");
    }

    private static ContextSnapshot context(final boolean full) {
        return new ContextSnapshot(
                "9f2c" + "0".repeat(60),
                List.of(new PromptSection("previousPairs", "[Previous]", PromptSection.Origin.USER, List.of("a", UK))),
                full ? List.of("ch1:0") : List.of(),
                full);
    }

    private static PromptFrame frame() {
        return new PromptFrame(
                "9f2c" + "0".repeat(60),
                List.of(
                        new PromptSection("role", "", PromptSection.Origin.SYSTEM, List.of("Translate " + UK)),
                        new PromptSection("styleSheet", "[Style]", PromptSection.Origin.SYSTEM, List.of("b"))));
    }

    // The snapshot keeps the frame's key, the call's own sections, the summary keys and the retry mark, and nothing
    // of the frame itself.
    @Test
    void roundTrip_contextSnapshot() {
        final ContextSnapshot snapshot = context(true);

        final String json = codec.write(snapshot);

        assertThat(codec.read(json, ContextSnapshot.class)).isEqualTo(snapshot);
        assertThat(json).contains("\"frameHash\":\"9f2c", "\"retry\":true").doesNotContain("Translate");
    }

    private static SegmentRecord segment(final boolean full) {
        return new SegmentRecord(
                "p1",
                "s1",
                "u1",
                7,
                SegmentKind.PARAGRAPH,
                SegmentStatus.FLAGGED,
                full ? "machine " + UK : null,
                full ? "masked ⟦g1⟧" : null,
                full ? "user " + UK : null,
                full ? "masked user" : null,
                0.75,
                full ? 8.5 : null,
                List.of(
                        new QaFinding("name-swap", Severity.HIGH, "note " + UK, "check"),
                        new QaFinding("number", Severity.LOW, "n", "reviewer-edit"),
                        new QaFinding("language", Severity.MEDIUM, "residue " + UK, "function-word-residue"),
                        new QaFinding("omission", Severity.MEDIUM, "ends " + UK, "terminal-mark")),
                SegmentPath.REPAIRED,
                true,
                full ? context(full) : null,
                full ? "rejected " + UK : null);
    }

    private static GlossaryEntry glossary() {
        return new GlossaryEntry(
                "g1",
                "p1",
                "Nia",
                List.of("Nia's", "Nias"),
                true,
                "Нія",
                TargetState.LOCKED,
                Origin.USER,
                TermType.CHARACTER,
                null,
                Origin.RULE,
                Gender.FEMALE,
                Origin.BOOK,
                true,
                false,
                "a note " + UK,
                MeaningChoice.TRANSCRIBE_GLOSS,
                null,
                2);
    }

    private static LexiconEntry lexicon() {
        return new LexiconEntry(
                "p1",
                "magic",
                List.of(new LexiconEntry.Rendering("магія", 3), new LexiconEntry.Rendering("чари", 1)),
                "магія",
                "чари",
                new LexiconEntry.Learned("магія", 2, 4),
                RenderingOrigin.PERSON,
                List.of("magics"));
    }

    private static RunRecord running() {
        return new RunRecord("r1", "p1", T0, null, JobState.RUNNING, 3, 1, new ModelSelection("prov", "model-x"));
    }

    private static RunRecord finished() {
        return new RunRecord("r2", "p1", T0, T0.plusSeconds(60), JobState.COMPLETED, 5, 0, null);
    }

    private static BookBrief brief() {
        return new BookBrief(
                "en",
                "uk",
                "fantasy",
                Register.CASUAL,
                "1990s",
                "teens",
                NamePolicy.TRANSLITERATE,
                ForeignPassagePolicy.TRANSLATE_WITH_NOTE,
                FootnotePolicy.KEEP,
                UnitPolicy.METRIC,
                70,
                new AlsoTranslate(true, false, true, false),
                QualityDial.MAX,
                new Narrator(
                        NarratorPerson.FIRST, Gender.FEMALE, Narrator.GenderOrigin.VERIFIED, List.of("«я пішла»")));
    }

    private static ProviderConfig provider() {
        return new ProviderConfig(
                "prov",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("http://127.0.0.1:1234/v1"),
                Duration.ofSeconds(7),
                Duration.ofMinutes(4),
                "LM Studio " + UK,
                ProviderAuth.BEARER,
                "X-Api-Key",
                "LMS_KEY",
                "/models",
                "/chat/completions",
                Map.of("Authorization", "Bearer ${env:LMS_KEY}"),
                List.of("custom-a", "custom-b"),
                ServerEngine.LM_STUDIO,
                ServerEngine.LLAMA_CPP,
                "0.3.9",
                true);
    }

    // The person's overrides and a refusal scoped to one call kind come back as written.
    @Test
    void roundTrip_overridesAndScopedRefusal_readsBackEqual() {
        final ModelSettings stored = modelSettings()
                .withOverrides(ThinkingOverride.ALLOW_PROOFREAD_AND_NAMES, SamplingOverride.custom(0.4, 20, null, 0.05))
                .withRefused(List.of(
                        new RefusedSetting("temperature", LocalDate.of(2026, 2, 3), "omitted it"),
                        new RefusedSetting("response_format", LocalDate.of(2026, 2, 4), "prompt only", "summary")));

        final String json = codec.write(stored);

        assertThat(json)
                .contains("\"thinkingOverride\":\"ALLOW_PROOFREAD_AND_NAMES\"")
                .contains("\"mode\":\"CUSTOM\"")
                .contains("\"scope\":\"summary\"");
        assertThat(codec.read(json, ModelSettings.class)).isEqualTo(stored);
    }

    // The Brief's policy keys, a lemma entry and the seconds per call kind come back as written.
    @Test
    void roundTrip_lemmaEntryBriefKeysAndSecondsPerCall() {
        final BookBrief declared = brief().withApostrophe("\u2019")
                .withQuoteStyle(new QuoteStyle("\u00ab", "\u00bb", "\u201e", "\u201c"))
                .withDefaultAddress(new Address(AddressForm.INFORMAL, AddressForm.FORMAL))
                .withConventions(Map.of("hardG", "\u0433"))
                .withLikely(Set.of(BriefField.GENRE));
        final ModelSettings measured = modelSettings().withMeasuredSpeed(CallKind.DRAFT, 3.0, MeasuredFrom.TEST);

        assertThat(codec.read(codec.write(glossary()), GlossaryEntry.class)).isEqualTo(glossary());
        assertThat(codec.read(codec.write(declared), BookBrief.class)).isEqualTo(declared);
        final ModelSettings back = codec.read(codec.write(measured), ModelSettings.class);
        assertThat(back.speedOf(CallKind.DRAFT)).hasValue(new MeasuredSpeed(3.0, MeasuredFrom.TEST));
        assertThat(back).isEqualTo(measured);
    }

    private static ModelSettings modelSettings() {
        return new ModelSettings(
                "prov",
                "model-x",
                TemperatureChoice.VALUE,
                0.7,
                16384,
                4096,
                OutputLengthField.MAX_COMPLETION_TOKENS,
                List.of(new RefusedSetting("temperature", LocalDate.of(2026, 2, 3), "omitted it")),
                Map.of(CallKind.DRAFT, new MeasuredSpeed(1.6, MeasuredFrom.TEST)),
                new ProbeVerdicts(
                        new ProbeVerdicts.Verdict(
                                ProbeVerdicts.Outcome.HONOURED, "test", Instant.parse("2026-10-29T10:00:00Z")),
                        new ProbeVerdicts.ThinkingVerdict(
                                "think",
                                new ProbeVerdicts.Verdict(
                                        ProbeVerdicts.Outcome.IGNORED, "test", Instant.parse("2026-10-29T10:00:01Z"))),
                        null,
                        new ProbeVerdicts.Verdict(
                                ProbeVerdicts.Outcome.REFUSED, "test", Instant.parse("2026-10-29T10:00:02Z"))),
                ThinkingOverride.RECOMMENDED,
                SamplingOverride.profileDefault());
    }
}
