package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;

/**
 * A French plain-text book with no metadata, translated with the source language Auto: the person chose nothing and the
 * file declares nothing, so the model is told to infer the language, no English pronoun is read as evidence of a
 * gender, and a French function word is never proposed as a name.
 *
 * <p>The book is invented French prose, written for this test.
 */
// `slow`: a whole run end to end; `test` and the gate run it, the `fastTest` inner loop leaves it out.
@Tag("slow")
class AutoSourceEndToEndTest {

    private static final String INFER = "the language of this segment (infer it from its text)";

    private static final String BOOK = String.join(
            "\n\n",
            "Ce mardi-là, le brouillard couvrait toute la petite ville de Vaux, et personne n'osait sortir de chez soi.",
            "La vieille Aubry ouvrit sa fenêtre, regarda la rue déserte, puis la referma aussitôt pour ne pas laisser"
                    + " entrer le froid.",
            "Sur la table de la cuisine, elle avait posé Le Courrier du matin, qu'elle lisait chaque jour en buvant"
                    + " son café.",
            "Le facteur arriva en retard, les mains rouges et le nez dans son écharpe. Il déposa une lettre sur le"
                    + " seuil et s'éloigna sans un mot.",
            "Aubry reconnut l'écriture de son neveu. Elle ouvrit l'enveloppe lentement, comme si elle craignait ce"
                    + " qu'elle allait y trouver.",
            "Il annonçait son retour pour le samedi suivant, avec une jeune femme qu'il voulait lui présenter. La"
                    + " vieille dame sourit longtemps.",
            "À midi, le brouillard ne s'était pas levé. Les enfants jouaient dans la cour de l'école de Vaux, et leurs"
                    + " cris traversaient l'air épais.",
            "Dans Le Courrier, on parlait d'un pont que la ville voulait construire sur la rivière. Aubry haussa les"
                    + " épaules : on en parlait depuis dix ans.",
            "Le soir venu, elle alluma la lampe et relut la lettre. Dehors, les cloches de Vaux sonnèrent six heures,"
                    + " puis sept.",
            "Le lendemain, elle se rendit chez le boulanger pour acheter un gâteau. Tout le village savait déjà que le"
                    + " neveu de la vieille Aubry reviendrait.",
            "On disait dans Le Courrier que la rivière avait monté pendant la nuit. Les pêcheurs restaient au bord,"
                    + " silencieux, regardant l'eau brune.",
            "Le samedi, une voiture s'arrêta devant la maison. Un homme grand en descendit, puis une jeune femme aux"
                    + " cheveux noirs.",
            "Sur le perron, Aubry les attendait, les mains jointes. Elle les embrassa l'un après l'autre sans pouvoir"
                    + " dire un seul mot.",
            "Ils déjeunèrent ensemble dans la cuisine. La jeune femme riait beaucoup, et la vieille dame se sentit plus"
                    + " légère qu'elle ne l'avait été depuis des années.",
            "Le lundi suivant, Le Courrier publia une courte nouvelle : le pont serait enfin construit. Aubry le lut"
                    + " deux fois, puis le plia avec soin.",
            "Dans la marge du journal, une main inconnue avait copié une phrase anglaise : \"We met Hessel at the"
                    + " pier. She smiled.\"",
            "Le neveu la recopia dans son carnet : \"Hessel waited by the gate. She never came back.\"",
            "Aubry la relut le soir même : \"Then Hessel left the town, and she did not look back.\"");

    private static final String CYRILLIC_PARAGRAPH = "*Здравствуйте*";

    @TempDir
    private Path tempDir;

    private record Outcome(List<GlossaryEntry> glossary, List<String> prompts) {}

