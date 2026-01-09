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

rootProject.name = "CamPilot"
include(
    ":app",
    ":core-camera",
    ":core-ai",
    ":feature-hdr",
    ":feature-timelapse"
)
