plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.sqldelight)
    id("seraphim.kotlin-policy")
}

sqldelight {
    databases {
        create("TaskBoardDatabase") {
            packageName.set("__PACKAGE_NAME__.localdata")
        }
    }
}

kotlin {
    android {
        namespace = "__PACKAGE_NAME__.localdata"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }
    // 合约测试基类 RepositoryContractTest 在 androidHostTest 下由
    // @RunWith(RobolectricTestRunner) 的 AndroidRepositoryContractTest 子类运行；
    // 排除基类本身，避免 JUnit4 在无 Robolectric 环境下双跑同一套 @Test 方法。
    tasks.configureEach {
        if (name == "testAndroidHostTest" && this is Test) {
            filter { excludeTestsMatching("__PACKAGE_NAME__.localdata.RepositoryContractTest") }
        }
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