    // IF Auto leaked the English default into any reader, THEN this French book would show it in the prompts, the
    // glossary or the genders.
    @Test
    void run_frenchTextWithNoMetadataUnderAuto_infersTheLanguageAndReadsItAsNoOneInParticular() {
        final Outcome outcome = translated(TestBooks.txt(tempDir.resolve("Livre.txt"), BOOK));

        assertThat(outcome.glossary()).extracting(GlossaryEntry::term).contains("Courrier", "Aubry", "Vaux");
        assertThat(outcome.glossary())
                .extracting(GlossaryEntry::term)
                .noneMatch(term -> term.equals("Le") || term.startsWith("Le "));
        assertThat(outcome.glossary()).extracting(GlossaryEntry::gender).containsOnly(Gender.UNKNOWN);
        assertThat(outcome.glossary()).noneMatch(entry -> entry.genderOrigin() == GenderOrigin.SUGGESTED);
        assertThat(outcome.prompts()).isNotEmpty();
        assertThat(outcome.prompts())
                .anySatisfy(text -> assertThat(text).contains(INFER))
                .allSatisfy(text -> assertThat(text).doesNotContain("French (fr)", "English (en)"));
    }

    // IF a prompt named the book's own declaration under Auto, THEN a book declaring fr would be told "French (fr)"
    // although the person chose nothing.
    @Test
    void run_markdownDeclaringFrenchUnderAuto_neverNamesTheDeclaredLanguageInAPrompt() {
        final Outcome outcome = translated(TestBooks.markdown(tempDir.resolve("Livre.md"), BOOK, "fr"));

        assertThat(outcome.prompts()).isNotEmpty();
        assertThat(outcome.prompts())
                .anySatisfy(text -> assertThat(text).contains(INFER))
                .allSatisfy(text -> assertThat(text).doesNotContain("French (fr)", "English (en)"));
    }

    // IF the run did not pass the book's majority script, THEN the segment's own letters would decide, and a
    // paragraph that is only a Cyrillic run would count as Cyrillic text and be sent for translation.
    @Test
    void run_segmentThatIsOnlyACyrillicRunInALatinBookUnderAuto_isNeverSentAsTranslatableText() {
        final Outcome outcome =
                translated(TestBooks.markdown(tempDir.resolve("Livre.md"), BOOK + "\n\n" + CYRILLIC_PARAGRAPH));

        assertThat(outcome.prompts()).isNotEmpty();
        // The neighbour shown as read-only context is plain text; what must never be sent is the run inside its markup.
        assertThat(outcome.prompts()).allSatisfy(text -> assertThat(text).doesNotContain("⟦g0⟧Здравствуйте"));
    }

    private Outcome translated(final Path book) {
        final Injector injector = injector();
        final ProjectService projects = injector.getInstance(ProjectService.class);
        final ImportedBook imported = dataOf(projects.importBook(book));
        final String projectId = Objects.requireNonNull(imported.projectId(), "project id");
        final BookBrief auto = Objects.requireNonNull(imported.brief(), "brief").withLanguages(null, "uk");
        dataOf(projects.updateBrief(projectId, auto));
        final List<ChatRequest> requests = new CopyOnWriteArrayList<>();

        dataOf(dataOf(injector.getInstance(TranslationEngine.class)
                        .newJob(new RunRequest(projectId, ReviewMode.UNATTENDED), recording(requests)))
                .run());

        assertThat(auto.sourceLanguage()).isNull();
        return new Outcome(
                dataOf(injector.getInstance(GlossaryRepository.class).all(projectId)),
                requests.stream().map(AutoSourceEndToEndTest::textOf).toList());
    }

    private static Injector injector() {
        return Guice.createInjector(
                new DocumentModule(),
                new LlmModule(),
                new InMemoryPersistenceModule(),
                new PipelineModule(),
                new ReviewModeTestModule());
    }

    private static ChatModel recording(final List<ChatRequest> requests) {
        final ObjectMapper mapper = new ObjectMapper();
        return request -> {
            requests.add(request);
            final var reply = mapper.createObjectNode();
            reply.put("target", "Це переклад.");
            return Result.ok(new ChatResponse(reply.toString(), FinishReason.STOP));
        };
    }

    private static String textOf(final ChatRequest request) {
        return request.messages().stream().map(ChatMessage::content).collect(Collectors.joining("\n"));
    }

    private static <T> T dataOf(final Result<T> result) {
        assertThat(result.isOk()).isTrue();
        return Objects.requireNonNull(result.data(), "result data");
    }
}
