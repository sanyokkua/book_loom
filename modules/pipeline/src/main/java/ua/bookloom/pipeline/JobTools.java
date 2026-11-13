package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * What one job drafts and writes with.
 *
 * @param documents the document port the translated book is written through
 * @param model the model every call of the run goes to
 * @param mapper the tolerant JSON mapper replies are read with
 * @param templates the prompt templates calls are built from
 */
record JobTools(DocumentPort documents, ChatModel model, ObjectMapper mapper, PromptTemplates templates) {

    JobTools {
        Objects.requireNonNull(documents, "documents");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(templates, "templates");
    }
}
