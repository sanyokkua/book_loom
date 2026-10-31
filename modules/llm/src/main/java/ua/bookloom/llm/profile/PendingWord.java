package ua.bookloom.llm.profile;

/**
 * A word no rule claimed, kept for the family; {@code afterSize} tells a fine-tune's label (after the size) from a
 * family word (before it).
 */
record PendingWord(String text, boolean afterSize) {}
