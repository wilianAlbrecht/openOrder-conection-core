pluginManagement {
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

rootProject.name = "openorder-connection-core"

include(
    ":core",
    ":networking",
    ":security",
    ":discovery",
    ":websocket",
    ":database",
    ":pairing",
    ":shared",
)
