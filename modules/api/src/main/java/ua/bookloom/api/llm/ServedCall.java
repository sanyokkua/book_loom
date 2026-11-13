package ua.bookloom.api.llm;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Who answered one call and what it cost, as an aggregating provider (OpenRouter) says in its reply. Each part stays
 * missing when the reply did not carry it, so a sum never counts an unreported cost as zero; a reported 0 is 0. Slugs
 * and a number only, never text.
 *
 * @param provider the hosting company's name, or null when not reported
 * @param model the model's slug as served, or null when not reported
 * @param cost the call's price in the provider's credit, or null when not reported
 */
public record ServedCall(
        @Nullable String provider,
        @Nullable String model,
        @Nullable BigDecimal cost) {}
