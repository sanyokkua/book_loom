package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.narrator.DialogueShare;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * What the book holds that decides which book rules a run states (design D9), computed once at run start from the
 * story. The existing {@code setup.BookFacts} (what the book says about itself) is a different record, kept beside it.
 *
 * @param hasMarks whether a story segment's masked text holds a {@code ⟦gN⟧} mark
 * @param dialogueShare the share of the story's letters inside speech, from 0 to 1
 * @param hasNumbers whether the story's text holds a digit
 * @param genderedCast whether a gender is known for the narrator or a character, and the target's file sets gendered
 *     past forms, so agreement is something the model can get wrong
 * @param targetInflects whether the target language declines proper names
 */
@Slf4j
public record RuleFacts(
        boolean hasMarks, double dialogueShare, boolean hasNumbers, boolean genderedCast, boolean targetInflects) {

    private static final String MARK = "⟦g";
    private static final String PAST_FEMALE = "pastFemale";

    /** Rejects a share outside 0 to 1. */
    public RuleFacts {
        if (dialogueShare < 0 || dialogueShare > 1) {
            throw new IllegalArgumentException("dialogueShare must be within [0,1], but was " + dialogueShare);
        }
    }

    /**
     * Reads the facts of a book for a run.
     *
     * @param index the non-null index of the book, which names its story segments
     * @param document the non-null book, whose masked texts hold the marks
     * @param brief the non-null Book Brief; its target language must be chosen
     * @param map the non-null book map, whose characters may carry a gender
     * @param rules the non-null language-rules map
     * @return the facts; never null
     */
    public static RuleFacts of(
            final BookIndex index,
            final Document document,
            final BookBrief brief,
            final BookMap map,
            final LanguageRules rules) {
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(rules, "rules");
        final String target = Objects.requireNonNull(brief.targetLanguage(), "brief.targetLanguage");
        final List<Integer> story = index.story();
        final List<String> texts = index.visibleTexts(story);
        final RuleFacts facts = new RuleFacts(
                hasMarks(index, document, story),
                DialogueShare.of(texts, brief.sourceLanguage()).fraction(),
                texts.stream().anyMatch(text -> text.chars().anyMatch(Character::isDigit)),
                hasGender(brief, map) && !rules.wordsOf(target, PAST_FEMALE).isEmpty(),
                rules.namesInflect(target));
        log.debug(
                "Rule facts of {}: story={} marks={} dialogueShare={} numbers={} genderedCast={} targetInflects={}",
                document.id(),
                story.size(),
                facts.hasMarks(),
                facts.dialogueShare(),
                facts.hasNumbers(),
                facts.genderedCast(),
                facts.targetInflects());
        return facts;
    }

    // The index reads tokens as spaces, so the marks are looked for in the document's masked texts.
    private static boolean hasMarks(final BookIndex index, final Document document, final List<Integer> story) {
        final Map<String, Segment> byId = document.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .collect(Collectors.toMap(Segment::id, Function.identity(), (first, ignored) -> first));
        return story.stream()
                .map(ordinal -> byId.get(index.segment(ordinal).segmentId()))
                .anyMatch(segment -> segment != null && segment.masked().contains(MARK));
    }

    private static boolean hasGender(final BookBrief brief, final BookMap map) {
        return isGendered(brief.narrator().gender())
                || map.entries().stream()
                        .anyMatch(entry -> entry.type() == TermType.CHARACTER && isGendered(entry.gender()));
    }

    private static boolean isGendered(final Gender gender) {
        return gender == Gender.MALE || gender == Gender.FEMALE;
    }
}
