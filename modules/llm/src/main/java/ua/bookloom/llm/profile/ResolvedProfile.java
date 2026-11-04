package ua.bookloom.llm.profile;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelIdentity;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelReport;

/**
 * One model's resolved handling: the profile a request reads and what the person reads about it.
 *
 * @param profile the request half; non-null
 * @param identity who the model is; non-null
 * @param match how the handling was found; non-null
 * @param verdict how BookLoom rates the model; non-null
 * @param warnings what the person should know; never null
 * @param quant the quant as the server reports it, else as the name writes it, lower-cased; null when neither says
 * @param baseModel the display name of the base model when the model is a known fine-tune; null otherwise
 */
public record ResolvedProfile(
        ModelProfile profile,
        ModelIdentity identity,
        ModelReport.Match match,
        String verdict,
        List<String> warnings,
        @Nullable String quant,
        @Nullable String baseModel) {

    public ResolvedProfile {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(verdict, "verdict");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    /**
     * What the person reads about the model.
     *
     * @return the report with the server's windows and the thinking control as the profile holds them
     */
    public ModelReport report() {
        return new ModelReport(
                profile.context().detected(),
                profile.context().loaded(),
                identity,
                match,
                verdict,
                warnings,
                profile.reasoning(),
                null,
                null,
                baseModel,
                null);
    }
}
