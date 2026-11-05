package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.memory.CheckedParagraphs;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.RetryDraft;
import ua.bookloom.pipeline.revision.ConsistencyPass;

/** The backward-revision pass a job test's run ends with on Max, over the test project's own stores. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ConsistencyPassFixture {

    static ConsistencyPass over(final TestProject project) {
        return over(project, new PromptTemplates());
    }

    /** The pass built on {@code templates}, so an eval's variants reach its re-drafts too. */
    static ConsistencyPass over(final TestProject project, final PromptTemplates templates) {
        return new ConsistencyPass(
                project.documents(),
                project.stores().openProjects(),
                project.stores().projects(),
                project.stores().segments(),
                project.deferrals(),
                project.stores().glossary(),
                templates,
                new ObjectMapper(),
                retryDraft(project, templates),
                project.stores().lexicon(),
                new CheckedParagraphs());
    }

    static RetryDraft retryDraft(final TestProject project) {
        return retryDraft(project, new PromptTemplates());
    }

    static RetryDraft retryDraft(final TestProject project, final PromptTemplates templates) {
        return new RetryDraft(
                project.documents(),
                project.stores().openProjects(),
                project.stores().projects(),
                project.stores().segments(),
                project.stores().glossary(),
                project.stores().lexicon(),
                project.stores().promptFrames(),
                project.stores().runs(),
                templates,
                new ObjectMapper(),
                Guice.createInjector().getInstance(QualityLoop.class),
                Guice.createInjector(new DocumentModule()).getInstance(SentenceSplitter.class),
                () -> ReviewMode.UNATTENDED,
                java.time.Clock.systemUTC());
    }
}
