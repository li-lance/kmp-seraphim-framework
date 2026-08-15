// settings.gradle.kts
pluginManagement {
    if (file("platform-kit/settings.gradle.kts").isFile) {
        includeBuild("platform-kit")
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kmp-seraphim-framework"
include(":tooling:generator")

if (file("products/daily-board/settings.gradle.kts").isFile) {
    includeBuild("products/daily-board")
}
