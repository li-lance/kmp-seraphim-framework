plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
}

val generatorRuntime by configurations.creating

dependencies {
    generatorRuntime(project(":tooling:generator"))
}

tasks.register<JavaExec>("createProduct") {
    group = "workbench"
    description = "Render a Phase 0 product"
    mainClass.set("com.seraphim.workbench.generator.MainKt")
    classpath = generatorRuntime
    args(
        "--manifest", providers.gradleProperty("manifest").orElse("project.yaml").get(),
        "--template", layout.projectDirectory.dir("templates/daily-board-base").asFile.absolutePath,
        "--output", providers.gradleProperty("output").orElse("products/daily-board").get(),
        "--platform-kit", layout.projectDirectory.dir("platform-kit").asFile.absolutePath,
    )
}
