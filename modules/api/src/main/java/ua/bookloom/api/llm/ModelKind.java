package ua.bookloom.api.llm;

/** What a model name or file stands for; only {@link #LLM} may ever be chosen to translate with. */
public enum ModelKind {
    /** A chat or instruction model. */
    LLM,
    /** A model that turns text into vectors. */
    EMBEDDING,
    /** A model that scores passages against a query. */
    RERANKER,
    /** An image generator. */
    IMAGE,
    /** A video generator. */
    VIDEO,
    /** A speech or audio model. */
    SPEECH,
    /** A companion file of a model, such as a vision projector. */
    SIDECAR,
    /** A name that is not a model at all, such as a dataset. */
    NOT_MODEL,
    /** One part of a weights file split into several. */
    WEIGHTS_SHARD
}
