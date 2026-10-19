package ua.bookloom.api.pipeline;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;

/**
 * What the model suggests for the tone and style of a Book Brief after reading passages of the book. Every field is a
 * suggestion the person can accept or change; nothing is applied by itself.
 *
 * @param genre the genre in the model's words, or null when it named none
 * @param register the suggested register
 * @param voiceEra a short phrase on the narrator's voice and the era of the language, or null when nothing stands out
 * @param audience the likely readers, or null when none was named
 * @param narrator who tells the story
 * @param narratorGender the first-person narrator's gender, {@link Gender#UNKNOWN} unless a quote the code verified
 *     shows it (a name the narrator is called by, or a word of address)
 * @param evidence what each field stands on, with its quotes; never null, empty when the suggestion was not read from
 *     the book, and without the narrator fields once they are held to a set narrator
 * @param languageProposal the language most of the passages read are written in, as a language tag, when the brief's
 *     source language is Auto; only a proposal the person may take, never applied; null when a source is chosen or no
 *     language has a strict majority
 */
public record BriefSuggestion(
        @Nullable String genre,
        Register register,
        @Nullable String voiceEra,
        @Nullable String audience,
        NarratorPerson narrator,
        Gender narratorGender,
        Map<BriefField, FieldEvidence> evidence,
        @Nullable String languageProposal) {

    private static final Pattern THIRD_PERSON = Pattern.compile("(?iU)third[- ]person|\\bтрет\\p{L}*\\s+особ");
    private static final Pattern FIRST_PERSON = Pattern.compile("(?iU)first[- ]person|\\bперш\\p{L}*\\s+особ");

    /** Rejects a missing choice and copies the evidence. */
    public BriefSuggestion {
        Objects.requireNonNull(register, "register");
        Objects.requireNonNull(narrator, "narrator");
        Objects.requireNonNull(narratorGender, "narratorGender");
        Objects.requireNonNull(evidence, "evidence");
        evidence = Map.copyOf(evidence);
    }

    /**
     * A suggestion that proposes no source language.
     *
     * @param genre the genre, or null when none was decided
     * @param register the suggested register
     * @param voiceEra the voice and era phrase, or null
     * @param audience the likely readers, or null
     * @param narrator who tells the story
     * @param narratorGender the first-person narrator's gender
     * @param evidence what each field stands on
     */
    public BriefSuggestion(
            @Nullable String genre,
            Register register,
            @Nullable String voiceEra,
            @Nullable String audience,
            NarratorPerson narrator,
            Gender narratorGender,
            Map<BriefField, FieldEvidence> evidence) {
        this(genre, register, voiceEra, audience, narrator, narratorGender, evidence, null);
    }

    /**
     * A suggestion with no evidence behind its fields.
     *
     * @param genre the genre in the model's words, or null when it named none
     * @param register the suggested register
     * @param voiceEra a short phrase on the narrator's voice and the era of the language, or null
     * @param audience the likely readers, or null when none was named
     * @param narrator who tells the story
     * @param narratorGender the first-person narrator's gender
     */
    public BriefSuggestion(
            @Nullable String genre,
            Register register,
            @Nullable String voiceEra,
            @Nullable String audience,
            NarratorPerson narrator,
            Gender narratorGender) {
        this(genre, register, voiceEra, audience, narrator, narratorGender, Map.of());
    }

    /**
     * The evidence behind one field.
     *
     * @param field the field
     * @return the field's quotes and agreement if the samples were asked about it, or empty if not
     */
    public Optional<FieldEvidence> evidenceOf(final BriefField field) {
        Objects.requireNonNull(field, "field");
        return Optional.ofNullable(evidence.get(field));
    }

    /**
     * This suggestion held to a narrator that is already set: a person who chose the narrator, or the detector that
     * read it from the text, is never contradicted by a guess. The narrator and its gender are the set ones, and a
     * voice note that names the other grammatical person ("third-person limited" for a first-person book) is dropped.
     * One gap is filled: a first-person narrator whose gender is unknown takes the gender this suggestion holds, with
     * its evidence, because that gender stands on a quote the code verified and the screen marks it as suggested; when
     * the suggestion holds none, it takes the pending quotes it kept (15i.T5). The evidence of a field that is no
     * longer the model's goes with it.
     *
     * @param current the brief's narrator now; {@link Narrator#unspecified()} when nothing is set
     * @return this suggestion when the brief names no narrator person, else the aligned one
     */
    public BriefSuggestion alignedTo(final Narrator current) {
        Objects.requireNonNull(current, "current");
        if (current.person() == NarratorPerson.UNSPECIFIED) {
            return this;
        }
        final boolean contradicts = voiceEra != null && contradicts(current.person(), voiceEra);
        final boolean fillsGender = current.person() == NarratorPerson.FIRST
                && current.gender() == Gender.UNKNOWN
                && (narratorGender == Gender.MALE || narratorGender == Gender.FEMALE);
        final Map<BriefField, FieldEvidence> kept = new EnumMap<>(BriefField.class);
        kept.putAll(evidence);
        kept.remove(BriefField.NARRATOR);
        if (!fillsGender && !holdsPendingFor(current)) {
            kept.remove(BriefField.NARRATOR_GENDER);
        }
        if (contradicts) {
            kept.remove(BriefField.VOICE);
        }
        return new BriefSuggestion(
                genre,
                register,
                contradicts ? null : voiceEra,
                audience,
                current.person(),
                fillsGender ? narratorGender : current.gender(),
                kept,
                languageProposal);
    }

    // Pending quotes only wait for a first-person narrator whose gender nobody knows.
    private boolean holdsPendingFor(final Narrator current) {
        final FieldEvidence proof = evidence.get(BriefField.NARRATOR_GENDER);
        return current.person() == NarratorPerson.FIRST
                && current.gender() == Gender.UNKNOWN
                && narratorGender == Gender.UNKNOWN
                && proof != null
                && !proof.isKept()
                && !proof.quotes().isEmpty();
    }

    /**
     * The narrator this suggestion gives the brief. A gender the code verified from quotes of the book carries those
     * quotes and the origin {@link Narrator.GenderOrigin#VERIFIED}, so a later step can check them again and the
     * screen can tell it from the person's choice; a known gender with no such proof is only
     * {@link Narrator.GenderOrigin#SUGGESTED}, never the person's; an unknown gender has no origin, but keeps the
     * quotes that call the narrator by a name nobody knew yet, to be checked again once the glossary knows it (15i.T5).
     *
     * @return the narrator to store; never null
     */
    public Narrator suggestedNarrator() {
        final FieldEvidence proof = evidence.get(BriefField.NARRATOR_GENDER);
        final boolean known = narratorGender == Gender.MALE || narratorGender == Gender.FEMALE;
        if (!known) {
            final List<String> pending = proof == null || proof.isKept() ? List.of() : proof.quotes();
            return new Narrator(narrator, narratorGender, Narrator.GenderOrigin.NONE, pending);
        }
        if (proof == null || !proof.isKept() || proof.quotes().isEmpty()) {
            return new Narrator(narrator, narratorGender, Narrator.GenderOrigin.SUGGESTED, List.of());
        }
        return new Narrator(narrator, narratorGender, Narrator.GenderOrigin.VERIFIED, proof.quotes());
    }

    private static boolean contradicts(final NarratorPerson person, final String voice) {
        return switch (person) {
            case FIRST -> THIRD_PERSON.matcher(voice).find();
            case THIRD -> FIRST_PERSON.matcher(voice).find();
            case UNSPECIFIED -> false;
        };
    }
}
