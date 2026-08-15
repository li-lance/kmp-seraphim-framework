plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("seraphim.kotlin-policy")
}

kotlin {
    wasmJs {
        nodejs()
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:task-board"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
