package ua.bookloom.api.document;

/**
 * Where a unit sits in the book, as the book's own markup says. Only {@link #BODY} is running story text: a title
 * page, a copyright page, an "also by" list or an acknowledgement is front or back matter and carries the author's,
 * the publisher's and the series' names rather than the book's.
 */
public enum UnitRole {

    /** Before the story: cover, title page, copyright, dedication, contents. */
    FRONT_MATTER,

    /** The book's text, and any unit whose format or markup does not say otherwise. */
    BODY,

    /** After the story: acknowledgements, about the author, other titles, colophon, index. */
    BACK_MATTER
}
