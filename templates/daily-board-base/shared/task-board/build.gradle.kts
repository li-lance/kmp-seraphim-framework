plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    id("seraphim.kotlin-policy")
}

kotlin {
    android {
        namespace = "__PACKAGE_NAME__.taskboard"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }
    iosArm64 {
        binaries.framework {
            baseName = "TaskBoardShared"
            isStatic = true
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "TaskBoardShared"
            isStatic = true
        }
    }
    __WASM_JS_TARGET__
    sourceSets {
        commonMain.dependencies {
            // api：task-board 被 LocalDataSql.framework export，传递依赖须为 API 可见（K/N 单运行时修正）
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        named("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
