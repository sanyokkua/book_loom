package ua.bookloom.pipeline.setup;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.pipeline.FieldEvidence.Basis;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Genre;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.pipeline.setup.BriefReplies.Answer;

/**
 * One decision per brief field (15i.B4), over the verified readings of the K windows. The register and, when the
 * detector cannot tell, the narrator are kept by a strict majority of all the windows read, so a window whose answer
 * was refused counts against every value; the narrator's person is the detector's when it answers, because it counted
 * every chapter; the narrator's gender is kept from verified votes when none contradicts another, one being enough. The
 * genre, audience and voice come from the one synthesis call and stand only on a basis the code checks: two cited
 * windows whose reading of the field says the value, accepted knowledge of the book that agrees with the value, or one
 * cited window when that knowledge agrees or that one window holds every line of the story. Anything else is empty and
 * uncertain.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BriefDecision {

    // A voice note longer than this would ride along in every later prompt.
    private static final int MAX_VOICE_WORDS = 12;
    private static final String MALE = "male";
    private static final String FEMALE = "female";

    /**
     * A field's value and what it stands on.
     *
     * @param value the decided value, or null for an empty text field
     * @param evidence what the value stands on
     */
    record Decided<T extends @Nullable Object>(T value, FieldEvidence evidence) {}

    /**
     * The register by a strict majority of the windows read.
     *
     * @param windows each window's verified answers, null for a window whose reply could not be read
     * @return the register, neutral and uncertain when no value has a strict majority
     */
    static Decided<Register> register(final List<@Nullable Map<BriefField, Answer>> windows) {
        final Decided<@Nullable String> word = majority(BriefField.REGISTER, windows);
        final Register register =
                switch (word.value() == null ? "" : word.value()) {
                    case "formal" -> Register.FORMAL_LITERARY;
                    case "casual" -> Register.CASUAL;
                    default -> Register.NEUTRAL;
                };
        logged(BriefField.REGISTER, register, word.evidence());
        return new Decided<>(register, word.evidence());
    }

    /**
     * The narrator's person: the detector's when it answers, a mixed book read as first person because a first-person
     * part needs the narrator rules; else a strict majority of the windows read.
     *
     * @param detected the detector's reading of the whole book, or null when it could not tell
     * @param windows each window's verified answers, null for an unreadable one
     * @return the person, unspecified and uncertain when neither the detector nor a majority tells it
     */
    static Decided<NarratorPerson> narrator(
            final @Nullable NarratorHint detected, final List<@Nullable Map<BriefField, Answer>> windows) {
        if (detected != null) {
            final NarratorPerson person =
                    switch (detected.person()) {
                        case FIRST, MIXED -> NarratorPerson.FIRST;
                        case THIRD -> NarratorPerson.THIRD;
                    };
            log.debug("Narrator person from the detector: {} read as {}", detected.person(), person);
            final FieldEvidence measured = new FieldEvidence(Basis.MEASURED, List.of(), 0, samples(windows));
            logged(BriefField.NARRATOR, person, measured);
            return new Decided<>(person, measured);
        }
        final Decided<@Nullable String> word = majority(BriefField.NARRATOR, windows);
        final NarratorPerson person =
                switch (word.value() == null ? "" : word.value()) {
                    case "first" -> NarratorPerson.FIRST;
                    case "third" -> NarratorPerson.THIRD;
                    default -> NarratorPerson.UNSPECIFIED;
                };
        logged(BriefField.NARRATOR, person, word.evidence());
        return new Decided<>(person, word.evidence());
    }

    /**
     * The narrator's gender from the windows' verified votes. A vote is a gender a window proved by a name or a word of
     * address ({@link BriefReplies} has dropped every other); one vote is enough, two that disagree decide nothing.
     *
     * @param person the narrator's decided person; a third-person narrator has no gender to give
     * @param windows each window's verified answers, null for an unreadable one
     * @return the gender with every vote's quote, or unknown and uncertain
     */
    static Decided<Gender> narratorGender(
            final NarratorPerson person, final List<@Nullable Map<BriefField, Answer>> windows) {
        final int samples = samples(windows);
        if (person == NarratorPerson.THIRD) {
            log.debug("Narrator gender not decided: the narrator is third person");
            return new Decided<>(Gender.UNKNOWN, uncertain(0, samples));
        }
        final List<Answer> votes = answersOf(BriefField.NARRATOR_GENDER, windows).stream()
                .filter(answer -> MALE.equals(answer.value()) || FEMALE.equals(answer.value()))
                .toList();
        final long female =
                votes.stream().filter(vote -> FEMALE.equals(vote.value())).count();
        log.debug("Narrator gender votes: {} of {} windows, {} female", votes.size(), samples, female);
        if (votes.isEmpty() || (female > 0 && female < votes.size())) {
            final FieldEvidence doubt = uncertain((int) Math.max(female, votes.size() - female), samples);
            logged(BriefField.NARRATOR_GENDER, Gender.UNKNOWN, doubt);
            return new Decided<>(Gender.UNKNOWN, doubt);
        }
        final Gender gender = female > 0 ? Gender.FEMALE : Gender.MALE;
        final FieldEvidence proof = new FieldEvidence(Basis.PASSAGES, quotesOf(votes), votes.size(), samples);
        logged(BriefField.NARRATOR_GENDER, gender, proof);
        return new Decided<>(gender, proof);
    }

    /**
     * One of the synthesis call's fields, held to a basis the code checks.
     *
     * @param field {@link BriefField#GENRE}, {@link BriefField#AUDIENCE} or {@link BriefField#VOICE}
     * @param synthesis the synthesis reply, or null when the call failed or its reply could not be read
     * @param knowledge the verdict on what the model knows of the book
     * @param windows each window's verified answers, in passage order, null for an unreadable one
     * @param wholeStory whether the one window read holds every line of the story, so a single cited reading of it
     *     stands for the whole book
     * @return the value with its basis, or null and uncertain
     */
    static Decided<@Nullable String> synthesized(
            final BriefField field,
            final @Nullable JsonNode synthesis,
            final Knowledge knowledge,
            final List<@Nullable Map<BriefField, Answer>> windows,
            final boolean wholeStory) {
        final int samples = samples(windows);
        final JsonNode node = synthesis == null ? null : synthesis.get(BriefReplies.wireName(field));
        if (node == null || !node.isObject()) {
            log.debug("Synthesis gave no {}", field);
            return empty(field, uncertain(0, samples));
        }
        final String value = valid(field, node.path("value").asText(""));
        final List<Integer> cited = cited(field, value, node.path("basis").path("passages"), windows);
        if (value == null) {
            return empty(field, uncertain(cited.size(), samples));
        }
        final boolean knowledgeCited = node.path("basis").path("knowledge").asBoolean(false);
        final boolean knowledgeAgrees = KnowledgeAgreement.agrees(field, value, knowledge);
        log.debug(
                "Synthesis {}: cited passages {} of {}, knowledge cited {} agrees {}, whole story {}",
                field,
                cited,
                samples,
                knowledgeCited,
                knowledgeAgrees,
                wholeStory);
        final Basis basis = basisOf(cited.size(), wholeStory, knowledgeCited, knowledgeAgrees);
        final FieldEvidence evidence = basis == Basis.UNCERTAIN
                ? uncertain(cited.size(), samples)
                : new FieldEvidence(basis, quotesOf(field, cited, windows), cited.size(), samples);
        log.info("Brief field {} decided: kept={} {}", field, evidence.isKept(), evidence.describe());
        log.trace("Brief field {} value '{}'", field, value);
        return new Decided<>(evidence.isKept() ? value : null, evidence);
    }

    /**
     * The language most windows are written in, proposed under Auto.
     *
     * @param languages each window's language tag, null for a window that named none
     * @return the tag a strict majority of the windows name, or null when none has one
     */
    static @Nullable String languageProposal(final List<@Nullable String> languages) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (final String tag : languages) {
            if (tag != null) {
                counts.merge(tag, 1, Integer::sum);
            }
        }
        final Map.Entry<String, Integer> best =
                counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
        final String proposal = best != null && 2 * best.getValue() > languages.size() ? best.getKey() : null;
        log.info("Source language proposal: {} from {} windows, counts {}", proposal, languages.size(), counts);
        return proposal;
    }

    private static Basis basisOf(
            final int cited, final boolean wholeStory, final boolean knowledgeCited, final boolean knowledgeAgrees) {
        if (cited >= 2) {
            return Basis.PASSAGES;
        }
        if (knowledgeAgrees && (knowledgeCited || cited == 1)) {
            return Basis.KNOWLEDGE;
        }
        return cited == 1 && wholeStory ? Basis.PASSAGES : Basis.UNCERTAIN;
    }

    // The value in the brief's own words, or null for an empty one or one outside the field's list or length.
    private static @Nullable String valid(final BriefField field, final String raw) {
        final String value = raw.strip();
        final String kept =
                switch (field) {
                    case GENRE -> Genre.ofEnglish(value).map(Genre::english).orElse(null);
                    case AUDIENCE -> {
                        final String audience = value.toLowerCase(Locale.ROOT);
                        yield BriefSynthesisSchema.AUDIENCES.contains(audience) ? audience : null;
                    }
                    case VOICE -> value.isEmpty() || value.split("\\s+").length > MAX_VOICE_WORDS ? null : value;
                    case REGISTER, NARRATOR, NARRATOR_GENDER ->
                        throw new IllegalArgumentException("not a synthesized field: " + field);
                };
        log.debug("Synthesis {} value valid: {}", field, kept != null);
        return kept;
    }

    // The windows the basis cites, each once and only when it exists, read the field and read what the value says
    // (a window that read "fantasy" cannot support "Thriller"); numbered from one. With no valid value there is
    // nothing to compare, so the evidence of an empty field still counts the windows that read the field.
    private static List<Integer> cited(
            final BriefField field,
            final @Nullable String value,
            final JsonNode passages,
            final List<@Nullable Map<BriefField, Answer>> windows) {
        final TreeSet<Integer> kept = new TreeSet<>();
        passages.forEach(number -> {
            final int passage = number.asInt(0);
            if (passage >= 1 && passage <= windows.size() && says(field, value, windows.get(passage - 1), passage)) {
                kept.add(passage);
            }
        });
        return List.copyOf(kept);
    }

    private static boolean says(
            final BriefField field,
            final @Nullable String value,
            final @Nullable Map<BriefField, Answer> window,
            final int passage) {
        final Answer reading = readingOf(field, window);
        if (reading == null) {
            return false;
        }
        final boolean agrees = value == null || KnowledgeAgreement.saysSame(field, value, reading.value());
        log.debug("Synthesis {}: cited passage {} agrees with the value: {}", field, passage, agrees);
        log.trace("Synthesis {}: cited passage {} reads '{}'", field, passage, reading.value());
        return agrees;
    }

    private static @Nullable Answer readingOf(final BriefField field, final @Nullable Map<BriefField, Answer> window) {
        final Answer answer = window == null ? null : window.get(field);
        return answer == null || answer.isNeutral() ? null : answer;
    }

    private static List<String> quotesOf(
            final BriefField field, final List<Integer> cited, final List<@Nullable Map<BriefField, Answer>> windows) {
        return quotesOf(cited.stream()
                .map(passage -> readingOf(field, windows.get(passage - 1)))
                .filter(Objects::nonNull)
                .toList());
    }

    private static List<String> quotesOf(final List<Answer> answers) {
        return answers.stream()
                .map(Answer::quote)
                .filter(quote -> !quote.isBlank())
                .distinct()
                .toList();
    }

    private static Decided<@Nullable String> majority(
            final BriefField field, final List<@Nullable Map<BriefField, Answer>> windows) {
        final Map<String, List<Answer>> byValue = new LinkedHashMap<>();
        answersOf(field, windows)
                .forEach(answer -> byValue.computeIfAbsent(answer.value(), value -> new ArrayList<>())
                        .add(answer));
        final int samples = samples(windows);
        final Map.Entry<String, List<Answer>> best = byValue.entrySet().stream()
                .max((one, other) ->
                        Integer.compare(one.getValue().size(), other.getValue().size()))
                .orElse(null);
        log.debug("Brief field {} answers by value {} of {} windows", field, byValue.keySet(), samples);
        if (best == null || 2 * best.getValue().size() <= samples) {
            return new Decided<>(
                    null, uncertain(best == null ? 0 : best.getValue().size(), samples));
        }
        final List<Answer> agreeing = best.getValue();
        return new Decided<>(
                best.getKey(), new FieldEvidence(Basis.PASSAGES, quotesOf(agreeing), agreeing.size(), samples));
    }

    // Every window's answer to the field, in passage order; an unreadable window and one with no answer give none.
    private static List<Answer> answersOf(
            final BriefField field, final List<@Nullable Map<BriefField, Answer>> windows) {
        final List<Answer> answers = new ArrayList<>();
        for (final Map<BriefField, Answer> window : windows) {
            final Answer answer = window == null ? null : window.get(field);
            if (answer != null) {
                answers.add(answer);
            }
        }
        return answers;
    }

    private static Decided<@Nullable String> empty(final BriefField field, final FieldEvidence evidence) {
        log.info("Brief field {} decided: empty, {}", field, evidence.describe());
        return new Decided<>(null, evidence);
    }

    private static void logged(final BriefField field, final Object value, final FieldEvidence evidence) {
        log.info("Brief field {} decided: {} {}", field, value, evidence.describe());
    }

    private static FieldEvidence uncertain(final int agreeing, final int samples) {
        return new FieldEvidence(Basis.UNCERTAIN, List.of(), Math.min(agreeing, samples), samples);
    }

    private static int samples(final List<?> windows) {
        return Math.max(1, windows.size());
    }
}
