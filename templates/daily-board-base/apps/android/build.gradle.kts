plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("seraphim.android-application")
}

android {
    namespace = "__PACKAGE_NAME__.android"
    defaultConfig {
        applicationId = "__PACKAGE_NAME__.android"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":shared:task-board"))
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.material3:material3")
}
