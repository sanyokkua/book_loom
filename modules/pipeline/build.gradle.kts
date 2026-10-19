// :pipeline — the translation engine. The only core module that sees :document, :llm and :persistence together,
// which is what makes it the single orchestration seam :ui talks to.

plugins {
    id("bookloom.java-conventions")
    id("bookloom.spotless-conventions")
    id("bookloom.test-conventions")
    id("bookloom.coverage-conventions")
}

dependencies {
    implementation(project(":api"))
    implementation(project(":util"))
    implementation(project(":document"))
    implementation(project(":llm"))
    implementation(project(":persistence"))
    implementation(libs.guice)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)

    testImplementation(libs.logback.classic)
}

// The prompt probe (scripts/probe.sh) runs ua.bookloom.pipeline.eval.Probe on the plain classpath, so editing a prompt
// resource needs no build: this task only records where the test runtime classpath is.
tasks.register("probeClasspath") {
    group = "verification"
    description = "Writes the test runtime classpath to build/probe.classpath for scripts/probe.sh."
    val runtimeClasspath = sourceSets["test"].runtimeClasspath
    val target = layout.buildDirectory.file("probe.classpath")
    // The module's own classes and processed resources are put on the classpath by the script (source resources first).
    val ownOutput = layout.buildDirectory.get().asFile.absolutePath
    inputs.files(runtimeClasspath)
    outputs.file(target)
    doLast {
        val file = target.get().asFile
        file.parentFile.mkdirs()
        val libraries = runtimeClasspath.files.filterNot { it.absolutePath.startsWith(ownOutput + File.separator) }
        file.writeText(libraries.joinToString(File.pathSeparator) { it.absolutePath })
    }
}
