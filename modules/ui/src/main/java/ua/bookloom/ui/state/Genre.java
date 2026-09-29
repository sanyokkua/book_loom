package ua.bookloom.ui.state;

import ua.bookloom.ui.i18n.MessageKey;

/**
 * The forty genres the Book Brief offers, in the order it lists them. A chosen genre reaches the prompt by its English
 * name whatever the interface language, while the person sees {@link #messageKey()} rendered in their own.
 */
public enum Genre {
    /** Literary fiction. */
    LITERARY_FICTION("Literary fiction", MessageKey.GENRE_LITERARY_FICTION),
    /** Classic literature. */
    CLASSIC_LITERATURE("Classic literature", MessageKey.GENRE_CLASSIC_LITERATURE),
    /** Historical fiction. */
    HISTORICAL_FICTION("Historical fiction", MessageKey.GENRE_HISTORICAL_FICTION),
    /** Gothic novel. */
    GOTHIC_NOVEL("Gothic novel", MessageKey.GENRE_GOTHIC_NOVEL),
    /** Romance. */
    ROMANCE("Romance", MessageKey.GENRE_ROMANCE),
    /** Historical romance. */
    HISTORICAL_ROMANCE("Historical romance", MessageKey.GENRE_HISTORICAL_ROMANCE),
    /** Mystery. */
    MYSTERY("Mystery", MessageKey.GENRE_MYSTERY),
    /** Detective fiction. */
    DETECTIVE_FICTION("Detective fiction", MessageKey.GENRE_DETECTIVE_FICTION),
    /** Crime fiction. */
    CRIME_FICTION("Crime fiction", MessageKey.GENRE_CRIME_FICTION),
    /** Thriller. */
    THRILLER("Thriller", MessageKey.GENRE_THRILLER),
    /** Psychological thriller. */
    PSYCHOLOGICAL_THRILLER("Psychological thriller", MessageKey.GENRE_PSYCHOLOGICAL_THRILLER),
    /** Horror. */
    HORROR("Horror", MessageKey.GENRE_HORROR),
    /** Science fiction. */
    SCIENCE_FICTION("Science fiction", MessageKey.GENRE_SCIENCE_FICTION),
    /** Fantasy. */
    FANTASY("Fantasy", MessageKey.GENRE_FANTASY),
    /** Epic fantasy. */
    EPIC_FANTASY("Epic fantasy", MessageKey.GENRE_EPIC_FANTASY),
    /** Dystopian fiction. */
    DYSTOPIAN_FICTION("Dystopian fiction", MessageKey.GENRE_DYSTOPIAN_FICTION),
    /** Adventure. */
    ADVENTURE("Adventure", MessageKey.GENRE_ADVENTURE),
    /** Western. */
    WESTERN("Western", MessageKey.GENRE_WESTERN),
    /** War fiction. */
    WAR_FICTION("War fiction", MessageKey.GENRE_WAR_FICTION),
    /** Humor. */
    HUMOR("Humor", MessageKey.GENRE_HUMOR),
    /** Satire. */
    SATIRE("Satire", MessageKey.GENRE_SATIRE),
    /** Young adult. */
    YOUNG_ADULT("Young adult", MessageKey.GENRE_YOUNG_ADULT),
    /** Children's literature. */
    CHILDRENS_LITERATURE("Children's literature", MessageKey.GENRE_CHILDRENS_LITERATURE),
    /** Fairy tale. */
    FAIRY_TALE("Fairy tale", MessageKey.GENRE_FAIRY_TALE),
    /** Short stories. */
    SHORT_STORIES("Short stories", MessageKey.GENRE_SHORT_STORIES),
    /** Poetry. */
    POETRY("Poetry", MessageKey.GENRE_POETRY),
    /** Drama. */
    DRAMA("Drama", MessageKey.GENRE_DRAMA),
    /** Memoir. */
    MEMOIR("Memoir", MessageKey.GENRE_MEMOIR),
    /** Biography. */
    BIOGRAPHY("Biography", MessageKey.GENRE_BIOGRAPHY),
    /** Autobiography. */
    AUTOBIOGRAPHY("Autobiography", MessageKey.GENRE_AUTOBIOGRAPHY),
    /** Essay. */
    ESSAY("Essay", MessageKey.GENRE_ESSAY),
    /** Travel writing. */
    TRAVEL_WRITING("Travel writing", MessageKey.GENRE_TRAVEL_WRITING),
    /** History. */
    HISTORY("History", MessageKey.GENRE_HISTORY),
    /** Philosophy. */
    PHILOSOPHY("Philosophy", MessageKey.GENRE_PHILOSOPHY),
    /** Religion and spirituality. */
    RELIGION_AND_SPIRITUALITY("Religion and spirituality", MessageKey.GENRE_RELIGION_AND_SPIRITUALITY),
    /** Popular science. */
    POPULAR_SCIENCE("Popular science", MessageKey.GENRE_POPULAR_SCIENCE),
    /** Self-help. */
    SELF_HELP("Self-help", MessageKey.GENRE_SELF_HELP),
    /** Business. */
    BUSINESS("Business", MessageKey.GENRE_BUSINESS),
    /** True crime. */
    TRUE_CRIME("True crime", MessageKey.GENRE_TRUE_CRIME),
    /** Graphic novel. */
    GRAPHIC_NOVEL("Graphic novel", MessageKey.GENRE_GRAPHIC_NOVEL);

    private final String english;
    private final MessageKey messageKey;

    Genre(final String english, final MessageKey messageKey) {
        this.english = english;
        this.messageKey = messageKey;
    }

    /**
     * The name sent to the prompt.
     *
     * @return the English genre name, identical under every interface language
     */
    public String english() {
        return english;
    }

    /**
     * The catalogue entry the person sees.
     *
     * @return the key of the genre's display name
     */
    public MessageKey messageKey() {
        return messageKey;
    }
}
