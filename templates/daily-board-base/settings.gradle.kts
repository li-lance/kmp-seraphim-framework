pluginManagement {
    includeBuild("__PLATFORM_KIT_PATH__")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.__REPOSITORIES_MODE__)
    repositories {
        google()
        mavenCentral()
        __WEB_TOOL_REPOS__
    }
}

rootProject.name = "__PRODUCT_ID__"
include(":shared:task-board", ":shared:local-data-sql", ":apps:android")
__WEB_MODULES__
