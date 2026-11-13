package ua.bookloom.pipeline.glossary;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The strict response schema of the name-knowledge call: per bare name a gender of three (no "unknown", so the model
 * gives its most probable value, owner decision D-11), a category and a confidence.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NameKnowledgeSchema {

    /** An object holding one item per name. */
    public static final String SCHEMA = """
            {"type":"object","properties":{"items":{"type":"array","items":{"type":"object","properties":{\
            "term":{"type":"string"},\
            "gender":{"type":"string","enum":["male","female","neuter"]},\
            "category":{"type":"string","enum":["person","city","river","country","region","place","organisation",\
            "product","title","term"]},\
            "confidence":{"type":"number"}},\
            "required":["term","gender","category","confidence"],"additionalProperties":false}}},\
            "required":["items"],"additionalProperties":false}""";
}
