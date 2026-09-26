// :ui — JavaFX presentation.
//
// Note what is absent: :document, :llm and :persistence. The UI reaches the core through :pipeline and :api only.
// JavaFX arrives via its own convention plugin rather than a conditional inside java-conventions, so a core
// module cannot acquire JavaFX by accident — it would have to apply a plugin it has no reason to apply.

plugins {
    id("bookloom.java-conventions")
    id("bookloom.spotless-conventions")
    id("bookloom.javafx-conventions")
    id("bookloom.test-conventions")
    // No `bookloom.coverage-conventions`, and that absence is a decision rather than an oversight: JavaFX
    // presentation code is excluded from the branch threshold because headless TestFX covers it behaviourally
    // — screen states, wiring and mockup conformance — which a branch percentage cannot express
    // (02_QUALITY_GATES.md#coverage-gate, 06_TESTING_STRATEGY.md#coverage-traceability).
}

dependencies {
    implementation(project(":api"))
    implementation(project(":util"))
    implementation(project(":pipeline"))
    implementation(libs.guice)
    implementation(libs.slf4j.api)
    // ICU MessageFormat: the plural and select forms the catalogue needs, which java.text.MessageFormat lacks.
    implementation(libs.icu4j)
    implementation(libs.controlsfx)
    implementation(libs.ikonli.javafx)
    // Each Ikonli icon pack is its own JPMS module; Feather's stroke glyphs match the mockup's navigation icons.
    implementation(libs.ikonli.feather.pack)
}

// The token-catalogue test reads the mockup's light block to prove the stylesheet carries exactly its roles. It is a
// declared input (relative path sensitivity) so editing the mockup re-runs the test instead of reusing a stale
// UP-TO-DATE result, and its location is handed over as a property rather than guessed from the working directory.
class MockupPathArgument(
    @get:Internal val mockup: File,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = listOf("-Dbookloom.mockup=${mockup.absolutePath}")
}

val mockupHtml = rootProject.layout.projectDirectory.file("docs/specification/mockups/ui-mockup.html")

tasks.withType<Test>().configureEach {
    inputs.file(mockupHtml).withPropertyName("mockupHtml").withPathSensitivity(PathSensitivity.RELATIVE)
    jvmArgumentProviders.add(MockupPathArgument(mockupHtml.asFile))
}
