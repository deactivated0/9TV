import java.io.BufferedReader

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

fun getVersionInfo(): Pair<Int, String> {
    val versionFile = rootProject.file("version.json")
    if (versionFile.exists()) {
        try {
            val content = versionFile.readText()
            val codeMatch = Regex("\"version_code\"\\s*:\\s*(\\d+)").find(content)
            val nameMatch = Regex("\"version_name\"\\s*:\\s*\"([^\"]+)\"").find(content)
            if (codeMatch != null && nameMatch != null) {
                return Pair(codeMatch.groupValues[1].toInt(), nameMatch.groupValues[1])
            }
        } catch (_: Exception) {}
    }

    try {
        val process = Runtime.getRuntime().exec("git describe --tags")
        process.waitFor()
        val raw = process.inputStream.bufferedReader().use(BufferedReader::readText).trim()
        if (raw.isNotEmpty() && !raw.contains("fatal") && !raw.contains("not a git")) {
            val versionName = raw.removePrefix("v").ifEmpty { "1.0.0" }
            val arr = (raw.replace("v", "").replace(".", " ").replace("-", " ") + " 0").split(" ")
            val versionCode =
                arr[0].toInt() * 16777216 + arr[1].toInt() * 65536 + arr[2].toInt() * 256 + arr[3].toInt()
            return Pair(versionCode, versionName)
        }
    } catch (_: Exception) {}

    return Pair(1, "1.0.0")
}

val (appVersionCode, appVersionName) = getVersionInfo()

android {
    namespace = "com.deactivated0.tv9"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.deactivated0.tv9"
        minSdk = 14
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        multiDexEnabled = true
        vectorDrawables.useSupportLibrary = true
        resourceConfigurations += listOf("en")
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // For AGP 7.4+
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.media3.ui)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.rtsp)

    implementation(libs.nanohttpd)
    implementation(libs.zxing)
    implementation(libs.glide)

    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.converter.gson)
    implementation(libs.retrofit)

    implementation(libs.core.ktx)
    implementation(libs.coroutines)

    implementation(libs.constraintlayout)
    implementation(libs.appcompat)
    implementation(libs.activity)
    implementation(libs.fragment)
    implementation(libs.recyclerview)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.multidex)

    implementation(files("libs/lib-decoder-ffmpeg-release.aar"))

    implementation(libs.conscrypt)
    implementation(libs.okhttp.logging)
}