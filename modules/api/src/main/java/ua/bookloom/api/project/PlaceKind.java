package ua.bookloom.api.project;

/** What kind of place a {@link TermType#PLACE} entry names, which decides how its target inflects. */
public enum PlaceKind {

    /** A town or city. */
    CITY,

    /** A river or other body of water. */
    RIVER,

    /** A country or state. */
    COUNTRY,

    /** A region, province or land that is not a country. */
    REGION
}
