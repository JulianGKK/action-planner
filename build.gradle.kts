import org.gradle.api.artifacts.ProjectDependency

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("com.android.library") version "9.4.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}

val verifyArchitecture = tasks.register("verifyArchitecture") {
    doLast {
        val allowed = mapOf(
            ":app" to setOf(":domain", ":data", ":security"),
            ":data" to setOf(":domain", ":security"),
            ":domain" to emptySet(), ":security" to emptySet()
        )
        check(subprojects.map { it.path }.toSet() == allowed.keys)
        subprojects.forEach { project ->
            val declared = project.configurations.filter {
                it.name in setOf("api", "implementation", "compileOnly", "runtimeOnly")
            }.flatMap { it.dependencies.withType(ProjectDependency::class.java) }.map { it.path }
            check(project.path !in declared) { "Declared self dependency: ${project.path}" }
            val actual = project.configurations.flatMap { configuration ->
                configuration.dependencies.withType(ProjectDependency::class.java).map { it.path }
            }.filter { it != project.path }.toSet() // AGP adds self-variant edges for its test/lint wiring.
            check(actual == allowed.getValue(project.path)) { "Forbidden module graph: ${project.path}: $actual" }
        }
        fileTree("app/src/main/kotlin/org/pag/prototype").matching { include("ui/**/*.kt", "product/**/*.kt", "demo/**/*.kt") }.forEach {
            check(!Regex("import (org\\.pag\\.(data|security)|android\\.(security|database|hardware\\.biometrics)|javax\\.crypto|java\\.security)").containsMatchIn(it.readText())) {
                "Implementation import in UI: $it"
            }
        }
        fileTree("app/src/main/kotlin/org/pag/prototype/demo").matching { include("**/*.kt") }.forEach {
            check(!Regex("ProductRuntime|SessionCoordinator|LeadershipGrant|StorageAccess|Repository|SavedState|rememberSaveable|SharedPreferences|DataStore|java\\.io|android\\.content").containsMatchIn(it.readText())) {
                "Forbidden product/persistence capability in demo: $it"
            }
        }
    }
}
subprojects {
    tasks.matching { it.name == "check" || it.name.startsWith("test") || it.name == "preBuild" }
        .configureEach { dependsOn(verifyArchitecture) }
}
