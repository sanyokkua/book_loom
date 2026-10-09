package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.UnknownGender;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * Finds the characters Start should ask about: those with no gender whom the book names {@value #MIN_MENTIONS} times
 * or more. The glossary is read off the FX thread; the answer is delivered on it. A failed read is "none": the question
 * is a courtesy, and must never stop a run from starting.
 */
@Slf4j
@Singleton
public final class UnknownGenders {

    /** The least number of mentions that makes a character worth a question at Start. */
    public static final int MIN_MENTIONS = 5;

    private final GlossaryCalls calls;
    private final GlossaryService glossary;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param glossary the port the characters are read through
     * @param executor the daemon executor the read runs on, never the FX thread
     */
    @Inject
    public UnknownGenders(final GlossaryService glossary, final @BackgroundExecutor ExecutorService executor) {
        this.glossary = Objects.requireNonNull(glossary, "glossary");
        this.calls = new GlossaryCalls(executor);
    }

    /**
     * Reads the characters to ask about.
     *
     * @param projectId the stored project
     * @param onFx receives the characters, most mentioned first, on the FX thread; empty when none or on failure
     */
    public void find(final String projectId, final Consumer<List<UnknownGender>> onFx) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(onFx, "onFx");
        log.debug("looking for characters of project {} with unknown gender and {}+ mentions", projectId, MIN_MENTIONS);
        calls.run("unknown genders", () -> glossary.unknownGenders(projectId, MIN_MENTIONS), answer -> {
            final List<UnknownGender> found = answer.data() == null ? List.of() : answer.data();
            log.debug("project {} has {} characters to ask about, ok {}", projectId, found.size(), answer.isOk());
            onFx.accept(found);
        });
    }
}
