/**
 * The per-chunk LLM-as-judge call: rendering its prompt, sending it through
 * {@link ua.bookloom.pipeline.prompt.ModelCalls}, and reading its tolerant reply into a {@link JudgeVerdict}.
 */
@NullMarked
package ua.bookloom.pipeline.judge;

import org.jspecify.annotations.NullMarked;
