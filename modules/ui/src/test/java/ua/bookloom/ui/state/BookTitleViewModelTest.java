package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.List;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.OriginalTitle;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.pipeline.TitleOrigin;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ScriptedReviewDesk;
import ua.bookloom.ui.state.BookTitleViewModel.Part;
import ua.bookloom.ui.state.BookTitleViewModel.Row;

/**
 * The title row follows the resolver's origin: it reads the segment the resolver names, shows the origin, and a title
 * taken from the file name has no segment, so nothing can be edited.
 */
class BookTitleViewModelTest extends FxTestBase {

    private CurrentProject current;
    private ScriptedReviewDesk desk;
    private BookTitleViewModel titles;

    @Override
    public void start(final Stage stage) {
        // The toolkit is all these tests need; the view model has no scene.
    }

    @BeforeEach
    void setUpViewModel() {
        current = new CurrentProject();
        desk = new ScriptedReviewDesk();
        titles = onFx(() -> new BookTitleViewModel(
                current,
                desk,
                new DirectExecutor(),
                new ua.bookloom.ui.i18n.Messages((ua.bookloom.ui.i18n.LocaleProvider) () -> java.util.Locale.ENGLISH)));
    }

    private static SegmentView view(final String id, final String source, final String target) {
        return new SegmentView(
                id,
                "aux",
                SegmentKind.FRONTMATTER_VALUE,
                SegmentStatus.ACCEPTED,
                source,
                source,
                target,
                null,
                null,
                List.of(),
                null,
                SegmentPath.DRAFT,
                false,
                null,
                null);
    }

