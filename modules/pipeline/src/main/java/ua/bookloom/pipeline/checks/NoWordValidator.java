package ua.bookloom.pipeline.checks;

import java.util.List;

/** The validator with no opinion; one shared instance, so two frames that both have none are equal. */
enum NoWordValidator implements WordValidator {
    INSTANCE;

    @Override
    public List<CheckFinding> find(final String target, final String targetLanguage) {
        return List.of();
    }
}
