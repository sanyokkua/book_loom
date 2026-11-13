package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.function.Function;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.NameKnowledgeCalls;
import ua.bookloom.pipeline.glossary.PronounGender;
import ua.bookloom.pipeline.glossary.TermReading;
import ua.bookloom.pipeline.index.Mention;
import ua.bookloom.pipeline.index.Window;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.setup.SetupAssistantImpl;

/**
 * Analysis calls as their own services send them to a recording model: the file name and a brief passage reading from
 * the setup assistant over an invented English book, a term reading and the bare-name question over an invented
 * Russian book whose style sheet names a first-person female narrator. The constant name is the call's kind.
 */
// The functions are stateless lambdas; Error Prone cannot see that without @Immutable.
@SuppressWarnings("ImmutableEnumChecker")
enum AnalysisCalls {
    FILE_NAME("English (en)", folder -> {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply("{\"target\":\"Книга. 2000\"}"));
        final ReviewFixtures.Desk desk = ReviewFixtures.markdown(folder, Fixture.BOOK);
        assistant(desk).suggestFileName(desk.projectId(), model);
        return model;
    }),
    BRIEF_SUGGESTION("English (en)", folder -> {
        final ScriptedChatModel model = new ScriptedChatModel().answerTimes(Fixture.ANSWERS, reply("{}"));
        final ReviewFixtures.Desk desk = ReviewFixtures.markdown(folder, Fixture.BOOK);
        assistant(desk).suggestBrief(desk.projectId(), model);
        return model;
    }),
    TERM_READING("Russian (ru)", folder -> {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply("{\"windows\":[],\"speaking\":false,\"brand\":false}"));
        new TermReading(new PromptTemplates(), new ObjectMapper())
                .read(
                        new TermReading.Subject(
                                Fixture.NAME, 1, new PronounGender.Evidence(List.of(), List.of()), List.of()),
                        List.of(new Window(
                                new Mention(Fixture.NAME, 0, 0, 7, 0, true), 0, 0, Fixture.NAME + " ушёл.", 0, 7)),
                        Fixture.RU_UK,
                        (kind, segmentId, request) -> model.chat(request));
        return model;
    }),
    NAME_KNOWLEDGE("Russian (ru)", folder -> {
        final ScriptedChatModel model = new ScriptedChatModel().answerTimes(Fixture.ANSWERS, reply("{\"items\":[]}"));
        new NameKnowledgeCalls(new PromptTemplates(), new ObjectMapper())
                .ask(
                        List.of(GlossaryEntry.of(
                                "g1", "p1", Fixture.NAME, null, TermType.CHARACTER, Gender.UNKNOWN, false)),
                        Fixture.RU_UK,
                        (kind, segmentId, request) -> model.chat(request));
        return model;
    });

    /** The narrator line of the Russian book's style sheet, which no analysis call may carry. */
    static final String NARRATOR_FEMALE = "Narrator: first person, female.";

    /** The invented books' facts, apart so the constants above can read them. */
    private static final class Fixture {
        static final String NAME = "Брэннок";
        static final int ANSWERS = 16;
        static final String BOOK = String.join(
                "\n\n",
                "Zerubin waited by the old fireplace for hours.",
                "Edwin opened the door and the master smiled.",
                "I walked home through the cold rain.",
                "The lamp burned low on the table.",
                "Nobody spoke until the clock struck nine.",
                "The master looked at the map for a long time.",
                "Outside, the wind shook the shutters.",
                "I closed the book and went to sleep.");
        static final CallFrame RU_UK = new CallFrame(
                "ru",
                "uk",
                StyleSheet.from(BookBrief.defaults("ru")
                        .withLanguages("ru", "uk")
                        .withNarrator(new Narrator(NarratorPerson.FIRST, Gender.FEMALE))),
                ForeignPassagePolicy.KEEP);
    }

    private final String language;
    private final Function<Path, ScriptedChatModel> service;

    AnalysisCalls(final String language, final Function<Path, ScriptedChatModel> service) {
        this.language = language;
        this.service = service;
    }

    /** The book's language as the role names it. */
    String language() {
        return language;
    }

    /** The call's kind. */
    CallKind kind() {
        return CallKind.valueOf(name());
    }

    /**
     * Runs the service once.
     *
     * @param folder an empty folder the setup calls open their book in
     * @return the model, holding every request it was sent
     */
    ScriptedChatModel send(final Path folder) {
        return service.apply(folder);
    }

    private static Result<ChatResponse> reply(final String json) {
        return Result.ok(new ChatResponse(json, FinishReason.STOP));
    }

    private static SetupAssistantImpl assistant(final ReviewFixtures.Desk desk) {
        return new SetupAssistantImpl(
                desk.projects(),
                desk.segments(),
                desk.glossary(),
                desk.openProjects(),
                new PromptTemplates(),
                new ObjectMapper(),
                Clock.systemUTC());
    }
}
