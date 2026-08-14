plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.snakeyaml)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
}

application {
    mainClass = "com.seraphim.workbench.generator.MainKt"
}

tasks.test {
    useJUnitPlatform()
}
