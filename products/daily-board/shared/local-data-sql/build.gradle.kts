plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.sqldelight)
    id("seraphim.kotlin-policy")
}

sqldelight {
    databases {
        create("TaskBoardDatabase") {
            packageName.set("com.seraphim.dailyboard.localdata")
        }
    }
}

kotlin {
    android {
        namespace = "com.seraphim.dailyboard.localdata"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }
    iosArm64 {
        binaries.framework {
            baseName = "LocalDataSql"
            isStatic = true
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "LocalDataSql"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:task-board"))
            implementation(libs.sqldelight.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        named("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.robolectric)
            }
        }
    }
}
