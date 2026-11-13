// Local-only, cross-module coverage for SonarQube. This is separate from the six modules' branch gates.
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

apply(plugin = "jacoco")

val sonarLocal = providers.gradleProperty("sonarLocal").isPresent
val jacocoVersion = extensions.getByType<VersionCatalogsExtension>().named("libs")
    .findVersion("jacoco").orElseThrow().requiredVersion
extensions.configure<JacocoPluginExtension> { toolVersion = jacocoVersion }

val sonarCoverageReport = tasks.register<JacocoReport>("sonarCoverageReport") {
    group = "verification"
    description = "Produces combined coverage for the local SonarQube analysis."
    reports {
        xml.required = true
        xml.outputLocation = layout.buildDirectory.file("reports/jacoco/sonar/sonar.xml")
        html.required = false
        csv.required = false
    }
    // A missing execution file must not turn a purported full analysis into an incomplete report.
    setOnlyIf { true }
    doFirst {
        check(sonarLocal) { "Local coverage requires -PsonarLocal." }
        val missing = executionData.files.filterNot { it.isFile }
        check(missing.isEmpty()) { "Missing JaCoCo execution data: $missing" }
    }
}

if (sonarLocal) {
    subprojects {
        pluginManager.withPlugin("java") {
            // UI and app normally do not apply JaCoCo; instrument them only for the local analysis.
            pluginManager.apply("jacoco")
            extensions.configure<JacocoPluginExtension> { toolVersion = jacocoVersion }
            val java = extensions.getByType<JavaPluginExtension>()
            val main = java.sourceSets.named("main")
            val executionFile = layout.buildDirectory.file("jacoco/test.exec")
            val testTaskPath = "$path:test"
            sonarCoverageReport.configure {
                dependsOn(testTaskPath)
                executionData(executionFile)
                sourceDirectories.from(main.map { it.allJava.srcDirs })
                classDirectories.from(main.map { it.output.classesDirs })
            }
        }
    }
}
