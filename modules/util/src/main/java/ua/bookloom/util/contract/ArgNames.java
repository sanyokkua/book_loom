package ua.bookloom.util.contract;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The names a null check gives the value it guards, written once so the same word is not repeated in every class.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ArgNames {

    public static final String CHECK = "check";
    public static final String CONFIG = "config";
    public static final String CONTENT_HASH = "contentHash";
    public static final String CONTEXT_KEY = "contextKey";
    public static final String DEFAULT = "default";
    public static final String DOCUMENT = "document";
    public static final String DOCUMENT_ID = "documentId";
    public static final String DURATION = "duration";
    public static final String ERROR = "error";
    public static final String FILE_NAME = "fileName";
    public static final String GENDER = "gender";
    public static final String INDEX = "index";
    public static final String KEPT_AUXILIARY_KINDS = "keptAuxiliaryKinds";
    public static final String LOCALE = "locale";
    public static final String LOCKED_RENDERINGS = "lockedRenderings";
    public static final String MASKED_SOURCE = "maskedSource";
    public static final String MESSAGES = "messages";
    public static final String MODEL = "model";
    public static final String MODEL_ID = "modelId";
    public static final String NAME_KEY = "nameKey";
    public static final String OCCASION = "occasion";
    public static final String OPERATION = "operation";
    public static final String OS_NAME = "osName";
    public static final String PROGRESS = "progress";
    public static final String PROJECT_ID = "projectId";
    public static final String PROVIDER_ID = "providerId";
    public static final String QUOTE = "quote";
    public static final String REPLY = "reply";
    public static final String REPORT = "report";
    public static final String REQUEST = "request";
    public static final String RETRY = "retry";
    public static final String REVISION = "revision";
    public static final String SEGMENT = "segment";
    public static final String SEGMENT_ID = "segmentId";
    public static final String SETTING = "setting";
    public static final String SOURCE = "source";
    public static final String SOURCE_HASH = "sourceHash";
    public static final String SUMMARY = "summary";
    public static final String TARGET = "target";
    public static final String TARGET_TAG = "targetTag";
    public static final String TERMS = "terms";
    public static final String TITLE = "title";
    public static final String TYPED_ID = "typedId";
}
