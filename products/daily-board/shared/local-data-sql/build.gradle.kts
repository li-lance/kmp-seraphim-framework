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
    // 合约测试基类 RepositoryContractTest 在 androidHostTest 下由
    // @RunWith(RobolectricTestRunner) 的 AndroidRepositoryContractTest 子类运行；
    // 排除基类本身，避免 JUnit4 在无 Robolectric 环境下双跑同一套 @Test 方法。
    tasks.configureEach {
        if (name == "testAndroidHostTest" && this is Test) {
            filter { excludeTestsMatching("com.seraphim.dailyboard.localdata.RepositoryContractTest") }
        }
    }
    iosArm64 {
        binaries.framework {
            baseName = "LocalDataSql"
            isStatic = true
            // K/N 单运行时：task-board 经 export 并入 LocalDataSql.framework（Task 17 修正）
            export(project(":shared:task-board"))
            transitiveExport = true
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "LocalDataSql"
            isStatic = true
            // K/N 单运行时：task-board 经 export 并入 LocalDataSql.framework（Task 17 修正）
            export(project(":shared:task-board"))
            transitiveExport = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            // api：task-board 被本模块的 framework export，导出项目须为 API 依赖（K/N 单运行时修正）
            api(project(":shared:task-board"))
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
