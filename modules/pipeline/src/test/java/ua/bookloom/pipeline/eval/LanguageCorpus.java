package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;
import ua.bookloom.pipeline.eval.EvalCase.Draft;

/**
 * The mini-corpus of one target language in the eval's own formats: draft cases that a reply is measured by and
 * reviewer cases (a clean and a defective candidate each) that the judge must tell apart. Synthetic text only.
 *
 * @param sourceLanguage the source language the cases are written in
 * @param targetLanguage the target language, whose rules the cases exercise
 * @param drafts the draft cases
 * @param reviews the reviewer cases
 */
record LanguageCorpus(String sourceLanguage, String targetLanguage, List<Draft> drafts, List<DefectCase> reviews) {

    /** Rejects missing parts and copies the lists. */
    LanguageCorpus {
        Objects.requireNonNull(sourceLanguage, "sourceLanguage");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        drafts = List.copyOf(drafts);
        reviews = List.copyOf(reviews);
    }
}
