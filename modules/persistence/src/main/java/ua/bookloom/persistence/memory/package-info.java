/**
 * In-memory storage adapters over the repository ports of {@code ua.bookloom.api.persistence} — one shared store per
 * injector, atomic per-key updates, and a chunk commit that is never half-visible (ADR-0034, {@code design.md} D2).
 * A later change replaces these with SQLite-backed adapters behind the same ports, proved by the same shared
 * {@code RepositoryContractTest}.
 */
@NullMarked
package ua.bookloom.persistence.memory;

import org.jspecify.annotations.NullMarked;
