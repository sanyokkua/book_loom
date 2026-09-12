package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.document.fixture.Fb2Fixtures;

/**
 * A restored tree-format segment carrying a character XML 1.0 cannot represent is refused at the segment, not at
 * the book.
 *
 * <p>Before this ran in {@code unmask}, the two tree formats disagreed and both answers were wrong. Measured on a
 * target carrying a U+0008 backspace: FB2's fragment parse threw, and it threw from {@code write}, aborting before
 * a single byte of the book was written — so one bad segment made the whole export impossible, and the error named
 * no segment. EPUB accepted the same content and dropped the character silently at serialization, so the book
 * exported with data quietly missing.
 *
 * <p>Two boundaries matter as much as the rejection itself, and each has its own test below. C1 controls are
 * <em>legal</em> in XML 1.0 — only XML 1.1 restricts them, and no format here emits XML 1.1 — so refusing them
 * would reject ordinary text. And the buffer-shaped formats have no XML to be illegal in, so the check must not
 * reach them.
 */
class UnwritableCharacterRestoreTest {

    /** U+0008 BACKSPACE — a C0 control outside XML 1.0's {@code Char} production. */
    private static final String BACKSPACE = "\b";

    /** A lone high surrogate: a well-formed Java {@code char}, and never a character an XML document may carry. */
    private static final String UNPAIRED_SURROGATE = "\uD800";

    /** U+0085 NEXT LINE — a C1 control, which XML 1.0 permits and only XML 1.1 restricts. */
    private static final String NEXT_LINE = Character.toString(0x0085);

    /** U+009F APPLICATION PROGRAM COMMAND — the last C1 control, at the far end of the same legal range. */
    private static final String APPLICATION_PROGRAM_COMMAND = Character.toString(0x009F);

    private static final String SIMPLE_FB2 = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description><title-info><book-title>S</book-title><lang>uk</lang></title-info></description>
              <body><section><p>He opened the old door.</p></section></body>
            </FictionBook>
            """;

    @TempDir
    private Path tempDir;

    // IF a segment's format expresses inline structure as markup, THEN the system SHALL treat
    // every part of the target that is not a placeholder token as character data when composing the restored
    // content; a character no XML 1.0 document can carry is not character data at all, so that one segment fails
    // with ErrorCode.validation and the rest of the book stays exportable.
    @ParameterizedTest
    @MethodSource("illegalCharacterCases")
    void unmask_targetCarryingAnXmlIllegalCharacter_returnsValidationError(BookFormat format, String illegal) {
        final Result<String> result = newService().unmask(format, plainSegment(), "старі" + illegal + "двері");

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(result.data()).as("nothing is restored").isNull();
    }

    // The failure SHALL NOT include the segment's source text, the target text, or any file
    // path.
    @ParameterizedTest
    @MethodSource("illegalCharacterCases")
    void unmask_targetCarryingAnXmlIllegalCharacter_carriesNoBookTextOnTheFailure(BookFormat format, String illegal) {
        final Result<String> result = newService().unmask(format, plainSegment(), "старі" + illegal + "двері");

        final AppError error = errorOf(result);
        assertThat(error.details())
                .as("no allowlisted field may carry book text")
                .isNull();
        assertThat(error.message()).doesNotContain("двері", "old door");
        assertThat(error.title()).doesNotContain("двері", "old door");
    }

    // IF a segment's format expresses inline structure as markup, THEN the system SHALL treat
    // every part of the target that is not a placeholder token as character data when composing the restored
    // content, and the operation SHALL NOT fail.
    // A C1 control is legal under XML 1.0's Char production — only XML 1.1, which no format here emits, restricts
    // it — so rejecting one would refuse ordinary text over a rule this project does not live under.
    @ParameterizedTest
    @MethodSource("c1ControlCases")
    void unmask_targetCarryingAC1Control_restoresNormally(BookFormat format, String control) {
        final Result<String> result = newService().unmask(format, plainSegment(), "старі" + control + "двері");

        assertThat(result.data()).isEqualTo("старі" + control + "двері");
    }

    // IF a segment's format expresses inline structure as markup, THEN the system SHALL treat
    // every part of the target that is not a placeholder token as character data when composing the restored
    // content.
    // The buffer-shaped formats splice bytes back into their own file and have no XML to be illegal in, so the
    // check must not reach them — a scope guard, not a decoration.
    @Test
    void unmask_plainTextTargetCarryingABackspace_restoresNormally() {
        final Result<String> result = newService().unmask(BookFormat.TXT, plainSegment(), "старі" + BACKSPACE);

        assertThat(result.data()).isEqualTo("старі" + BACKSPACE);
    }

    // IF a segment's format expresses inline structure as markup, THEN the system SHALL treat
    // every part of the target that is not a placeholder token as character data when composing the restored
    // content, and the operation SHALL NOT fail.
    // The positive control: refusing one segment must not have made the ordinary restore-then-write path refuse
    // anything, so a valid target still reaches the written book.
    @Test
    void write_validTargetOnARealFb2Document_writesTheTranslatedText() {
        final DocumentService service = newService();
        final Document document = openFb2(service);
        final Segment segment = document.units().get(0).segments().get(0);
        final Result<String> restored = service.unmask(BookFormat.FB2, segment, "Він відчинив старі двері.");
        assertThat(restored.isOk())
                .withFailMessage("valid target refused: %s", restored.error())
                .isTrue();

        final Result<Path> written = service.write(
                SegmentTargets.withTargets(document, List.of(Objects.requireNonNull(restored.data(), "data"))),
                tempDir.resolve("out.fb2"),
                "uk");

        assertThat(written.isOk())
                .withFailMessage("write failed: %s", written.error())
                .isTrue();
        assertThat(readUtf8(Objects.requireNonNull(written.data(), "data")))
                .contains("<p>Він відчинив старі двері.</p>");
    }

    private static Stream<Arguments> illegalCharacterCases() {
        return Stream.of(
                Arguments.of(BookFormat.EPUB, BACKSPACE),
                Arguments.of(BookFormat.FB2, BACKSPACE),
                Arguments.of(BookFormat.EPUB, UNPAIRED_SURROGATE),
                Arguments.of(BookFormat.FB2, UNPAIRED_SURROGATE));
    }

    private static Stream<Arguments> c1ControlCases() {
        return Stream.of(
                Arguments.of(BookFormat.EPUB, NEXT_LINE),
                Arguments.of(BookFormat.FB2, NEXT_LINE),
                Arguments.of(BookFormat.EPUB, APPLICATION_PROGRAM_COMMAND),
                Arguments.of(BookFormat.FB2, APPLICATION_PROGRAM_COMMAND));
    }

    private Document openFb2(DocumentService service) {
        final Path file = Fb2Fixtures.writeFb2(tempDir.resolve("book.fb2"), SIMPLE_FB2, StandardCharsets.UTF_8);
        final Result<Document> opened = service.open(file);
        assertThat(opened.isOk())
                .withFailMessage("fixture did not open: %s", opened.error())
                .isTrue();
        return Objects.requireNonNull(opened.data(), "document");
    }

    private static Segment plainSegment() {
        return new Segment(
                "seg-1",
                "unit-1",
                0,
                SegmentKind.PARAGRAPH,
                "old door",
                "old door",
                Map.of(),
                "deadbeef",
                null,
                null,
                new NodeAnchor(List.of(0), 0),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    private static String readUtf8(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static AppError errorOf(Result<?> result) {
        return Objects.requireNonNull(result.error(), "error");
    }

    private static DocumentService newService() {
        return DocumentServices.newService();
    }
}
