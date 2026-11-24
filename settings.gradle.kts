pluginManagement {
    repositories {
        google()
        // Mirror Maven Central (GCS) DULUAN: Gradle mengunduh jar dari
        // repo tempat metadata ditemukan — cadangan setelah Central tak dicoba.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Mirror Maven Central (GCS) DULUAN: Gradle mengunduh jar dari
        // repo tempat metadata ditemukan — cadangan setelah Central tak dicoba.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        mavenCentral()
    }
}
rootProject.name = "SysFileManager"
include(":app", ":liquidglass")
