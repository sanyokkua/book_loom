package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * must be in the target's script with its author part in it too; the brief must name the narrator the book is told in.
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
        return new StageRow(
                id,
                brief.narrator() == setupCase.narrator(),
                "narrator=" + brief.narrator() + " gender=" + brief.narratorGender() + " register=" + brief.register(),
                counts.calls(),
                counts.failed(),
                counts.repeated(),
                0);
    }

    private static StageRow failed(final String id, final String code, final StageProbe.Counts counts) {
        return new StageRow(id, false, "error " + code, counts.calls(), counts.failed(), counts.repeated(), 0);
    }

    private Path write(final SetupCase setupCase) {
        final String[] author = setupCase.author().split(" ", 2);
        final String body = String.join(
                "\n",
                setupCase.paragraphs().stream()
                        .map(text -> "<p>" + text + "</p>")
                        .toList());
        final String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
            <description><title-info>
            <author><first-name>%s</first-name><last-name>%s</last-name></author>
            <book-title>%s</book-title><lang>%s</lang>
            </title-info></description>
            <body><section>%s</section></body>
            </FictionBook>
            """.formatted(
                        author[0], author.length > 1 ? author[1] : "", setupCase.title(), setupCase.source(), body);
        try {
            return Files.writeString(workDir.resolve(setupCase.fileStem() + ".fb2"), xml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
