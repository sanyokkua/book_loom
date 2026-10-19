package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What one whole-book run decided and measured, written as {@code book-run.json}. Every count is computed by code from
 * the stored project or the exported files, never by a model.
 *
 * @param book the source book's file name
 * @param model the model every stage called
 * @param dial the quality dial of the brief
 * @param brief the brief the run translated under
 * @param end how the translation job ended
 * @param calls the model calls per stage
 * @param minutes the wall-clock minutes from import to the second export
 * @param segments the segments of the book
 * @param flaggedSegments the segments the run left flagged
 * @param characters the glossary's characters by how their gender is known
 * @param noConsistencyPass the book as exported with the consistency pass off, or null when that export failed
 * @param withConsistencyPass the book as exported after the consistency pass, or null when that export failed
 * @param errors the stages that ended in an error, as {@code stage: code}
 */
record BookRunSummary(
        String book,
        String model,
        String dial,
        BriefChoice brief,
        String end,
        Calls calls,
        double minutes,
        int segments,
        int flaggedSegments,
        Characters characters,
        @Nullable Written noConsistencyPass,
        @Nullable Written withConsistencyPass,
        List<String> errors) {

    /** The file name of the report in the output directory. */
    static final String FILE = "book-run.json";

    /** Copies the errors. */
    BookRunSummary {
        Objects.requireNonNull(book, "book");
        errors = List.copyOf(errors);
    }

    /**
     * The brief the run translated under.
     *
     * @param suggested whether the model's suggestion was accepted; false when none arrived and the defaults stand
     * @param source the source language tag
     * @param target the target language tag
     * @param genre the genre, or null
     * @param register the register
     * @param voiceEra the voice and era, or null
     * @param audience the audience, or null
     * @param names the name policy
     * @param narrator the narrator's person
     * @param narratorGender the narrator's gender
     * @param narratorGenderOrigin who decided the narrator's gender
     */
    record BriefChoice(
            boolean suggested,
            String source,
            String target,
            String genre,
            String register,
            String voiceEra,
            String audience,
            String names,
            String narrator,
            String narratorGender,
            String narratorGenderOrigin) {}

    /**
     * Model calls per stage.
     *
     * @param briefSuggestion the brief suggestion's calls
     * @param names the names pass's calls
     * @param translation the translation job's calls
     * @param consistencyPass the consistency pass's calls inside the second export
     */
    record Calls(int briefSuggestion, int names, int translation, int consistencyPass) {

        /** The calls of all stages. */
        int total() {
            return briefSuggestion + names + translation + consistencyPass;
        }
    }

    /**
     * The glossary's characters by how their gender is known.
     *
     * @param verified a gender the person set or the run verified
     * @param suggested a gender that is only a guess
     * @param none no gender
     */
    record Characters(int verified, int suggested, int none) {}

    /**
     * One exported file read back against its source.
     *
     * @param file the exported book's file name
     * @param aligned the segments of the export paired with the source's
     * @param unaligned the source segments the export does not hold
     * @param latinWords the words left in Latin letters in a target of another script, identifiers excepted
     * @param alphabetWords the words holding a letter the target language's alphabet does not have
     * @param flaggedWritten the flagged segments the export wrote with their machine target
     * @param awaitingGenderSegments the segments that wait on a character's unknown gender
     * @param detectors the segments each deterministic detector fires on, by detector name
     */
    record Written(
            String file,
            int aligned,
            int unaligned,
            int latinWords,
            int alphabetWords,
            int flaggedWritten,
            int awaitingGenderSegments,
            Map<String, Integer> detectors) {

        /** Copies the detector counts. */
        Written {
            detectors = Map.copyOf(detectors);
        }
    }
}
