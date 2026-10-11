pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        // Cafe Bazaar's in-app billing library (Poolakey) is published on JitPack.
        maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\.cafebazaar.*") } }
    }
}
rootProject.name = "Yadar"
include(":app")
