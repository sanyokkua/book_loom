package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.eval.JudgementRunner.Judgement;

/**
 * How one model judged books, scored two ways: <b>agreement</b>, the share of a field's answers across seeds that are
 * the most common answer (1.0 when every seed said the same; a model that says something else on another seed is not
 * to be trusted with a person's brief), and <b>accuracy</b>, the share that match the gold. The fields are the brief's
 * genre (scored by the gold's genre class), register, narrator and narrator gender, and each main character's type and
 * gender. Voice and audience are free phrases and are not scored.
 *
 * @param suite {@code stability} or {@code gold}
 * @param model the model measured
 * @param seeds the sampling seeds, one judgement per book each
 * @param books each book's scored fields, in run order
 */
record JudgementReport(String suite, String model, List<Integer> seeds, List<Book> books) {

    /** The agreement a field must reach (15h.E3). */
    static final double TARGET = 0.8;

    private static final String ERROR = "error";
    private static final String NONE = "-";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Copies the lists. */
    JudgementReport {
        Objects.requireNonNull(suite, "suite");
        Objects.requireNonNull(model, "model");
        seeds = List.copyOf(seeds);
        books = List.copyOf(books);
    }

    /**
     * One scored field of one book.
     *
     * @param name {@code genre}, {@code register}, {@code narrator}, {@code narratorGender}, or {@code <term>:type} and
     *     {@code <term>:gender}
     * @param gold the gold answer as the report prints it
     * @param answers each seed's answer, {@code error} for a failed step and {@code -} for a removed character
     * @param correct whether each answer matches the gold
     */
    record Item(String name, String gold, List<String> answers, List<Boolean> correct) {

        /** Copies the lists. */
        Item {
            answers = List.copyOf(answers);
            correct = List.copyOf(correct);
        }

        /** The most common answer's share; 1.0 for a single answer, 0.0 for none. */
        double agreement() {
            if (answers.isEmpty()) {
                return 0.0;
            }
            final long most = answers.stream()
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .values()
                    .stream()
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0);
            return (double) most / answers.size();
        }

