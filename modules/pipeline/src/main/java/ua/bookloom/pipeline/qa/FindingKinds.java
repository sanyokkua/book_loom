package ua.bookloom.pipeline.qa;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The finding kinds a check raises, written once for the enum of checks. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FindingKinds {

    static final String LANGUAGE = "language";
    static final String FLUENCY = "fluency";
    static final String OMISSION = "omission";
    static final String GLOSSARY = "glossary";
    static final String MEANING = "meaning";
    static final String MARKUP = "markup";
    static final String GENDER = "gender";
}
