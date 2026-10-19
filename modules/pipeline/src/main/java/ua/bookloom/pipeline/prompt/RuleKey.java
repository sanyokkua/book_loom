package ua.bookloom.pipeline.prompt;

/** The prose rules a language or pair file may state, in the order they are shown, each under its label. */
enum RuleKey {
    QUOTES("quotes", "Quotes"),
    DIALOGUE("dialogue", "Dialogue"),
    APOSTROPHE("apostrophe", "Apostrophe"),
    HYPHEN("hyphen", "Dashes"),
    ELLIPSIS("ellipsis", "Ellipsis"),
    AGREEMENT("agreement", "Agreement"),
    ADDRESS("address", "Address"),
    NUMBERS("numbers", "Numbers"),
    DATES("dates", "Dates"),
    NAMES("names", "Names");

    private final String key;
    private final String label;

    RuleKey(final String key, final String label) {
        this.key = key;
        this.label = label;
    }

    String key() {
        return key;
    }

    String label() {
        return label;
    }
}