        /** The share of answers that match the gold. */
        double accuracy() {
            return correct.isEmpty()
                    ? 0.0
                    : (double) correct.stream().filter(Boolean::booleanValue).count() / correct.size();
        }
    }

    /**
     * One book's fields.
     *
     * @param book the book's id
     * @param calls the model calls its judgements sent
     * @param items the scored fields
     */
    record Book(String book, int calls, List<Item> items) {

        /** Copies the items. */
        Book {
            items = List.copyOf(items);
        }
    }

    /** Scores one book's judgements, one per seed, against its gold. */
    static Book score(final String book, final JudgementGold gold, final List<Judgement> judgements) {
        final List<Item> items = new ArrayList<>(briefItems(gold.brief(), judgements));
        for (final JudgementGold.Person character : gold.characters()) {
            final String term = character.term();
            items.add(entryItem(
                    term + ":type",
                    character.type().name(),
                    judgements,
                    term,
                    e -> e.type().name()));
            items.add(entryItem(
                    term + ":gender",
                    character.gender().name(),
                    judgements,
                    term,
                    e -> e.gender().name()));
        }
        return new Book(book, judgements.stream().mapToInt(Judgement::calls).sum(), items);
    }

    private static List<Item> briefItems(final JudgementGold.Brief brief, final List<Judgement> judgements) {
        return List.of(
                item(
                        "genre",
                        String.join("|", brief.genreClass()),
                        judgements,
                        suggestion -> genreAnswer(brief.genreClass(), suggestion.genre()),
                        answer -> JudgementGold.isOfClass(brief.genreClass(), answer)),
                briefItem(
                        "register",
                        brief.register().name(),
                        judgements,
                        s -> s.register().name()),
                briefItem(
                        "narrator",
                        brief.narrator().name(),
                        judgements,
                        s -> s.narrator().name()),
                briefItem(
                        "narratorGender",
                        brief.narratorGender().name(),
                        judgements,
                        s -> s.narratorGender().name()));
    }

    private static Item briefItem(
            final String name,
            final String gold,
            final List<Judgement> judgements,
            final Function<BriefSuggestion, String> answer) {
        return item(name, gold, judgements, answer, gold::equals);
    }

    private static Item item(
            final String name,
            final String gold,
            final List<Judgement> judgements,
            final Function<BriefSuggestion, String> answer,
            final Predicate<String> right) {
        final List<String> answers = judgements.stream()
                .map(judgement -> judgement.brief() == null ? ERROR : answer.apply(judgement.brief()))
                .toList();
        return new Item(
                name,
                gold,
                answers,
                answers.stream().map(a -> !ERROR.equals(a) && right.test(a)).toList());
    }

    private static Item entryItem(
            final String name,
            final String gold,
            final List<Judgement> judgements,
            final String term,
            final Function<GlossaryEntry, String> answer) {
        final List<String> answers = judgements.stream()
                .map(judgement -> entryAnswer(judgement, term, answer))
                .toList();
        return new Item(name, gold, answers, answers.stream().map(gold::equals).toList());
    }

    private static String entryAnswer(
            final Judgement judgement, final String term, final Function<GlossaryEntry, String> answer) {
        if (judgement.reviewError() != null) {
            return ERROR;
        }
        final GlossaryEntry entry = judgement.entries().get(JudgementRunner.key(term));
        return entry == null ? NONE : answer.apply(entry);
    }

    // The genre as the class word it holds, so "dark literary fiction" and "literary fiction" agree; any other genre
    // as its own lower-case words.
    static String genreAnswer(final List<String> genreClass, @Nullable final String genre) {
        if (genre == null || genre.isBlank()) {
            return NONE;
        }
        final String lower = genre.strip().toLowerCase(Locale.ROOT);
        return genreClass.stream()
                .map(word -> word.toLowerCase(Locale.ROOT))
                .filter(lower::contains)
                .findFirst()
                .orElse(lower);
    }

    /** Every item of every book. */
    List<Item> items() {
        return books.stream().flatMap(book -> book.items().stream()).toList();
    }

    /** The lowest agreement of any item; 0.0 when there are none. */
    double agreement() {
        return items().stream().mapToDouble(Item::agreement).min().orElse(0.0);
    }

    /** The mean accuracy over all items; 0.0 when there are none. */
    double accuracy() {
        return items().stream().mapToDouble(Item::accuracy).average().orElse(0.0);
    }

    /** The items, as {@code <book>:<name>}, whose agreement is below {@link #TARGET}. */
    List<String> belowTarget() {
        return books.stream()
                .flatMap(book -> book.items().stream()
                        .filter(item -> item.agreement() < TARGET)
                        .map(item -> book.book() + ":" + item.name()))
                .toList();
    }

    /** The report as one JSON object; {@code suite} tells the matrix table which suite it is. */
    String json() {
        final ObjectNode root = MAPPER.createObjectNode()
                .put("suite", suite)
                .put("model", model)
                .put("target", TARGET)
                .put("agreement", rounded(agreement()))
                .put("accuracy", rounded(accuracy()))
                .put("meetsTarget", belowTarget().isEmpty());
        seeds.forEach(root.putArray("seeds")::add);
        belowTarget().forEach(root.putArray("belowTarget")::add);
        final ArrayNode bookNodes = root.putArray("books");
        for (final Book book : books) {
            final ObjectNode node =
                    bookNodes.addObject().put("book", book.book()).put("calls", book.calls());
            final ArrayNode itemNodes = node.putArray("items");
            book.items().forEach(item -> itemJson(itemNodes.addObject(), item));
        }
        return root.toString();
    }

    private static void itemJson(final ObjectNode node, final Item item) {
        node.put("name", item.name()).put("gold", item.gold());
        item.answers().forEach(node.putArray("answers")::add);
        node.put("agreement", rounded(item.agreement())).put("accuracy", rounded(item.accuracy()));
    }

    private static double rounded(final double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    /** The table printed to the log and written beside the JSON. */
    String table() {
        final StringBuilder out = new StringBuilder(String.format(
                Locale.ROOT,
                "promptEval %s model=%s seeds=%s agreement(min)=%.2f accuracy(mean)=%.2f target=%.1f below=%d",
                suite,
                model,
                seeds,
                agreement(),
                accuracy(),
                TARGET,
                belowTarget().size()));
        for (final Book book : books) {
            out.append(String.format(Locale.ROOT, "%n%s calls=%d", book.book(), book.calls()));
            for (final Item item : book.items()) {
                out.append(String.format(
                        Locale.ROOT,
                        "%n  %-28s agree=%.2f right=%.2f gold=%s answers=%s",
                        item.name(),
                        item.agreement(),
                        item.accuracy(),
                        item.gold(),
                        item.answers()));
            }
        }
        return out.toString();
    }
}
