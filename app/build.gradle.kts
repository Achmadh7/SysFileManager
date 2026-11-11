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
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"
    }

    // Kunci tanda tangan TETAP untuk semua build dari repo ini (keystore
    // sysfile.p12 di root repo). Tanpa ini, setiap build GitHub Actions
    // memakai kunci debug acak baru, sehingga APK baru tidak bisa dipasang
    // menimpa yang lama dan aplikasi harus di-uninstall dulu setiap update.
    signingConfigs {
        create("ci") {
            storeFile = rootProject.file("sysfile.p12")
            storePassword = "sysfile"
            keyAlias = "sysfile"
            keyPassword = "sysfile"
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
            signingConfig = signingConfigs.getByName("ci")
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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
}
