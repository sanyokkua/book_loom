package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.revision.ConsistencyPass;

/** The backward-revision pass a job test's run ends with on Max, over the test project's own stores. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ConsistencyPassFixture {

    static ConsistencyPass over(final TestProject project) {
        return new ConsistencyPass(
                project.documents(),
                project.stores().openProjects(),
                project.stores().projects(),
                project.stores().segments(),
                project.deferrals(),
                project.stores().glossary(),
                new PromptTemplates(),
                new ObjectMapper());
    }
}
