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
}
