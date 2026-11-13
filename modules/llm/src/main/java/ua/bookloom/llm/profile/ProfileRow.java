package ua.bookloom.llm.profile;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ReasoningControl;

/**
 * What a built-in row says about the models it names: the request half of their handling and the verdict a person
 * reads. The server's facts and the person's overrides are laid over it by {@link ModelProfiles}.
 *
 * @param name the row's name, for the log and the tests; non-null
 * @param reasoning the thinking switch the row's models have; non-null
 * @param markupFilters the reasoning markup their replies hold; never null
 * @param systemRole where their system text goes; non-null
 * @param quirks the template behaviours a request works around; never null
 * @param hardCap the most tokens they handle well; positive
 * @param outputMax the longest answer asked of them; positive
 * @param templateOverhead the tokens their chat template adds; zero or more
 * @param batchSize the most segments one draft call carries; positive
 * @param tier their size class; non-null
 * @param verdict how BookLoom rates them for translation; non-null
 * @param warnings the warning keys the person should see for these models, such as
 *     {@value ua.bookloom.api.llm.ModelReport#WARNING_LICENCE_NOTE}; never null, usually empty
 */
public record ProfileRow(
        String name,
        ReasoningControl reasoning,
        Set<ModelProfile.MarkupFilter> markupFilters,
        ModelProfile.SystemRole systemRole,
        Set<ModelProfile.Quirk> quirks,
        int hardCap,
        int outputMax,
        int templateOverhead,
        int batchSize,
        ModelProfile.Tier tier,
        String verdict,
        List<String> warnings) {

    public ProfileRow {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(reasoning, "reasoning");
        Objects.requireNonNull(systemRole, "systemRole");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(verdict, "verdict");
        markupFilters = Set.copyOf(Objects.requireNonNull(markupFilters, "markupFilters"));
        quirks = Set.copyOf(Objects.requireNonNull(quirks, "quirks"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }
}
