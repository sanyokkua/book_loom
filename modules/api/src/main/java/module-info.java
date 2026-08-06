/**
 * Contracts only — ports, records/DTOs, enums, the {@code Result} envelope, {@code AppError}, {@code ErrorCode}.
 *
 * <p>The dependency floor: this module requires nothing internal and no framework. Every other module points at it,
 * so a framework dependency here would propagate to all eight
 * (docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md#dependency-direction).
 */
module ua.bookloom.api {
    // Compile-time only: JSpecify supplies the `@NullMarked` package markers that switch
    // NullAway on. Nothing reflects on them at runtime (02_QUALITY_GATES.md#null-safety).
    requires static org.jspecify;

    exports ua.bookloom.api;
}
