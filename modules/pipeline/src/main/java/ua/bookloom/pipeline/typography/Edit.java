package ua.bookloom.pipeline.typography;

/**
 * One typography step's answer.
 *
 * @param text the text after the step
 * @param count how many marks or spaces the step changed
 */
record Edit(String text, int count) {}
