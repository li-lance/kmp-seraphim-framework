pluginManagement {
    includeBuild("__PLATFORM_KIT_PATH__")
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

rootProject.name = "__PRODUCT_ID__"
include(":shared:task-board", ":apps:android")
