plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "id.sysfile.manager"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.sysfile.manager"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4.0"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // Kunci tanda tangan: baca kredensial dari env/CI secrets terlebih dahulu.
    // Fallback ke `sysfile.p12` bawaan hanya jika `SYSFILE_STRICT_SIGNING` tidak aktif
    // (menjaga CI tetap jalan saat belum ada secrets, sekaligus memperingatkan saat release).
    signingConfigs {
        val envStoreFile = System.getenv("SYSFILE_STORE_FILE")
        val envStorePass = System.getenv("SYSFILE_STORE_PASSWORD")
        val envAlias = System.getenv("SYSFILE_KEY_ALIAS")
        val envKeyPass = System.getenv("SYSFILE_KEY_PASSWORD")
        val usingDemoKeystore = envStoreFile.isNullOrBlank() || envStorePass.isNullOrBlank()

        create("ci") {
            storeFile = envStoreFile?.takeIf { it.isNotBlank() }?.let { file(it) } ?: rootProject.file("sysfile.p12")
            storePassword = envStorePass?.takeIf { it.isNotBlank() } ?: "sysfile"
            keyAlias = envAlias?.takeIf { it.isNotBlank() } ?: "sysfile"
            keyPassword = envKeyPass?.takeIf { it.isNotBlank() } ?: "sysfile"
            storeType = "PKCS12"
        }
        create("releaseSigning") {
            if (usingDemoKeystore) {
                if (System.getenv("SYSFILE_STRICT_SIGNING") == "true") {
                    throw GradleException("SYSFILE_STORE_FILE dan SYSFILE_STORE_PASSWORD wajib diisi saat SYSFILE_STRICT_SIGNING=true")
                }
                logger.warn("PERINGATAN: Build release memakai keystore demo repo (sysfile.p12). Set SYSFILE_* env/secrets untuk rilis publik.")
            }
            storeFile = envStoreFile?.takeIf { it.isNotBlank() }?.let { file(it) } ?: rootProject.file("sysfile.p12")
            storePassword = envStorePass?.takeIf { it.isNotBlank() } ?: "sysfile"
            keyAlias = envAlias?.takeIf { it.isNotBlank() } ?: "sysfile"
            keyPassword = envKeyPass?.takeIf { it.isNotBlank() } ?: "sysfile"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("ci")
            // R8 aktif juga di build debug: APK yang dipasang sehari-hari
            // sudah teruji melewati minify + shrink resources.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            signingConfig = signingConfigs.getByName("releaseSigning")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose")
    // Folder Terkunci: prompt sidik jari / kredensial layar kunci (perlu FragmentActivity).
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3:1.5.0-alpha08")
    implementation("androidx.compose.material:material-icons-extended")
    // Mesin arsip: TAR/7Z/GZIP/BZIP2 (Commons Compress) + XZ/LZMA (Tukaani) + ZIP terenkripsi (zip4j)
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    implementation("com.github.junrar:junrar:7.5.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")

    // Unit test JVM untuk logika murni (routing arsip/kategori, formatSize, uniqueName).
    testImplementation("junit:junit:4.13.2")

    // Thumbnail foto & video di daftar file.
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
}
