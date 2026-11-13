package ua.bookloom.pipeline.export.archfixture;

import ua.bookloom.api.llm.ChatModel;

/**
 * Violation fixture for {@code export-makes-no-model-call}: an export class holding a chat model, which is how a
 * provider request would get into the checked export.
 */
public final class ModelUsingExporter {

    private final ChatModel model;

    public ModelUsingExporter(final ChatModel model) {
        this.model = model;
    }

    public ChatModel model() {
        return model;
    }
}
