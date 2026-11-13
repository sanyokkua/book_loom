package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.project.ChapterBoundaries;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.setup.BookFacts;

/**
 * The fixed part of one run's prompts, built once at run start (design D8): every translate-family call of the run
 * sends {@link #system()} byte for byte, and every analysis call sends {@link #analysis()}.
 *
 * @param brief the Book Brief the run started with
 * @param bookFacts what the book says about itself
 * @param index the book's index, which the recently-present card reads its paragraphs from
 * @param chapters the book's chapters, which place the card's window and end every chunk and batch
 * @param ruleFacts what the book holds that chooses the book rules
 * @param styleSheet the style sheet derived from the brief
 * @param rules the book rules of block 3, in order; copied
 * @param examples what block 6 shows
 * @param reminders the reminder lines in the target's words
 * @param outputRatio the pair's output tokens per source token, which sizes a translate call's output reserve; positive
 * @param system blocks 1–7 of the TRANSLATE frame, in order; copied
 * @param analysis blocks 1, 2 and 7 of the ANALYSIS frame, in order; copied
 * @param frameHash the SHA-256, lower-case hex, of {@code system}'s blocks, each written as its slot, a line break and
 *     its text, joined by a NUL character, so two frames whose texts match but whose slots differ get different
 *     hashes; it names the frame a stored prompt was sent with
 * @param firstPersonUnits the body units whose own narration is told in the first person, read once at run start so a
 *     call's card never reads its chapter again; copied, empty when the brief names the narrator or none is
 */
public record RunFrame(
        BookBrief brief,
        BookFacts bookFacts,
        BookIndex index,
        ChapterBoundaries chapters,
        RuleFacts ruleFacts,
        StyleSheet styleSheet,
        List<RuleLine> rules,
        ExampleChoice examples,
        ReminderWords reminders,
        double outputRatio,
        List<Block> system,
        List<Block> analysis,
        String frameHash,
        Set<String> firstPersonUnits) {

    /** Rejects a missing part and copies the lists. */
    public RunFrame {
        Objects.requireNonNull(brief, "brief");
        Objects.requireNonNull(bookFacts, "bookFacts");
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(chapters, "chapters");
        Objects.requireNonNull(ruleFacts, "ruleFacts");
        Objects.requireNonNull(styleSheet, "styleSheet");
        Objects.requireNonNull(examples, "examples");
        Objects.requireNonNull(reminders, "reminders");
        Objects.requireNonNull(frameHash, "frameHash");
        if (outputRatio <= 0 || Double.isNaN(outputRatio)) {
            throw new IllegalArgumentException("outputRatio must be positive: " + outputRatio);
        }
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        system = List.copyOf(Objects.requireNonNull(system, "system"));
        analysis = List.copyOf(Objects.requireNonNull(analysis, "analysis"));
        firstPersonUnits = Set.copyOf(Objects.requireNonNull(firstPersonUnits, "firstPersonUnits"));
    }

    /**
     * The reminder lines of a run, each in the target language's words, read once so a call never opens a file.
     *
     * @param marks the line on keeping the marks
     * @param dialogue the line on dialogue punctuation
     * @param narrator the line naming the narrator's gender, or empty when the person stated none
     */
    public record ReminderWords(String marks, String dialogue, String narrator) {

        /** Rejects a missing line. */
        public ReminderWords {
            Objects.requireNonNull(marks, "marks");
            Objects.requireNonNull(dialogue, "dialogue");
            Objects.requireNonNull(narrator, "narrator");
        }
    }
}
