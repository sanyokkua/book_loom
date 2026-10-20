package ua.bookloom.api.llm;

/** How a model's temperature is sent. */
public enum TemperatureChoice {
    /** BookLoom keeps the per-task temperature of each request. */
    CHOOSE,
    /** One value the person set is sent on every request. */
    VALUE,
    /** No temperature is sent, for a model that refuses it. */
    OMIT
}
