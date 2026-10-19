package ua.bookloom.api.pipeline;

/** A field of the Book Brief the model suggests, so the evidence for each one can be shown beside it. */
public enum BriefField {

    /** The genre in the model's words. */
    GENRE,

    /** Formal, neutral or casual language. */
    REGISTER,

    /** The narrator's voice and the era of the language. */
    VOICE,

    /** The likely readers. */
    AUDIENCE,

    /** First person, third person or not stated. */
    NARRATOR,

    /** The first-person narrator's gender. */
    NARRATOR_GENDER
}
