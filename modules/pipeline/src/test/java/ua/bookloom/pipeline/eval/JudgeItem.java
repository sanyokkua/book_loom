package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * What the judge sees of one case besides the translations: the source, the language pair, the gold terms the case
 * uses and the neighbouring paragraphs in the source language. It holds nothing about which arm or run wrote a
 * translation, so the judge cannot learn it.
 *
 * @param caseId the case's id, such as {@code ch01.md:3}; never shown to the judge
 * @param sourceLanguage the source language tag
 * @param targetLanguage the target language tag
 * @param source the judged source text
 * @param goldTerms the gold excerpt: the required renderings of the terms the source uses; empty when none
 * @param neighbours the previous and next paragraphs in the source language, context only; empty when none
 */
record JudgeItem(
        String caseId,
        String sourceLanguage,
        String targetLanguage,
        String source,
        List<GoldTerm> goldTerms,
        List<String> neighbours) {

    /**
     * A required rendering.
     *
     * @param source the term as the source writes it
     * @param target its required rendering
     */
    record GoldTerm(String source, String target) {

        /** Rejects a missing part. */
        GoldTerm {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
        }
    }

    /** Rejects a missing part and copies the lists. */
    JudgeItem {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(sourceLanguage, "sourceLanguage");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        Objects.requireNonNull(source, "source");
        goldTerms = List.copyOf(goldTerms);
        neighbours = List.copyOf(neighbours);
    }

    /** The user message of a single translation's judgement. */
    String pointwise(final String translation) {
        Objects.requireNonNull(translation, "translation");
        return context() + "\n\nTranslation:\n<<<\n" + translation + "\n>>>";
    }

    /** The user message of a comparison, {@code a} shown as A and {@code b} as B. */
    String pairwise(final String a, final String b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        return context() + "\n\nTranslation A:\n<<<\n" + a + "\n>>>\n\nTranslation B:\n<<<\n" + b + "\n>>>";
    }

    private String context() {
        final String terms = goldTerms.isEmpty()
                ? "(none)"
                : goldTerms.stream()
                        .map(t -> "- " + t.source() + " → " + t.target())
                        .collect(Collectors.joining("\n"));
        final String around = neighbours.isEmpty()
                ? "(none)"
                : neighbours.stream().map(p -> "- " + p).collect(Collectors.joining("\n"));
        return "Language pair: " + sourceLanguage + " → " + targetLanguage
                + "\n\nGlossary (source → required rendering):\n" + terms
                + "\n\nNeighbouring paragraphs (source language, context only, not judged):\n" + around
                + "\n\nSource:\n<<<\n" + source + "\n>>>";
    }
}
