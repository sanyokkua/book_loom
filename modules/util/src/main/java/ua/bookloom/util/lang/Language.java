package ua.bookloom.util.lang;

import java.util.Objects;

/**
 * The 34 languages the Book Brief offers as source or target, each carrying its BCP-47 tag, its English display
 * name and its {@link Script}.
 *
 * <p>Declaration order is {@link Languages#all()}'s order — the same order the Book Brief's searchable list shows
 * before task 11.8 layers ICU-localized names over it.
 */
public enum Language {

    /** English. */
    EN("en", "English", Script.LATIN),

    /** Bulgarian, written in Cyrillic. */
    BG("bg", "Bulgarian", Script.CYRILLIC),

    /** Croatian. */
    HR("hr", "Croatian", Script.LATIN),

    /** Czech. */
    CS("cs", "Czech", Script.LATIN),

    /** Danish. */
    DA("da", "Danish", Script.LATIN),

    /** Dutch. */
    NL("nl", "Dutch", Script.LATIN),

    /** Estonian. */
    ET("et", "Estonian", Script.LATIN),

    /** Finnish. */
    FI("fi", "Finnish", Script.LATIN),

    /** French. */
    FR("fr", "French", Script.LATIN),

    /** German. */
    DE("de", "German", Script.LATIN),

    /** Greek, written in the Greek alphabet. */
    EL("el", "Greek", Script.GREEK),

    /** Hungarian. */
    HU("hu", "Hungarian", Script.LATIN),

    /** Irish. */
    GA("ga", "Irish", Script.LATIN),

    /** Italian. */
    IT("it", "Italian", Script.LATIN),

    /** Latvian. */
    LV("lv", "Latvian", Script.LATIN),

    /** Lithuanian. */
    LT("lt", "Lithuanian", Script.LATIN),

    /** Maltese. */
    MT("mt", "Maltese", Script.LATIN),

    /** Polish. */
    PL("pl", "Polish", Script.LATIN),

    /** Portuguese. */
    PT("pt", "Portuguese", Script.LATIN),

    /** Romanian. */
    RO("ro", "Romanian", Script.LATIN),

    /** Slovak. */
    SK("sk", "Slovak", Script.LATIN),

    /** Slovenian. */
    SL("sl", "Slovenian", Script.LATIN),

    /** Spanish. */
    ES("es", "Spanish", Script.LATIN),

    /** Swedish. */
    SV("sv", "Swedish", Script.LATIN),

    /** Chinese written with Simplified characters. */
    ZH_HANS("zh-Hans", "Chinese (Simplified)", Script.HAN),

    /** Chinese written with Traditional characters. */
    ZH_HANT("zh-Hant", "Chinese (Traditional)", Script.HAN),

    /** Ukrainian, written in Cyrillic. */
    UK("uk", "Ukrainian", Script.CYRILLIC),

    /** Russian, written in Cyrillic. */
    RU("ru", "Russian", Script.CYRILLIC),

    /** Belarusian, written in Cyrillic. */
    BE("be", "Belarusian", Script.CYRILLIC),

    /** Turkish. */
    TR("tr", "Turkish", Script.LATIN),

    /** Japanese. */
    JA("ja", "Japanese", Script.JAPANESE),

    /** Norwegian Bokmål. */
    NB("nb", "Norwegian Bokmål", Script.LATIN),

    /** Serbian, written in Cyrillic. */
    SR("sr", "Serbian", Script.CYRILLIC),

    /** Korean, written in Hangul. */
    KO("ko", "Korean", Script.HANGUL);

    private final String tag;
    private final String displayName;
    private final Script script;

    Language(String tag, String displayName, Script script) {
        this.tag = Objects.requireNonNull(tag, "tag");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.script = Objects.requireNonNull(script, "script");
    }

    /**
     * The normalized BCP-47 tag.
     *
     * @return the tag {@link LanguageTags#normalize(String)} produces for this language
     */
    public String tag() {
        return tag;
    }

    /**
     * The English display name.
     *
     * @return the name shown before an interface-language translation is applied
     */
    public String displayName() {
        return displayName;
    }

    /**
     * The writing system.
     *
     * @return the script this language is translated and estimated in
     */
    public Script script() {
        return script;
    }
}
