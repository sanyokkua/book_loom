/**
 * Server-engine detection: which program answers at a provider's address (Ollama, LM Studio, llama-server, Unsloth,
 * OpenRouter or a generic OpenAI-compatible server), found by the shape of a few short GET replies on Save, Test or
 * Detect again, never by a port or a status, and stored on the provider.
 */
@org.jspecify.annotations.NullMarked
package ua.bookloom.llm.engine;
