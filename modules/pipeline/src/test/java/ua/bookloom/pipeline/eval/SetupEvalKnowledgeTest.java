package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.eval.StageCases.SetupCase;

/**
 * The setup suite asks the production knowledge question (15i.B2) and records the verdict in
 * {@code Observed.knowledge}, offline over the scripted model: a classic's title, author and year are echoed and the
 * verdict is {@code known}; an invented book is {@code unknown}; a book with no metadata makes no call at all.
 */
class SetupEvalKnowledgeTest {

    @TempDir
    private Path dir;

    private static SetupCase caseOf(final String id) {
        return StageCases.setup().stream()
                .filter(setupCase -> setupCase.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static List<ChatRequest> knowledgeRequests(final SetupEvalRunner runner) {
        return runner.probe().requests().stream()
                .filter(request -> request.callKind() == CallKind.BOOK_KNOWLEDGE)
                .toList();
    }

    @Test
    void run_classicBook_isAskedOnceAboutItsTitleAndAuthorAndIsKnown() {
        final SetupCase classic = caseOf("classic-dracula");
        final SetupEvalRunner runner = new SetupEvalRunner(new SetupScriptedModel(List.of(classic), Set.of()), dir);

        final List<StageRow> rows = runner.run(List.of(classic));

        assertThat(knowledgeRequests(runner)).hasSize(1);
        assertThat(knowledgeRequests(runner).getFirst().messages().getLast().content())
                .contains("Dracula", "Bram Stoker");
        assertThat(runner.observed()).extracting(SetupScore.Observed::knowledge).containsExactly("known");
        assertThat(rows.stream().filter(row -> row.id().endsWith(":knowledge")))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo("classic-dracula:knowledge");
                    assertThat(row.passed()).isTrue();
                    assertThat(row.calls()).isEqualTo(1);
                });
    }

    @Test
    void run_inventedBook_isAskedOnceAndIsUnknown() {
        final SetupCase invented = caseOf("invented-03-salt-road");
        final SetupEvalRunner runner = new SetupEvalRunner(new SetupScriptedModel(List.of(invented), Set.of()), dir);

        final List<StageRow> rows = runner.run(List.of(invented));

        assertThat(knowledgeRequests(runner)).hasSize(1);
        assertThat(runner.observed()).extracting(SetupScore.Observed::knowledge).containsExactly("unknown");
        assertThat(rows.stream().filter(row -> row.id().endsWith(":knowledge")))
                .singleElement()
                .satisfies(row -> assertThat(row.passed()).isTrue());
    }

    // IF a model "knew" an invented book and echoed what it was asked, THEN the verdict is known and the row fails: the
    // echo check proves the answer is about this book, not that the model knows it.
    @Test
    void run_modelClaimsToKnowAnInventedBook_rowFailsAndObservedIsKnown() {
        final SetupCase invented = caseOf("invented-03-salt-road");
        final SetupEvalRunner runner =
                new SetupEvalRunner(new SetupScriptedModel(List.of(invented), Set.of(invented.id())), dir);

        final List<StageRow> rows = runner.run(List.of(invented));

        assertThat(runner.observed()).extracting(SetupScore.Observed::knowledge).containsExactly("known");
        assertThat(rows.stream().filter(row -> row.id().endsWith(":knowledge")))
                .singleElement()
                .satisfies(row -> assertThat(row.passed()).isFalse());
        assertThat(runner.score().line()).endsWith("knowledge.invented 0/1 knowledge.classic n/a");
    }

    @Test
    void run_bookWithNoMetadata_makesNoKnowledgeCallAndIsUnknown() {
        final SetupCase untitled = caseOf("setup-no-metadata");
        final SetupEvalRunner runner = new SetupEvalRunner(new SetupScriptedModel(List.of(untitled), Set.of()), dir);

        final List<StageRow> rows = runner.run(List.of(untitled));

        assertThat(knowledgeRequests(runner)).isEmpty();
        assertThat(runner.observed()).extracting(SetupScore.Observed::knowledge).containsExactly("unknown");
        assertThat(rows.stream().filter(row -> row.id().endsWith(":knowledge")))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.passed()).isTrue();
                    assertThat(row.calls()).isZero();
                    assertThat(row.detail()).contains("no-title");
                });
    }
}
