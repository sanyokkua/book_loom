package ua.bookloom.pipeline.context;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.prompt.FrameWording;
import ua.bookloom.pipeline.prompt.LanguageRules;
import ua.bookloom.pipeline.prompt.RuleKey;

/**
 * Chooses the writing rules a run states once, in block 3, from what the book holds (design D9): a rule the book has
 * no use for is not sent, so a small model is not taught a form it never needs. The wording is the target file's,
 * never this class's.
 */
@Slf4j
public final class BookRules {

    /** The least share of the story's letters in speech for which the dialogue and address rules are sent. */
    public static final double DIALOGUE_SHARE_MIN = 0.05;

    private static final Set<RuleKey> STATED_HERE = Arrays.stream(BookRule.values())
            .map(BookRule::sectionKey)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(RuleKey.class)));

    private final LanguageRules rules;

    /**
     * Reads its wording from a language-rules map already loaded, such as the one a run's templates hold.
     *
     * @param rules the non-null language-rules map
     */
    @Inject
    public BookRules(final LanguageRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /**
     * The language-rules keys a book-rule line states, which block 5 leaves out whether or not the line is sent.
     *
     * @return never null; the keys of the dialogue, address, numbers and agreement rules
     */
    public static Set<RuleKey> sectionExclusions() {
        return Set.copyOf(STATED_HERE);
    }

    /**
     * The rule lines of one run, in the fixed order of {@link BookRule}.
     *
     * @param facts the non-null facts of the book
     * @param targetTag the non-null target language tag
     * @return the lines; never null, empty when the book needs none
     */
    public List<RuleLine> choose(final RuleFacts facts, final String targetTag) {
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(targetTag, "targetTag");
        final List<RuleLine> lines = new ArrayList<>();
        for (final BookRule rule : BookRule.values()) {
            final boolean wanted = isWanted(rule, facts);
            final String text = wanted ? FrameWording.bookRule(rules, targetTag, rule.key()) : "";
            log.debug("Book rule {} for {}: wanted={} stated={}", rule, targetTag, wanted, !text.isEmpty());
            if (!text.isEmpty()) {
                lines.add(new RuleLine(rule, text));
            }
        }
        log.debug(
                "Book rules for {}: {} (dialogue share {} against {})",
                targetTag,
                lines.stream().map(RuleLine::rule).toList(),
                facts.dialogueShare(),
                DIALOGUE_SHARE_MIN);
        return List.copyOf(lines);
    }

    private static boolean isWanted(final BookRule rule, final RuleFacts facts) {
        return switch (rule) {
            case MARKS -> facts.hasMarks();
            case DIALOGUE, ADDRESS -> facts.dialogueShare() >= DIALOGUE_SHARE_MIN;
            case NUMBERS -> facts.hasNumbers();
            case AGREEMENT -> facts.genderedCast();
            case INFLECT -> facts.targetInflects();
        };
    }
}
