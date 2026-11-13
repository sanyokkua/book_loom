package ua.bookloom.api.project;

/**
 * Whether a book's chapters tell one story or separate ones, as the brief analysis read it; it suggests whether
 * chapter summaries are written, because a summary of an unrelated earlier story only misleads a later one.
 */
public enum StoryShape {
    /** The chapters continue one story: summaries of earlier chapters help later ones. */
    ONE_STORY,
    /** The chapters are separate stories (a collection): summaries are off unless the person forces them. */
    SEPARATE_STORIES
}
