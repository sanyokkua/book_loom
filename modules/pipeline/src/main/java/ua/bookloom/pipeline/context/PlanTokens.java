package ua.bookloom.pipeline.context;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.pipeline.chunk.TokenEstimator;

/** The context builder's estimate of a plan's prompt, each block counted at the rate of the language it is in. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PlanTokens {

    // The block wording is English; the style sheet, the language rules, the examples and the map are in the target,
    // and the window and the items are the book's text.
    private static final String INSTRUCTIONS_LANGUAGE = "en";

    // Every block at its own language's rate: instructions in English, the rules, the map and a repair's attempt in the
    // target, the book's text in the source, the previous pairs at the mixed rate; the template's overhead once.
    static int of(final List<Block> blocks, final RunFrame frame, final ModelProfile profile) {
        final List<TokenEstimator.Message> messages = blocks.stream()
                .map(block -> new TokenEstimator.Message(block.text(), languageOf(block, frame)))
                .toList();
        return TokenEstimator.estimateCall(messages, profile.templateOverhead());
    }

    private static @Nullable String languageOf(final Block block, final RunFrame frame) {
        return switch (block.slot()) {
            case BlockSlots.STYLE_SHEET,
                    BlockSlots.LANGUAGE_RULES,
                    BlockSlots.EXAMPLES,
                    BlockSlots.MAP,
                    BlockSlots.KIND_EXTRA -> frame.brief().targetLanguage();
            case BlockSlots.ITEMS -> frame.brief().sourceLanguage();
            // The card names the book's own words; the summaries before it are in the target, so the mixed rate.
            case BlockSlots.SCENE ->
                EarlierChapters.opensBlock(block.text()) ? null : frame.brief().sourceLanguage();
            // The previous pairs hold the source and its kept translation, so the cautious mixed-script rate.
            case BlockSlots.WINDOW -> null;
            default -> INSTRUCTIONS_LANGUAGE;
        };
    }
}
