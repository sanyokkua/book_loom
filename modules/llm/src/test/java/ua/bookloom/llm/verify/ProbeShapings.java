package ua.bookloom.llm.verify;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.llm.profile.BuiltInRows;
import ua.bookloom.llm.profile.ModelProfiles;
import ua.bookloom.llm.provider.ModelSettingsCache;
import ua.bookloom.llm.provider.RefusalMemory;

/** The probe shaping a verifier built by hand gets: the built-in rows over the given settings. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProbeShapings {

    static ProbeShaping over(final ModelSettingsRepository repository) {
        final ModelSettingsCache cache = new ModelSettingsCache(repository);
        return new ProbeShaping(new ModelProfiles(new BuiltInRows()), cache, new RefusalMemory(cache));
    }
}
