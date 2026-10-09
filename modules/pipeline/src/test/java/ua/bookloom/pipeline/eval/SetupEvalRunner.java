package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FileNameSuggestion;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Prepared;
import ua.bookloom.pipeline.eval.StageCases.SetupCase;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.setup.SetupAssistantImpl;

/**
 * Imports each case's small FB2 book through the real project service and asks the production
 * {@link SetupAssistantImpl} for the file name (FILE_NAME) and the Book Brief suggestion (BRIEF_SUGGESTION). The name
 * must be in the target's script with its author part in it too; the brief must give the narrator the book is told in,
 * the narrator's gender, the register and a genre of the case's class.
 */
@Slf4j
final class SetupEvalRunner {

    private final StageProbe probe;
    private final Path workDir;

    SetupEvalRunner(final ChatModel model, final Path workDir) {
        this.probe = new StageProbe(Objects.requireNonNull(model, "model"));
        this.workDir = Objects.requireNonNull(workDir, "workDir");
    }

    StageProbe probe() {
        return probe;
    }

    List<StageRow> run(final List<SetupCase> cases) {
        final List<StageRow> rows = new ArrayList<>();
        for (final SetupCase setupCase : cases) {
            log.info("Setup eval case={}", setupCase.id());
            final Prepared prepared = SequenceJobs.importBook(
                    write(setupCase),
                    SequenceJobs.brief(
                            setupCase.source(), setupCase.target(), QualityDial.FAST, Narrator.unspecified()));
            final SetupAssistantImpl assistant = new SetupAssistantImpl(
                    prepared.stores().projects(),
                    prepared.stores().segments(),
                    prepared.stores().glossary(),
                    prepared.stores().openProjects(),
                    new PromptTemplates(),
                    new ObjectMapper(),
                    java.time.Clock.systemUTC());
            rows.add(fileName(setupCase, assistant, prepared));
            rows.add(brief(setupCase, assistant, prepared));
        }
        return rows;
    }

    private StageRow fileName(final SetupCase setupCase, final SetupAssistantImpl assistant, final Prepared prepared) {
        final int mark = probe.mark();
        final Result<FileNameSuggestion> result = assistant.suggestFileName(prepared.projectId(), probe);
        final StageProbe.Counts counts = probe.since(mark);
        final String id = setupCase.id() + ":name";
        if (result.isErr()) {
            return failed(id, Objects.requireNonNull(result.error()).code().name(), counts);
        }
        final FileNameSuggestion name = Objects.requireNonNull(result.data(), "name");
        // The cases all translate into Ukrainian, so the target's script is Cyrillic.
        final boolean cyrillic = name.name()
                .codePoints()
                .anyMatch(point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.CYRILLIC);
        final boolean passed = cyrillic && !name.authorKeptInSourceScript();
        return new StageRow(
                id,
                passed,
                "'" + name.name() + "' cyrillic=" + cyrillic + " authorKept=" + name.authorKeptInSourceScript(),
                counts.calls(),
                counts.failed(),
                counts.repeated(),
                name.authorKeptInSourceScript() ? 1 : 0);
    }

    private StageRow brief(final SetupCase setupCase, final SetupAssistantImpl assistant, final Prepared prepared) {
        final int mark = probe.mark();
        final Result<BriefSuggestion> result = assistant.suggestBrief(prepared.projectId(), probe);
        final StageProbe.Counts counts = probe.since(mark);
        final String id = setupCase.id() + ":brief";
        if (result.isErr()) {
            return failed(id, Objects.requireNonNull(result.error()).code().name(), counts);
        }
        final BriefSuggestion brief = Objects.requireNonNull(result.data(), "brief");
        final List<String> misses = misses(setupCase, brief);
        return new StageRow(
                id,
                misses.isEmpty(),
                (misses.isEmpty() ? "" : "missed " + String.join(",", misses) + ": ") + "narrator=" + brief.narrator()
                        + " gender=" + brief.narratorGender() + " register=" + brief.register() + " genre="
                        + brief.genre(),
                counts.calls(),
                counts.failed(),
                counts.repeated(),
                0);
    }

    // Every field a reader settles is asserted, not only the narrator: the brief is accepted as is by most people.
    static List<String> misses(final SetupCase setupCase, final BriefSuggestion brief) {
        final List<String> misses = new ArrayList<>();
        if (brief.narrator() != setupCase.narrator()) {
            misses.add("narrator");
        }
        if (brief.narratorGender() != setupCase.narratorGender()) {
            misses.add("narratorGender");
        }
        if (brief.register() != setupCase.register()) {
            misses.add("register");
        }
        if (!JudgementGold.isOfClass(setupCase.genreClass(), brief.genre())) {
            misses.add("genre");
        }
        log.debug("Setup eval case={} brief misses={}", setupCase.id(), misses);
        return misses;
    }

    private static StageRow failed(final String id, final String code, final StageProbe.Counts counts) {
        return new StageRow(id, false, "error " + code, counts.calls(), counts.failed(), counts.repeated(), 0);
    }

    private Path write(final SetupCase setupCase) {
        return Fb2Fixture.write(
                workDir,
                new Fb2Fixture.Meta(setupCase.fileStem(), setupCase.title(), setupCase.author(), setupCase.source()),
                List.of(new Fb2Fixture.Section(null, setupCase.paragraphs())));
    }
}
