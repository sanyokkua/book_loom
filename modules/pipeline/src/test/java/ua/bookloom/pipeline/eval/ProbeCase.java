package ua.bookloom.pipeline.eval;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * One case the probe can send: its id, the suite it belongs to, and the production eval class that sends it and scores
 * the reply.
 *
 * @param id the case id as the suite lists it
 * @param suite {@code prompt}, {@code defects} or {@code realrun}
 * @param run sends the case once through the production builders and returns the verdict lines
 */
record ProbeCase(String id, String suite, Function<Sending, List<String>> run) {

    /**
     * What a case needs to be sent.
     *
     * @param calls the run's model-call seam, sized to the window
     * @param window the window the prompt cases build their requests against
     * @param workDir a directory the repair cases write their source into
     */
    record Sending(ModelCalls calls, int window, Path workDir) {}
}
