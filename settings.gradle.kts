pluginManagement {
    repositories {
        google()
        mavenCentral()
        // Cadangan Maven Central (mirror GCS): bila repo.maven.apache.org
        // melempar 429, Gradle lanjut ke repo berikutnya.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Cadangan Maven Central (mirror GCS): bila repo.maven.apache.org
        // melempar 429, Gradle lanjut ke repo berikutnya.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
    }
}
rootProject.name = "SysFileManager"
include(":app")
