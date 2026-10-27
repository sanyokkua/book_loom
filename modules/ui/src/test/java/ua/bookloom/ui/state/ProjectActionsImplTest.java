package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.ScriptedProjectEdits.TranslateCall;
import ua.bookloom.ui.ViewNames;

/** What the Translate-into and Same-language-pair row actions ask the service and the prompts. */
class ProjectActionsImplTest extends ProjectActionsTestBase {

    private static BookGroup groupOf(final Project... projects) {
        return new BookGroup(
                ProjectFixtures.BOOK_HASH,
                "Earth Gravity",
                ProjectFixtures.BOOK_FILE,
                UK.format(),
                ProjectFixtures.BOOK_SEGMENTS,
                Arrays.stream(projects).map(ProjectActionsTestBase::rowOf).toList());
    }

    private void translateIntoGroup() throws Exception {
        onFx(() -> actions().translateInto(groupOf(UK, DE)));
        settle();
    }

    @Test
    void translateInto_asked_handsTheBookAndRulesOfItsOwnProjectsToThePrompt() throws Exception {
        listStored(UK, DE, OTHER);

        translateIntoGroup();

        assertThat(translate.asked()).hasSize(1);
        assertThat(translate.asked().getFirst().group().contentHash()).isEqualTo(ProjectFixtures.BOOK_HASH);
        final ProjectNameRules rules = translate.asked().getFirst().rules();
        assertThat(rules.check("Земне тяжіння")).isEqualTo(ProjectNameRules.Check.USED);
        assertThat(rules.check("Shared name")).isEqualTo(ProjectNameRules.Check.OK);
    }

    // IF Create copied from the wrong translation, THEN the new project would start from another project's brief.
    @Test
    void translateInto_created_copiesTheFirstRowsProjectAndOpensTheNewOneAtTheBrief() throws Exception {
        listStored(UK, DE);
        final Project created =
                book("p-new", "fr", NameOrigin.AUTO, "Earth Gravity · EN → FR", ProjectFixtures.BOOK_HASH);
        service.edits().onTranslateInto(Result.ok(new ProjectChange.Saved(created)));
        translateIntoGroup();

        onFx(() -> translate.create("fr", null));
        settle();

        assertThat(service.edits().translations()).containsExactly(new TranslateCall("p-uk", "fr", null));
        assertThat(opener.opened()).hasSize(1);
        assertThat(opener.opened().getFirst().summary().project().id()).isEqualTo("p-new");
        assertThat(opener.opened().getFirst().step()).isEqualTo(ViewNames.BOOK_BRIEF);
    }

    @Test
    void translateInto_createdWithATypedName_passesTheNameOn() throws Exception {
        listStored(UK, DE);
        service.edits().onTranslateInto(Result.ok(new ProjectChange.Saved(UK)));
        translateIntoGroup();

        onFx(() -> translate.create("uk", "Earth Gravity · 26b"));
        settle();

        assertThat(service.edits().translations())
                .containsExactly(new TranslateCall("p-uk", "uk", "Earth Gravity · 26b"));
    }

    @Test
    void translateInto_cancelled_makesNoCallAndOpensNothing() throws Exception {
        listStored(UK, DE);
        translateIntoGroup();

        onFx(translate::cancel);
        settle();

        assertThat(service.edits().translations()).isEmpty();
        assertThat(opener.opened()).isEmpty();
    }

    // IF a stale list let a used pair through, THEN the service's PairInUse must lead to Same language pair, not a
    // silent failure.
    @Test
    void translateInto_pairAlreadyUsed_opensSamePairWithTheExistingProject() throws Exception {
        listStored(UK, DE);
        service.edits().onTranslateInto(Result.ok(new ProjectChange.PairInUse(UK)));
        translateIntoGroup();

        onFx(() -> translate.create("uk", null));
        settle();

        assertThat(samePair.asked()).hasSize(1);
        assertThat(samePair.asked().getFirst().existing().summary().project().id())
                .isEqualTo("p-uk");
        assertThat(samePair.asked().getFirst().languageName()).isEqualTo("Ukrainian");
        assertThat(samePair.asked().getFirst().keepName()).isEqualTo("Cancel");
        assertThat(opener.opened()).isEmpty();
    }

    @Test
    void samePair_makeSecondChosen_createsTheTranslationWithTheTypedName() throws Exception {
        listStored(UK, DE);
        service.edits().onTranslateInto(Result.ok(new ProjectChange.PairInUse(UK)));
        translateIntoGroup();
        onFx(() -> translate.create("uk", null));
        settle();
        service.edits().onTranslateInto(Result.ok(new ProjectChange.Saved(UK)));

        onFx(() -> samePair.makeSecond("Earth Gravity · second try"));
        settle();

        assertThat(service.edits().translations())
                .containsExactly(
                        new TranslateCall("p-uk", "uk", null),
                        new TranslateCall("p-uk", "uk", "Earth Gravity · second try"));
        assertThat(opener.opened()).hasSize(1);
    }

    @Test
    void samePair_openExistingChosen_opensTheExistingProject() throws Exception {
        listStored(UK, DE);
        service.edits().onTranslateInto(Result.ok(new ProjectChange.PairInUse(UK)));
        translateIntoGroup();
        onFx(() -> translate.create("uk", null));
        settle();

        onFx(samePair::openExisting);
        settle();

        assertThat(opener.opened()).hasSize(1);
        assertThat(opener.opened().getFirst().summary().project().id()).isEqualTo("p-uk");
        assertThat(service.edits().translations()).hasSize(1);
    }
}
