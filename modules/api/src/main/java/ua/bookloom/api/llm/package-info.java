/**
 * Provider-neutral chat-model contracts used by the translation engine and provider implementations.
 *
 * <p>The package deliberately contains only framework-free records, enums and interfaces so callers bind a model
 * once without depending on a provider's transport or configuration details.
 */
@NullMarked
package ua.bookloom.api.llm;

import org.jspecify.annotations.NullMarked;
