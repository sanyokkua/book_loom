package ua.bookloom.api.settings;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;

/**
 * One stored preference: its key, its default and the text codec a store uses.
 *
 * <p>A key whose default is {@code null} has no value until the person (or the first-launch look) sets one, so a
 * read of it may be empty; every other key always reads to a value. {@link #parse} never throws — a stored text
 * that no longer fits the type reads as the default.
 *
 * @param key the stored name
 * @param defaultValue what an unset or unreadable value reads as, or {@code null} when the setting is absent until set
 * @param parser reads the stored text, or answers empty when the text is not a value of the type
 * @param formatter writes a value as the stored text
 * @param <T> the value type
 */
public record AppSetting<T>(
        String key, @Nullable T defaultValue, Function<String, Optional<T>> parser, Function<T, String> formatter) {

    private static final String FALSE = "false";
    private static final String TRUE = "true";
    private static final Set<String> LANGUAGES = Set.of("en", "uk");
    private static final String PART_SEPARATOR = " ";
    private static final int MODEL_PARTS = 2;

    /** The colour theme. */
    public static final AppSetting<ThemeSetting> THEME = ofEnum("ui.theme", ThemeSetting.SYSTEM);

    /** The app language ({@code en} or {@code uk}); absent until the person chooses one. */
    public static final AppSetting<String> LANGUAGE = new AppSetting<>(
            "ui.language", null, raw -> Optional.of(raw.strip()).filter(LANGUAGES::contains), Function.identity());

    /** Whether the technical views and lines are shown. */
    public static final AppSetting<Boolean> TECHNICAL_DETAILS = ofBoolean("ui.technicalDetails", false);

    /** The default review mode of a new run. */
    public static final AppSetting<ReviewMode> REVIEW_MODE = ofEnum("run.reviewMode", ReviewMode.UNATTENDED);

    /** The default quality of a new project. */
    public static final AppSetting<QualityDial> DEFAULT_DIAL = ofEnum("run.defaultDial", QualityDial.BALANCED);

    /** Thinking before answering. */
    public static final AppSetting<ReasoningSetting> REASONING = ofEnum("run.reasoning", ReasoningSetting.OFF);

    /** Which sampling a call sends. */
    public static final AppSetting<SamplingSetting> SAMPLING = ofEnum("run.sampling", SamplingSetting.PROGRAM_DEFAULTS);

    /** Whether the review desk opens when a translation ends with segments to review. */
    public static final AppSetting<Boolean> OPEN_REVIEW_DESK = ofBoolean("automation.openReviewDesk", true);

    /**
     * Whether the last check runs before saving by default. Absent until the person writes it, then Export shows the
     * switch off for a book below Max quality unless this says otherwise; Automation writes it explicitly.
     */
    public static final AppSetting<Boolean> LAST_CHECK = ofBoolean("automation.lastCheck", false);

    /** Whether the computer is kept awake during a run. */
    public static final AppSetting<Boolean> KEEP_AWAKE = ofBoolean("automation.keepAwake", true);

    /** Whether the window opens the last project at launch. */
    public static final AppSetting<Boolean> REOPEN_LAST_PROJECT = ofBoolean("automation.reopenLastProject", true);

    /** The model a new run and a new project use; absent until one is chosen. */
    public static final AppSetting<ModelSelection> CURRENT_MODEL =
            new AppSetting<>("model.current", null, AppSetting::readModel, AppSetting::writeModel);

    /** The id of the project that was open last; absent until one is opened. */
    public static final AppSetting<String> LAST_PROJECT = new AppSetting<>(
            "app.lastProject", null, raw -> Optional.of(raw.strip()).filter(s -> !s.isEmpty()), Function.identity());

    /** Whether the first-launch look at local servers has run; absent until it has. */
    public static final AppSetting<Boolean> FIRST_LAUNCH_PROBED =
            new AppSetting<>("firstLaunch.probed", null, AppSetting::readBoolean, AppSetting::writeBoolean);

    /**
     * Rejects an incomplete setting definition.
     */
    public AppSetting {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(parser, "parser");
        Objects.requireNonNull(formatter, "formatter");
    }

    /**
     * The "sleep line closed" flag of one project, kept apart so closing it in one book leaves the others alone.
     *
     * @param projectId the non-null project id
     * @return the setting, closed = {@code true}, default {@code false}
     */
    public static AppSetting<Boolean> sleepLineClosed(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return ofBoolean("help.sleepLineClosed." + projectId, false);
    }

    /**
     * Reads a stored text.
     *
     * @param raw the stored text, or {@code null} when nothing is stored
     * @return the value, or the default when the text is {@code null} or not a value of the type; {@code null} only
     *     when the default is {@code null}
     */
    public @Nullable T parse(final @Nullable String raw) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            final Optional<T> parsed = parser.apply(raw);
            return parsed.isPresent() ? parsed.get() : defaultValue;
        } catch (RuntimeException unreadable) {
            return defaultValue;
        }
    }

    /**
     * Writes a value as the stored text.
     *
     * @param value the non-null value
     * @return the text {@link #parse} reads back to the same value
     */
    public String format(final T value) {
        Objects.requireNonNull(value, "value");
        return formatter.apply(value);
    }

    private static <E extends Enum<E>> AppSetting<E> ofEnum(final String key, final E defaultValue) {
        final Class<E> type = defaultValue.getDeclaringClass();
        return new AppSetting<>(
                key,
                defaultValue,
                raw -> Optional.of(Enum.valueOf(type, raw.strip().toUpperCase(Locale.ROOT))),
                value -> value.name().toLowerCase(Locale.ROOT));
    }

    private static AppSetting<Boolean> ofBoolean(final String key, final boolean defaultValue) {
        return new AppSetting<>(key, defaultValue, AppSetting::readBoolean, AppSetting::writeBoolean);
    }

    private static Optional<Boolean> readBoolean(final String raw) {
        final String text = raw.strip().toLowerCase(Locale.ROOT);
        return TRUE.equals(text) ? Optional.of(true) : FALSE.equals(text) ? Optional.of(false) : Optional.empty();
    }

    private static String writeBoolean(final Boolean value) {
        return value ? TRUE : FALSE;
    }

    private static Optional<ModelSelection> readModel(final String raw) {
        final String[] parts = raw.split(PART_SEPARATOR, -1);
        if (parts.length != MODEL_PARTS) {
            return Optional.empty();
        }
        return Optional.of(new ModelSelection(decode(parts[0]), decode(parts[1])));
    }

    private static String writeModel(final ModelSelection model) {
        return encode(model.providerId()) + PART_SEPARATOR + encode(model.modelId());
    }

    private static String encode(final String part) {
        return URLEncoder.encode(part, StandardCharsets.UTF_8);
    }

    private static String decode(final String part) {
        return URLDecoder.decode(part, StandardCharsets.UTF_8);
    }
}
