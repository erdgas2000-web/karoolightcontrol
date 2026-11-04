plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

fun gitShortSha(): String = try {
    val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    process.inputStream.bufferedReader().readText().trim().ifEmpty { "unknown" }
} catch (e: Exception) {
    "unknown"
}

fun gitCommitCount(): Int = try {
    val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    process.inputStream.bufferedReader().readText().trim().toInt()
} catch (e: Exception) {
    1
}

android {
    namespace = "io.lightprofiles.karoo"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.lightprofiles.karoo"
        minSdk = 23
        targetSdk = 34
        versionCode = gitCommitCount()
        versionName = "1.0"
        buildConfigField("String", "GIT_SHA", "\"${gitShortSha()}\"")
    }

    signingConfigs {
        create("release") {
            // Set in ~/.gradle/gradle.properties so the key never lands in git.
            val storePath = providers.gradleProperty("klp.storeFile").orNull
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = providers.gradleProperty("klp.storePassword").orNull
                keyAlias = providers.gradleProperty("klp.keyAlias").orNull
                keyPassword = providers.gradleProperty("klp.keyPassword").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (providers.gradleProperty("klp.storeFile").orNull != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.hammerhead.karoo.ext)
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.compose.ui)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.timber)
    implementation(libs.kotlinx.serialization.json)
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation(libs.junit)
}