    private void openBook() {
        final ImportedBook imported =
                BookFixtures.imported("p1", ua.bookloom.api.document.BookFormat.MARKDOWN, null, null, null, 1);
        onFx(() -> {
            current.open(new OpenedBook(
                    "p1",
                    Path.of("notes.md"),
                    imported.inspection(),
                    imported.profile(),
                    ua.bookloom.api.project.BookBrief.defaults("en"),
                    null));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private Row title() {
        return onFx(() -> titles.title().get());
    }

    // IF the row still read a fixed aux:title, THEN a Markdown book's front-matter title would never be shown.
    @Test
    void title_frontMatterTitle_showsItsSourceTargetAndOrigin() {
        desk.willAnswerOriginalTitle(new OriginalTitle("Field Notes", TitleOrigin.FRONT_MATTER, "aux:fm:title"));
        desk.willAnswerSegment(view("aux:fm:title", "Field Notes", "Польові нотатки"));

        openBook();

        assertThat(title())
                .extracting(Row::source, Row::target, Row::editable, Row::origin)
                .containsExactly("Field Notes", "Польові нотатки", true, TitleOrigin.FRONT_MATTER);
    }

    // IF the file name were editable, THEN an edit would have no stored segment to land in.
    @Test
    void title_fileNameOrigin_hasNoTargetAndCannotBeEdited() {
        desk.willAnswerOriginalTitle(new OriginalTitle("earth-gravity", TitleOrigin.FILE_NAME, null));

        openBook();

        assertThat(title())
                .extracting(Row::source, Row::target, Row::editable, Row::origin)
                .containsExactly("earth-gravity", "", false, TitleOrigin.FILE_NAME);
        // The author row still reads its own segment; only the title must not be read.
        assertThat(desk.calls()).noneMatch(call -> call.startsWith("segment(") && !call.contains("aux:creator"));
    }

    // IF an edit went to a fixed id, THEN a front-matter title's edit would be stored where no title lives.
    @Test
    void save_frontMatterTitle_storesTheEditOnTheResolvedSegment() {
        desk.willAnswerOriginalTitle(new OriginalTitle("Field Notes", TitleOrigin.FRONT_MATTER, "aux:fm:title"));
        desk.willAnswerSegment(view("aux:fm:title", "Field Notes", "Польові нотатки"));
        openBook();

        onFx(() -> {
            titles.save(Part.TITLE, "Польові записи");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(desk.calls()).contains("saveEdit(p1, aux:fm:title, Польові записи)");
    }

    // IF a file-name title accepted an edit, THEN the desk would be asked to edit a segment that does not exist.
    @Test
    void save_fileNameTitle_isRefusedWithoutCallingTheDesk() {
        desk.willAnswerOriginalTitle(new OriginalTitle("earth-gravity", TitleOrigin.FILE_NAME, null));
        openBook();

        onFx(() -> {
            titles.save(Part.TITLE, "Земне тяжіння");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(desk.calls()).noneMatch(call -> call.startsWith("saveEdit("));
    }

    // IF the author row took an origin, THEN the card would print a reason line under an author.
    @Test
    void author_whenRead_hasNoOrigin() {
        desk.willAnswerOriginalTitle(new OriginalTitle("Field Notes", TitleOrigin.DETAILS, "aux:title"));
        desk.willAnswerSegment(view("aux:title", "Field Notes", "Польові нотатки"));
        desk.willAnswerSegment(view("aux:creator:0", "Ann Hale", "Анна Гейл"));

        openBook();

        assertThat(onFx(() -> titles.author().get()).origin()).isNull();
    }

    private static SegmentView markedHeading() {
        return new SegmentView(
                "ch1:0",
                "ch1:0",
                SegmentKind.HEADING,
                SegmentStatus.ACCEPTED,
                "The ⟦g0⟧Field⟦g1⟧ Notes",
                "The Field Notes",
                "Нотатки ⟦g0⟧Поля⟦g1⟧",
                null,
                null,
                List.of(),
                null,
                SegmentPath.DRAFT,
                false,
                null,
                null);
    }

    private void openMarkedHeading() {
        desk.willAnswerOriginalTitle(new OriginalTitle("The Field Notes", TitleOrigin.FIRST_HEADING, "ch1:0"));
        desk.willAnswerSegment(markedHeading());
        openBook();
    }

    private void save(final String text) {
        onFx(() -> {
            titles.save(Part.TITLE, text);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF a heading title showed its stored codes, THEN the person would read ⟦g0⟧ in the book's own title.
    @Test
    void title_headingWithMarks_showsNumberedMarksInTheSourceAndTheTranslation() {
        openMarkedHeading();

        assertThat(title().sourceShown()).isEqualTo("The ⟦0⟧Field⟦1⟧ Notes");
        assertThat(title().target()).isEqualTo("Нотатки ⟦0⟧Поля⟦1⟧");
    }

    // IF the shown form were stored as typed, THEN the desk would receive marks it does not know.
    @Test
    void save_headingWithMarks_storesTheTextWithTheMarksPutBackToTheStoredForm() {
        openMarkedHeading();

        save("Записи ⟦0⟧Поля⟦1⟧");

        assertThat(desk.calls()).contains("saveEdit(p1, ch1:0, Записи ⟦g0⟧Поля⟦g1⟧)");
    }

    // IF an edit lacking a mark reached the desk, THEN the gate would refuse it with only a log line.
    @Test
    void save_headingMissingAMark_isRefusedHereWithTheMessageAndNoDeskCall() {
        openMarkedHeading();

        save("Записи ⟦0⟧Поля");

        assertThat(desk.calls()).noneMatch(call -> call.startsWith("saveEdit("));
        assertThat(onFx(() -> titles.refusal().get()))
                .isEqualTo("Not saved: the title lacks the formatting marks ⟦1⟧.");
    }

    // IF a refusal by the gate were silent, THEN a person would think the title was saved.
    @Test
    void save_gateRefusesTheEdit_showsANotSavedMessage() {
        openMarkedHeading();
        desk.willAnswer(Result.err(AppError.of(ErrorCode.validation, "Refused", "The placeholders differ.")));

        save("Записи ⟦0⟧Поля⟦1⟧");

        assertThat(onFx(() -> titles.refusal().get())).startsWith("Not saved:");
    }

    @Test
    void save_afterARefusal_aGoodEditClearsTheMessage() {
        openMarkedHeading();
        save("Записи ⟦0⟧Поля");

        save("Записи ⟦0⟧Поля⟦1⟧");

        assertThat(onFx(() -> titles.refusal().get())).isEmpty();
    }
}
