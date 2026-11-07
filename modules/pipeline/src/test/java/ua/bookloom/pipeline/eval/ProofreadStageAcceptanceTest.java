package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Prepared;
import ua.bookloom.pipeline.WireMockProvider;

/**
 * Group 13's acceptance (design D15, D24): the Proofread stage inside every run, on mini-ru. Written red in task 13.1.
 * Verdicts are read from the stored record's JSON ({@code proofread.outcome}, {@code proofread.reason}), the shape
 * task 13.2 gives {@code SegmentRecord}, so this class compiles before those types exist. A proofread request is the
 * one whose body carries the schema title {@code "proofread"} (task 13.5), the way the chapter-summary call carries
 * {@code "chapter_summary"}.
 */
@Tag("slow")
class ProofreadStageAcceptanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final String PROOFREAD_MARK = "\"proofread\"";
    private static final String UNCHANGED = "{\"unchanged\":true}";
    private static final String TRAP_ID = "ch02.md:14";
    private static final String GENDER_ID = "ch02.md:16";
    private static final String TRAP_TARGET = "Мирена кивнула й сказала, що ключ у Тарна.";
    private static final String TRAP_REWRITE = "Мирена кивнула й сказала, что ключ у Тарна.";
    private static final String MALE_TARGET = "Івар сказав, що повернеться.";
    private static final String FEMALE_TARGET = "Івар сказала, що повернеться.";
    private static final String SOURCE_NAME = "Ивар";
    private static final String GENDER_CHANGED = "GENDER_CHANGED";
    private static final Pattern ITEM = Pattern.compile("<s id=\"([^\"]+)\">(.*?)</s>", Pattern.DOTALL);
    private static final Pattern TOKEN = Pattern.compile("⟦[^⟧]*⟧");

    /** The book's drafts, by block id: invented, and clean under every check, so only Proofread can act on them. */
    private static final Map<String, String> DRAFTS = Map.ofEntries(
            Map.entry("ch01.md:1", "4. Нічний перевал"),
            Map.entry(
                    "ch01.md:3",
                    "Я повернулася пізно. Я довго стояла біля вікна й слухала вітер, а потім я лягла, але так і не заснула."),
            Map.entry("ch01.md:5", "— Поклич Веломира, — сказала вона."),
            Map.entry(
                    "ch01.md:7",
                    "Ключ Веломира лежав на столі. Веломир повернувся пізно. Він зняв мокрий плащ, і Веломир довго мовчав біля вогню."),
            Map.entry("ch01.md:9", "— Двері були відчинені, — тихо сказав він, — і світло горіло до ранку."),
            Map.entry("ch01.md:11", "Ні, не сьогодні."),
            Map.entry("ch01.md:13", "Вона сказала, що це лише початок, і більше нічого не додала."),
            Map.entry("ch01.md:15", "На воротах висів старий замок, і ніхто не пам'ятав, де ключ."),
            Map.entry("ch01.md:17", "Ніхто не прийшов."),
            Map.entry("ch02.md:2", "5. Ранковий обхід"),
            Map.entry(
                    "ch02.md:4",
                    "— Ти запізнився, — сказав Тарн. — Дорогу закрили, ти ж знаєш, — відповів Ігнат. — Я чекав на тебе цілу годину."),
            Map.entry("ch02.md:6", "Олексій поклав ключ на підвіконня й пішов."),
            Map.entry(
                    "ch02.md:8",
                    "— Хто тут? — спитав Тарн. — Це я, — відповіла Мирена. — А я просто повз проходив, — буркнув Ігнат."),
            Map.entry(
                    "ch02.md:10",
                    "— Так! — вигукнула Мирена й розчинила вікно. Мирена довго дивилася на порожню дорогу."),
            Map.entry(
                    "ch02.md:12",
                    "Лист Рудакової знайшли вранці. Сама Рудакова ще спала. Рудакова прокинулася опівдні, і вона одразу спитала про лист."),
            Map.entry(TRAP_ID, TRAP_TARGET),
            Map.entry(GENDER_ID, MALE_TARGET));

    @TempDir
    Path directory;

    // (a) The trap: the accepted line is clean, so no mark sends it to Proofread; Max's second round reads it anyway.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_maxCleanSegmentRewrittenWithRussianWord_textKeptAndVerdictRejected(final ProviderKind kind) {
        final MiniBook book = MiniBook.load(MiniBook.MINI_RU);
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            final Prepared prepared = prepare(book, provider, QualityDial.MAX);

            run(prepared, provider);

            final SegmentRecord trap = record(prepared, TRAP_ID);
            assertThat(trap.maskedMachineTarget()).as("the draft stands").isEqualTo(TRAP_TARGET);
            assertThat(proofreadRequestsReading(provider, TRAP_TARGET))
                    .as("Max's second round read the trap's window")
                    .isNotEmpty();
            final JsonNode verdict = MAPPER.valueToTree(trap).path("proofread");
            assertThat(verdict.path("outcome").asText()).as("stored verdict").isEqualTo("REJECTED");
            assertThat(verdict.path("reason").asText()).contains("что");
        }
    }

    // (b) A gender the person sets after the run is applied on Continue by one proofread window.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void continue_balancedGenderChangedAfterRun_windowRewritesAndDeferralSettled(final ProviderKind kind) {
        final MiniBook book = MiniBook.load(MiniBook.MINI_RU);
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            final Prepared prepared = prepare(book, provider, QualityDial.BALANCED);
            run(prepared, provider);
            assertThat(record(prepared, GENDER_ID).maskedMachineTarget())
                    .as("decided before the change")
                    .isEqualTo(MALE_TARGET);

            setIvarFemale(prepared);
            run(prepared, provider);

            assertThat(proofreadRequestsReading(provider, MALE_TARGET))
                    .as("one window reads the decided line")
                    .hasSize(1);
            final SegmentRecord line = record(prepared, GENDER_ID);
            assertThat(line.maskedMachineTarget()).isEqualTo(FEMALE_TARGET);
            assertThat(MAPPER.valueToTree(line)
                            .path("proofread")
                            .path("outcome")
                            .asText())
                    .isEqualTo("KEPT");
            assertThat(openDeferralsOf(prepared, GENDER_ID))
                    .as("no deferral whose fact has arrived stays open")
                    .isEmpty();
        }
    }

    // (c) Fast makes no proofread call; the segment waits on the gender for the person.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void continue_fastGenderChangedAfterRun_noProofreadRequestAndGenderDeferralOpen(final ProviderKind kind) {
        final MiniBook book = MiniBook.load(MiniBook.MINI_RU);
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            final Prepared prepared = prepare(book, provider, QualityDial.FAST);
            run(prepared, provider);

            setIvarFemale(prepared);
            run(prepared, provider);

            assertThat(provider.chatBodies())
                    .as("a Fast run sends no request in the proofread format")
                    .noneMatch(body -> body.contains(PROOFREAD_MARK));
            assertThat(record(prepared, GENDER_ID).maskedMachineTarget()).isEqualTo(MALE_TARGET);
            assertThat(openDeferralsOf(prepared, GENDER_ID))
                    .as("'Waiting on a gender' holds the segment")
                    .extracting(deferral -> deferral.reason().name())
                    .contains(GENDER_CHANGED);
        }
    }

    private Prepared prepare(final MiniBook book, final WireMockProvider provider, final QualityDial dial) {
        final MiniRunner runner = new MiniRunner(book, dial, NamePolicy.TRANSLITERATE, null);
        final Prepared prepared = SequenceJobs.importBook(book.writeEpub(directory), runner.brief());
        provider.stubResponder(body -> replyTo(body, segmentIds(prepared, book)));
        seedIvar(prepared);
        return prepared;
    }

    private static void run(final Prepared prepared, final WireMockProvider provider) {
        final ChatModel model = provider.model(REQUEST_TIMEOUT, ignored -> {});
        SequenceJobs.run(prepared, model, ReviewMode.UNATTENDED, null, e -> {});
    }

    // The entry of the source name, male because the book says "сказал" after it.
    private static void seedIvar(final Prepared prepared) {
        final GlossaryEntry entry = GlossaryEntry.of(
                        prepared.projectId() + ":" + SOURCE_NAME,
                        prepared.projectId(),
                        SOURCE_NAME,
                        "Івар",
                        TermType.CHARACTER,
                        Gender.UNKNOWN,
                        false)
                .withInferredGender(Gender.MALE);
        Objects.requireNonNull(prepared.stores().glossary().add(entry).data(), "seeded");
    }

    // The person's value, through the production service, so the deferral producer sees the change.
    private static void setIvarFemale(final Prepared prepared) {
        final GlossaryEntry held = Objects.requireNonNull(
                        prepared.stores()
                                .glossary()
                                .findByTerm(prepared.projectId(), SOURCE_NAME)
                                .data(),
                        "find")
                .orElseThrow();
        Objects.requireNonNull(
                SequenceJobs.names(prepared)
                        .glossary()
                        .update(held.withGender(Gender.FEMALE))
                        .data(),
                "updated");
    }

    private static List<Deferral> openDeferralsOf(final Prepared prepared, final String blockId) {
        final String segmentId =
                segmentIds(prepared, MiniBook.load(MiniBook.MINI_RU)).get(blockId);
        return Objects.requireNonNull(
                        prepared.deferrals().open(prepared.projectId()).data(), "open deferrals")
                .stream()
                .filter(deferral -> deferral.segmentId().equals(segmentId))
                .toList();
    }

    private static SegmentRecord record(final Prepared prepared, final String blockId) {
        final String segmentId =
                segmentIds(prepared, MiniBook.load(MiniBook.MINI_RU)).get(blockId);
        return Objects.requireNonNull(
                        prepared.stores().segments().all(prepared.projectId()).data(), "records")
                .stream()
                .filter(record -> record.segmentId().equals(segmentId))
                .findFirst()
                .orElseThrow();
    }

    /** The imported segment id of each block, matched by text as the mini runner does. */
    private static Map<String, String> segmentIds(final Prepared prepared, final MiniBook book) {
        final Map<String, String> byText = new LinkedHashMap<>();
        prepared.document()
                .units()
                .forEach(unit -> unit.segments()
                        .forEach(segment -> byText.put(
                                TOKEN.matcher(segment.masked()).replaceAll("").strip(), segment.id())));
        final Map<String, String> ids = new LinkedHashMap<>();
        book.blocks().forEach(block -> ids.put(block.id(), byText.get(block.text())));
        return ids;
    }

    private static List<String> proofreadRequestsReading(final WireMockProvider provider, final String text) {
        return provider.chatBodies().stream()
                .filter(body -> body.contains(PROOFREAD_MARK))
                .filter(body -> userMessage(body).contains(text))
                .toList();
    }

    // A draft batch gets its canned targets; a proofread window answers unchanged but for the two scripted rewrites;
    // anything else (summary, reviewer, audit) gets the empty answer the other acceptance tests use.
    private static String replyTo(final String body, final Map<String, String> ids) {
        final String message = userMessage(body);
        if (body.contains(PROOFREAD_MARK)) {
            return proofreadReply(message, ids);
        }
        final Matcher matcher = ITEM.matcher(message);
        final List<Map<String, String>> items = new ArrayList<>();
        while (matcher.find()) {
            final String target = draftFor(matcher.group(2));
            if (target != null) {
                items.add(Map.of("id", matcher.group(1), "target", target));
            }
        }
        return items.isEmpty() ? "{\"results\":[]}" : write(Map.of("items", items));
    }

    private static String proofreadReply(final String message, final Map<String, String> ids) {
        if (message.contains(TRAP_TARGET)) {
            return write(Map.of("items", List.of(Map.of("id", ids.get(TRAP_ID), "target", TRAP_REWRITE))));
        }
        if (message.contains(MALE_TARGET)) {
            return write(Map.of("items", List.of(Map.of("id", ids.get(GENDER_ID), "target", FEMALE_TARGET))));
        }
        return UNCHANGED;
    }

    private static String draftFor(final String source) {
        final MiniBook book = MiniBook.load(MiniBook.MINI_RU);
        return book.blocks().stream()
                .filter(block -> TOKEN.matcher(source).replaceAll("").strip().equals(block.text()))
                .map(block -> DRAFTS.get(block.id()))
                .findFirst()
                .orElse(null);
    }

    /** The user message of a chat request body, the same field in both dialects. */
    private static String userMessage(final String body) {
        try {
            return MAPPER.readTree(body)
                    .path("messages")
                    .path(1)
                    .path("content")
                    .asText();
        } catch (JsonProcessingException cause) {
            throw new AssertionError("could not read request body", cause);
        }
    }

    private static String write(final Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException cause) {
            throw new AssertionError("could not encode reply", cause);
        }
    }
}
