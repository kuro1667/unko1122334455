import java.security.MessageDigest

plugins {
    id("com.android.application")
}

android {
    namespace = "com.kyomu.tools"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kyomu.tools"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "EXPECTED_PACKAGE", "\"com.kyomu.tools\"")
        buildConfigField("String", "BRAND_TITLE_HASH", "\"${sha256("KyomuTools")}\"")
        buildConfigField("String", "BRAND_LOG_TAG_HASH", "\"${sha256("[kyomu]")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82") {
        isTransitive = false
    }
}

fun sha256(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { b: Byte -> "%02x".format(b) }
}
