package ua.bookloom.llm.profile;

import org.jspecify.annotations.Nullable;

/**
 * The family, generation and line read from the leftover words of a name.
 *
 * @param family the words joined by "-" (with "-distill" for a distillation), or null when there are none
 * @param version the generation as written, or null
 * @param line the named line, or null
 * @param finetune a fine-tune's own label ("llamacppfixed"), or null
 * @param distillOf the family a distillation learned from, or null
 */
record FamilyWords(
        @Nullable String family,
        @Nullable String version,
        @Nullable String line,
        @Nullable String finetune,
        @Nullable String distillOf) {}
