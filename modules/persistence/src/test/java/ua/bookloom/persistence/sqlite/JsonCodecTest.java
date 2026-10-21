package ua.bookloom.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.ExportRecord;
import ua.bookloom.api.project.FootnotePolicy;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Register;
import ua.bookloom.api.project.RenderingOrigin;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;
import ua.bookloom.api.project.SnapshotRendering;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.SnapshotTmHit;
import ua.bookloom.api.project.TargetOrigin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.api.project.TmEntry;
import ua.bookloom.api.project.UnitPolicy;

class JsonCodecTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:15:30.123Z");
    private static final String UK = "Привіт, світе: «ґанок» — їжак";

    private final JsonCodec codec = new JsonCodec();

    static Stream<Object> storedRecords() {
        return Stream.of(
                segment(true),
                segment(false),
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
                new ExportRecord(
                        T0,
                        Path.of("/tmp/out/книга.epub"),
                        10,
                        1,
                        2,
                        3,
                        4,
                        5,
                        6,
                        List.of(Path.of("/tmp/out/glossary.csv"), Path.of("/tmp/out/log.txt"))),
                new ExportRecord(T0, Path.of("out/книга.epub"), 1, 0, 0, 0, 0, 0, 1, List.of(Path.of("side.csv"))));
    }

    static Stream<Object> storedDocuments() {
        return Stream.of(
                new GlossaryDocument(List.of(glossary()), List.of("milton")),
                new LexiconDocument(List.of(lexicon()), List.of("master")));
    }

    @ParameterizedTest
    @MethodSource("storedDocuments")
    void roundTrip_storedDocument_readsBackEqual(final Object stored) {
        final String json = codec.write(stored);

        assertThat(codec.read(json, stored.getClass())).isEqualTo(stored);
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
                List.of("prev " + UK, "prev2"),
                List.of(new SnapshotTerm("Nia", "Нія", TermType.CHARACTER, Gender.FEMALE, true, false, null)),
                List.of(new SnapshotTmHit(SnapshotTmHit.TmHitKind.FUZZY, "src", "tgt")),
                "summary " + UK,
                "style sheet",
                List.of(new SnapshotRendering("magic", "магія")),
                List.of("Nia"),
                full
                        ? List.of(
                                new PromptSection("rules", "Rules", PromptSection.Origin.SYSTEM, List.of("a", UK)),
                                new PromptSection("src", "Source", PromptSection.Origin.USER, List.of("b")))
                        : null);
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
                        new QaFinding("number", Severity.LOW, "n", "reviewer-edit")),
                SegmentPath.REPAIRED,
                2,
                true,
                full ? context(full) : null,
                full ? "rejected " + UK : null);
    }

    private static GlossaryEntry glossary() {
        return new GlossaryEntry(
                "g1",
                "p1",
                "Nia",
                "Нія",
                TermType.CHARACTER,
                Gender.FEMALE,
                true,
                TargetOrigin.PERSON,
                GenderOrigin.VERIFIED,
                true,
                List.of("Nia's", "Nias"),
                false);
    }

    private static LexiconEntry lexicon() {
        return new LexiconEntry(
                "p1",
                "magic",
                List.of(new LexiconEntry.Rendering("магія", 3), new LexiconEntry.Rendering("чари", 1)),
                "магія",
                "чари",
                new LexiconEntry.Learned("магія", 2, 4),
                RenderingOrigin.PERSON);
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
                List.of("custom-a", "custom-b"));
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
                1.6);
    }
}
