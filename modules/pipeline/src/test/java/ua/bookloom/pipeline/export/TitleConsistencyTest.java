package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;

/** The book's one translated title replaces every place the source title stands alone, at export. */
class TitleConsistencyTest {

    private static final String TITLE = "Amber Tide";
    private static final String BRIEF_TITLE = "Бурштиновий Приплив";
    private static final String MODEL_TITLE = "Янтарна Хвиля";

    private final List<Segment> auxiliary = new ArrayList<>();
    private final List<Segment> body = new ArrayList<>();
    private final List<SegmentRecord> records = new ArrayList<>();

    // IF each place were left to its own translation, THEN the book carries the title two ways (the evidence book did).
    @Test
    void apply_titleTranslatedElsewhereInOtherWords_everyWholeTextPlaceTakesTheTitleSegmentsTarget() {
        decided(aux("aux:title", SegmentKind.METADATA_TITLE, TITLE), BRIEF_TITLE);
        decided(aux("aux:ncx-doctitle", SegmentKind.NAV_LABEL, TITLE), MODEL_TITLE);
        decided(aux("aux:nav:1", SegmentKind.NAV_LABEL, "amber  tide"), MODEL_TITLE);
        decided(aux("aux:head-title:c1", SegmentKind.TITLE, TITLE), MODEL_TITLE);
        decided(body("c1:1", SegmentKind.HEADING, TITLE), MODEL_TITLE);

        final Map<String, String> written = written(Set.of());

        assertThat(written)
                .containsEntry("aux:ncx-doctitle", BRIEF_TITLE)
                .containsEntry("aux:nav:1", BRIEF_TITLE)
                .containsEntry("aux:head-title:c1", BRIEF_TITLE)
                .containsEntry("c1:1", BRIEF_TITLE);
    }

    // IF the person edited the title, THEN the edit, not the model's answer, is the one title.
    @Test
    void apply_titleEditedByThePerson_theEditIsWrittenEverywhere() {
        final Segment title = aux("aux:title", SegmentKind.METADATA_TITLE, TITLE);
        decided(title, MODEL_TITLE);
        records.set(0, records.get(0).withStatus(SegmentStatus.REVISED).withUserTarget(BRIEF_TITLE, BRIEF_TITLE));
        decided(body("c1:1", SegmentKind.HEADING, TITLE), MODEL_TITLE);

        assertThat(written(Set.of())).containsEntry("c1:1", BRIEF_TITLE).containsEntry("aux:title", BRIEF_TITLE);
    }

    // IF a longer heading that merely contains the title were replaced, THEN prose would lose its words.
    @Test
    void apply_headingHoldsMoreThanTheTitle_isLeftToItsOwnTranslation() {
        decided(aux("aux:title", SegmentKind.METADATA_TITLE, TITLE), BRIEF_TITLE);
        decided(body("c1:1", SegmentKind.HEADING, "Amber Tide: Part One"), "Бурштиновий приплив: частина перша");
        decided(body("c1:2", SegmentKind.PARAGRAPH, TITLE), "Приплив");

        assertThat(written(Set.of()))
                .containsEntry("c1:1", "Бурштиновий приплив: частина перша")
                .containsEntry("c1:2", "Приплив");
    }

    // IF the brief keeps navigation as source, THEN a nav label stays the source title.
    @Test
    void apply_navigationKeptAsSource_navLabelIsNotReplaced() {
        decided(aux("aux:title", SegmentKind.METADATA_TITLE, TITLE), BRIEF_TITLE);
        decided(aux("aux:nav:1", SegmentKind.NAV_LABEL, TITLE), MODEL_TITLE);

        assertThat(written(Set.of(SegmentKind.NAV_LABEL))).doesNotContainKey("aux:nav:1");
    }

    // IF the title itself was never translated, THEN nothing is invented.
    @Test
    void apply_titlePending_otherPlacesKeepTheirOwnTranslation() {
        final Segment title = aux("aux:title", SegmentKind.METADATA_TITLE, TITLE);
        records.add(record(title, SegmentStatus.PENDING, null));
        decided(body("c1:1", SegmentKind.HEADING, TITLE), MODEL_TITLE);

        assertThat(written(Set.of())).containsEntry("c1:1", MODEL_TITLE);
    }

    private Segment aux(final String id, final SegmentKind kind, final String text) {
        final Segment segment = segment(id, Unit.AUXILIARY_ID, kind, text);
        auxiliary.add(segment);
        return segment;
    }

    private Segment body(final String id, final SegmentKind kind, final String text) {
        final Segment segment = segment(id, "c1", kind, text);
        body.add(segment);
        return segment;
    }

    private void decided(final Segment segment, final String target) {
        records.add(record(segment, SegmentStatus.ACCEPTED, target));
    }

    private Map<String, String> written(final Set<SegmentKind> kept) {
        final Document opened = new Document(
                "d1",
                BookFormat.TXT,
                "en",
                null,
                null,
                null,
                "hash",
                Map.of(),
                List.of(unit("c1", body), unit(Unit.AUXILIARY_ID, auxiliary)));
        final EffectiveTargets targets =
                EffectiveTargets.apply(opened, records, kept, (segment, masked) -> Result.ok(masked), "en", "uk");
        final Map<String, String> byId = new java.util.LinkedHashMap<>();
        targets.document().units().stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> segment.targetInner() != null)
                .forEach(segment -> byId.put(segment.id(), segment.targetInner()));
        return byId;
    }

    private static Unit unit(final String id, final List<Segment> segments) {
        return new Unit(id, 1, id + ".txt", "text/plain", new SkeletonHandle(id), segments);
    }

    private static Segment segment(final String id, final String unit, final SegmentKind kind, final String text) {
        return new Segment(
                id,
                unit,
                1,
                kind,
                text,
                text,
                Map.of(),
                "h" + id,
                null,
                null,
                new ByteSpanAnchor(0, text.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    private static SegmentRecord record(
            final Segment segment, final SegmentStatus status, @Nullable final String target) {
        return new SegmentRecord(
                "p1",
                segment.id(),
                segment.unit(),
                1,
                segment.kind(),
                status,
                target,
                target,
                null,
                null,
                0.9,
                null,
                List.of(),
                SegmentPath.DRAFT,
                0,
                false,
                null);
    }
}
