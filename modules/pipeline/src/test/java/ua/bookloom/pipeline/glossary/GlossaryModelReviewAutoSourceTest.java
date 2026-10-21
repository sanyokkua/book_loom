package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.book;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.project;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * "Review with model" on a book that declares English while the source language is Auto: the prompts say the language
 * is to be inferred, yet the code's own pronoun reading uses the declaration the application recognises.
 */
class GlossaryModelReviewAutoSourceTest {

    private static final String INFER = "the language of this segment (infer it from its text)";
    private static final String NO_SUGGESTIONS = "{\"suggestions\":[]}";
    private static final String ASH_VERDICT =
            "{\"verdicts\":[{\"term\":\"Ash\",\"verdict\":\"name\",\"type\":\"term\",\"gender\":\"unknown\"}]}";

    private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();
    private Injector injector;
    private GlossaryService service;

    @BeforeEach
    void setUp() {
        injector = Guice.createInjector(
                new DocumentModule(),
                new InMemoryPersistenceModule(),
                binder -> binder.bind(Clock.class).toInstance(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
        service = injector.getInstance(GlossaryServiceImpl.class);
    }

    private ChatModel recording() {
        return request -> {
            requests.add(request);
            final String content = request.callKind() == CallKind.REVIEW_TERMS ? ASH_VERDICT : NO_SUGGESTIONS;
            return Result.ok(new ChatResponse(content, FinishReason.STOP));
        };
    }

    private static String textOf(final ChatRequest request) {
        return request.messages().stream().map(ChatMessage::content).collect(Collectors.joining("\n"));
    }

    // IF the scans' frame carried only the prompt's source, THEN Auto would drop the declared English and the five
    // "she"s after Wren would give the code no evidence; IF it carried the declared tag into a prompt, THEN the model
    // would be told a language nobody chose.
    @Test
    void review_autoSourceOnABookDeclaringEnglish_readsEnglishPronounsAndPromptsInferTheLanguage() {
        final BookBrief auto = BookBrief.defaults("en").withLanguages(null, "uk");
        injector.getInstance(ProjectRepository.class).save(project(auto));
        final List<String> body = Stream.concat(
                        IntStream.range(0, 5).mapToObj(day -> "Day " + day + ". Wren smiled. She left."),
                        Stream.of("Ash lay in the road."))
                .toList();
        injector.getInstance(OpenProjects.class).put(PROJECT, book(body, "Title"));
        service.add(new GlossaryEntry("p1:wren", PROJECT, "Wren", null, TermType.CHARACTER, Gender.UNKNOWN, false));
        service.add(new GlossaryEntry("p1:ash", PROJECT, "Ash", null, TermType.OTHER, Gender.UNKNOWN, false));

        final var report = service.review(PROJECT, recording(), event -> {});

        assertThat(report.isOk()).isTrue();
        assertThat(Objects.requireNonNull(service.entries(PROJECT).data()))
                .filteredOn(entry -> entry.term().equals("Wren"))
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(tuple(Gender.FEMALE, GenderOrigin.VERIFIED));
        assertThat(requests).extracting(ChatRequest::callKind).contains(CallKind.REVIEW_TERMS);
        assertThat(requests.stream()
                        .map(GlossaryModelReviewAutoSourceTest::textOf)
                        .toList())
                .anySatisfy(text -> assertThat(text).contains(INFER))
                .allSatisfy(text -> assertThat(text).doesNotContain("English (en)"));
    }
}
