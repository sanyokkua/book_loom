package ua.bookloom.pipeline;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.proofread.CodeSweep;
import ua.bookloom.pipeline.proofread.ProofreadTools;

/** The Proofread stage's collaborators a job test's run ends with, over the test project's own stores. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProofreadFixture {

    static ProofreadTools over(final TestProject project) {
        return new ProofreadTools(
                new CodeSweep(
                        project.stores().segments(),
                        project.deferrals(),
                        project.stores().glossary()),
                project.deferrals());
    }
}
