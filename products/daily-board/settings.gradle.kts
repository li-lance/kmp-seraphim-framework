pluginManagement {
    includeBuild("../../platform-kit")
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

rootProject.name = "daily-board"
include(":shared:task-board", ":shared:local-data-sql", ":apps:android")

