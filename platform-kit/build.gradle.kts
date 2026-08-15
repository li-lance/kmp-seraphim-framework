plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        register("kotlinPolicy") {
            id = "seraphim.kotlin-policy"
            implementationClass = "com.seraphim.workbench.build.KotlinPolicyPlugin"
        }
        register("androidApplication") {
            id = "seraphim.android-application"
            implementationClass = "com.seraphim.workbench.build.AndroidApplicationPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
