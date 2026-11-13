package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.index.IndexTestBooks;

class WindowAssemblerTest {

    private static final String FLAGGED_TARGET = "Він сказав, что прийде.";

    private static final List<Unit> BOOK = List.of(
            IndexTestBooks.unit("titlepage", UnitRole.FRONT_MATTER, SegmentKind.TITLE, "Тихая река"),
            IndexTestBooks.unit(
                    "ch07",
                    UnitRole.BODY,
                    "Утром туман лежал над рекой.",
                    "Лодки стояли у берега.",
                    "Ирвин спустился к воде.",
                    "Он долго смотрел на мост."),
            IndexTestBooks.unit("aux", UnitRole.BODY, SegmentKind.METADATA_TITLE, "Тихая река"),
            IndexTestBooks.unit(
                    "ch08", UnitRole.BODY, "Наступил вечер.", "Глафира зажгла лампу.", "Ветер стих к ночи."),
            IndexTestBooks.unit("back", UnitRole.BACK_MATTER, "Спасибо читателям."));

    private static Window assemble(
            final List<Unit> units, final ModelProfile profile, final AcceptedTargets accepted, final String... ids) {
        final Result<Window> window = WindowAssembler.assemble(units, focus(units, ids), profile, accepted);
        assertThat(window.isOk()).as("window of %s", List.of(ids)).isTrue();
        return Objects.requireNonNull(window.data());
    }

    private static Focus focus(final List<Unit> units, final String... ids) {
        final List<String> wanted = List.of(ids);
        final List<FocusItem> items = units.stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> wanted.contains(segment.id()))
                .map(segment -> new FocusItem(segment.id(), segment.masked()))
                .toList();
        return new Focus(items, Map.of());
    }

    private static AcceptedTargets acceptedFrom(final Map<String, SegmentRecord> records) {
        return id -> Result.ok(Optional.ofNullable(records.get(id)).flatMap(AcceptedTargets::of));
    }

    private static SegmentRecord record(final String id, final SegmentStatus status, final String target) {
        return new SegmentRecord(
                "p1",
                id,
                id.substring(0, id.indexOf(':')),
                0,
                SegmentKind.PARAGRAPH,
                status,
                target,
                target,
                null,
                null,
                1.0,
                null,
                List.of(),
                SegmentPath.DRAFT,
                false,
                null);
    }

    private static ModelProfile tier(final ModelProfile.Tier tier) {
        final ModelProfile base = ModelProfile.defaults();
        return new ModelProfile(
                base.reasoning(),
                base.markupFilters(),
                base.systemRole(),
                base.quirks(),
                base.sampling(),
                base.thinking(),
                base.context(),
                base.templateOverhead(),
                base.batchSize(),
                tier);
    }

    @Test
    void window_firstOfChapter_seesEndOfPreviousChapter() {
        final AcceptedTargets accepted = acceptedFrom(Map.of(
                "ch07:1", record("ch07:1", SegmentStatus.ACCEPTED, "Човни стояли біля берега."),
                "ch07:2", record("ch07:2", SegmentStatus.ACCEPTED, "Ірвін спустився до води."),
                "ch07:3", record("ch07:3", SegmentStatus.ACCEPTED, "Він довго дивився на міст.")));

        final Window window = assemble(BOOK, tier(ModelProfile.Tier.SMALL), accepted, "ch08:0", "ch08:1");

        assertThat(window.previous())
                .containsExactly(
                        new WindowPair("ch07:2", "Ирвин спустился к воде.", "Ірвін спустився до води."),
                        new WindowPair("ch07:3", "Он долго смотрел на мост.", "Він довго дивився на міст."));
        assertThat(window.focus().segmentIds()).containsExactly("ch08:0", "ch08:1");
        assertThat(window.nextSource()).isEqualTo("Ветер стих к ночи.");
    }

    @Test
    void window_largeTier_threePrevious() {
        final Window window =
                assemble(BOOK, tier(ModelProfile.Tier.LARGE), id -> Result.ok(Optional.empty()), "ch08:0");

        assertThat(window.previous()).extracting(WindowPair::segmentId).containsExactly("ch07:1", "ch07:2", "ch07:3");
    }

    @Test
    void window_frontMatterNeverPrevious() {
        final AcceptedTargets accepted =
                acceptedFrom(Map.of("titlepage:0", record("titlepage:0", SegmentStatus.ACCEPTED, "Тиха річка")));

        final Window window = assemble(BOOK, ModelProfile.defaults(), accepted, "ch07:0");

        assertThat(window.previous()).isEmpty();
        assertThat(window.nextSource()).isEqualTo("Лодки стояли у берега.");
    }

    @Test
    void window_flaggedNeighbour_sourceOnly() {
        final List<Unit> book = List.of(
                IndexTestBooks.unit("ch03", UnitRole.BODY, "Он сказал, что придёт.", "Дверь осталась открытой."));
        final AcceptedTargets accepted =
                acceptedFrom(Map.of("ch03:0", record("ch03:0", SegmentStatus.FLAGGED, FLAGGED_TARGET)));

        final Window window = assemble(book, ModelProfile.defaults(), accepted, "ch03:1");
        final RunFrame frame = FrameFixtures.frame(FrameFixtures.book(FrameFixtures.NARRATION));
        final PromptPlan plan = Objects.requireNonNull(FrameFixtures.BUILDER
                .plan(
                        CallKind.DRAFT,
                        window,
                        FrameFixtures.emptyMap(frame.brief()),
                        frame,
                        ModelProfile.defaults(),
                        List.of())
                .data());

        assertThat(window.previous()).containsExactly(new WindowPair("ch03:0", "Он сказал, что придёт.", null));
        assertThat(plan.blocks())
                .filteredOn(block -> block.slot().equals(BlockSlots.WINDOW))
                .singleElement()
                .satisfies(block -> assertThat(block.text())
                        .contains("Source: Он сказал, что придёт.")
                        .doesNotContain(FLAGGED_TARGET));
        assertThat(plan.userText()).doesNotContain(FLAGGED_TARGET);
    }

    @Test
    void window_lastBodySegment_noNext() {
        final Window window = assemble(BOOK, ModelProfile.defaults(), id -> Result.ok(Optional.empty()), "ch08:2");

        assertThat(window.nextSource()).isNull();
        assertThat(window.previous()).extracting(WindowPair::maskedTarget).containsExactly(null, null);
    }

    @Test
    void window_neighbourReadFails_returnsTheError() {
        final Result<Window> window = WindowAssembler.assemble(
                BOOK,
                focus(BOOK, "ch08:0"),
                ModelProfile.defaults(),
                id -> Result.err(AppError.of(ErrorCode.internal, "Read failed", "The segment could not be read.")));

        assertThat(window.isErr()).isTrue();
    }
}
