/**
 * The deterministic text checks: what code can decide about a translated segment without a model — a word that mixes
 * alphabets, a quote pair left open, a paragraph still in the source language, a doubled word, a spacing artefact.
 * Each check reports typed {@link ua.bookloom.pipeline.checks.CheckFinding}s with the exact span, so the fix prompt
 * can name the place instead of asking the model to find it.
 */
@NullMarked
package ua.bookloom.pipeline.checks;

import org.jspecify.annotations.NullMarked;
