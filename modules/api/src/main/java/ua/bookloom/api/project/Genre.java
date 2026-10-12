package ua.bookloom.api.project;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The forty genres the Book Brief offers, in the order it lists them. They live in {@code :api} so the brief's
 * synthesis can hold a model's genre to exactly these names (15i.B4) while the interface shows each in its own
 * language; the brief stores the English name whatever the interface language, and free text stays free text.
 */
public enum Genre {
    /** Literary fiction. */
    LITERARY_FICTION("Literary fiction"),
    /** Classic literature. */
    CLASSIC_LITERATURE("Classic literature"),
    /** Historical fiction. */
    HISTORICAL_FICTION("Historical fiction"),
    /** Gothic novel. */
    GOTHIC_NOVEL("Gothic novel"),
    /** Romance. */
    ROMANCE("Romance"),
    /** Historical romance. */
    HISTORICAL_ROMANCE("Historical romance"),
    /** Mystery. */
    MYSTERY("Mystery"),
    /** Detective fiction. */
    DETECTIVE_FICTION("Detective fiction"),
    /** Crime fiction. */
    CRIME_FICTION("Crime fiction"),
    /** Thriller. */
    THRILLER("Thriller"),
    /** Psychological thriller. */
    PSYCHOLOGICAL_THRILLER("Psychological thriller"),
    /** Horror. */
    HORROR("Horror"),
    /** Science fiction. */
    SCIENCE_FICTION("Science fiction"),
    /** Fantasy. */
    FANTASY("Fantasy"),
    /** Epic fantasy. */
    EPIC_FANTASY("Epic fantasy"),
    /** Dystopian fiction. */
    DYSTOPIAN_FICTION("Dystopian fiction"),
    /** Adventure. */
    ADVENTURE("Adventure"),
    /** Western. */
    WESTERN("Western"),
    /** War fiction. */
    WAR_FICTION("War fiction"),
    /** Humor. */
    HUMOR("Humor"),
    /** Satire. */
    SATIRE("Satire"),
    /** Young adult. */
    YOUNG_ADULT("Young adult"),
    /** Children's literature. */
    CHILDRENS_LITERATURE("Children's literature"),
    /** Fairy tale. */
    FAIRY_TALE("Fairy tale"),
    /** Short stories. */
    SHORT_STORIES("Short stories"),
    /** Poetry. */
    POETRY("Poetry"),
    /** Drama. */
    DRAMA("Drama"),
    /** Memoir. */
    MEMOIR("Memoir"),
    /** Biography. */
    BIOGRAPHY("Biography"),
    /** Autobiography. */
    AUTOBIOGRAPHY("Autobiography"),
    /** Essay. */
    ESSAY("Essay"),
    /** Travel writing. */
    TRAVEL_WRITING("Travel writing"),
    /** History. */
    HISTORY("History"),
    /** Philosophy. */
    PHILOSOPHY("Philosophy"),
    /** Religion and spirituality. */
    RELIGION_AND_SPIRITUALITY("Religion and spirituality"),
    /** Popular science. */
    POPULAR_SCIENCE("Popular science"),
    /** Self-help. */
    SELF_HELP("Self-help"),
    /** Business. */
    BUSINESS("Business"),
    /** True crime. */
    TRUE_CRIME("True crime"),
    /** Graphic novel. */
    GRAPHIC_NOVEL("Graphic novel");

    private final String english;

    Genre(final String english) {
        this.english = english;
    }

    /**
     * The name a prompt and the stored brief use.
     *
     * @return the English genre name, identical under every interface language
     */
    public String english() {
        return english;
    }

    /**
     * The genre a text names, forgiving letter case and the spaces around it.
     *
     * @param text a model's or a person's genre; never null
     * @return the genre whose English name the text is, or empty if it names none of the forty
     */
    public static Optional<Genre> ofEnglish(final String text) {
        Objects.requireNonNull(text, "text");
        final String wanted = text.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(genre -> genre.english.toLowerCase(Locale.ROOT).equals(wanted))
                .findFirst();
    }

    /**
     * Turns what the genre box shows into what the brief holds.
     *
     * @param shown the box's text; never null
     * @param displayName how a genre is named for the person
     * @return the English name when the text is a genre's display name, ignoring case; otherwise the text as written
     */
    public static String toStored(final String shown, final Function<Genre, String> displayName) {
        Objects.requireNonNull(shown, "shown");
        Objects.requireNonNull(displayName, "displayName");
        return Arrays.stream(values())
                .filter(genre -> displayName.apply(genre).equalsIgnoreCase(shown.strip()))
                .map(Genre::english)
                .findFirst()
                .orElse(shown);
    }

    /**
     * Turns what the brief holds into what the genre box shows.
     *
     * @param stored the brief's genre; never null
     * @param displayName how a genre is named for the person
     * @return the display name when the text is a genre's English name, otherwise the text as written
     */
    public static String toShown(final String stored, final Function<Genre, String> displayName) {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(displayName, "displayName");
        return Arrays.stream(values())
                .filter(genre -> genre.english.equals(stored))
                .map(displayName)
                .findFirst()
                .orElse(stored);
    }
}
