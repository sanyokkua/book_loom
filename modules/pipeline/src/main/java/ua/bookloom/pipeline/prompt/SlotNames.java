package ua.bookloom.pipeline.prompt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The slot names a prompt template fills, written once for the enum of prompts and the names-pass slot sets. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SlotNames {

    static final String SOURCE_LANGUAGE = "sourceLanguage";
    static final String TARGET_LANGUAGE = "targetLanguage";
    static final String STYLE_SHEET = "styleSheet";
    static final String FOREIGN_PASSAGE_RULE = "foreignPassageRule";
    static final String EXAMPLES = "examples";
    static final String LANGUAGE_RULES = "languageRules";
    static final String CHARACTERS = "characters";
    static final String TERMS = "terms";
    static final String TITLE = "title";
    static final String AUTHOR = "author";
    static final String SOURCE = "source";
    static final String TOKENS = "tokens";
}
