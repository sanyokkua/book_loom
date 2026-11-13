package ua.bookloom.api.llm;

/**
 * How much source text one call sends, so a model that thinks on every call can be given room for its reasoning in
 * proportion to what it reads (design D20).
 *
 * @param tokens the estimated tokens of the source text the call translates or reads; zero or more
 * @param items how many items (segments, terms, candidates) the call carries; zero or more
 */
public record SourceSize(int tokens, int items) {

    public SourceSize {
        if (tokens < 0) {
            throw new IllegalArgumentException("tokens must not be negative: " + tokens);
        }
        if (items < 0) {
            throw new IllegalArgumentException("items must not be negative: " + items);
        }
    }
}
