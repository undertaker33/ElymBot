import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localReleaseSigningPropertiesFile = rootProject.file("keystore.properties")
val localReleaseSigningProperties = localReleaseSigningPropertiesFile.takeIf { it.isFile }?.let { file ->
    runCatching {
        Properties().apply {
            file.inputStream().use { input -> load(input) }
        }
    }.getOrNull()
}
val localReleaseSigning = localReleaseSigningProperties?.let { properties ->
    val keystoreFile = properties.getProperty("storeFile")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { rootProject.file(it) }
    val requiredProperties = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    if (
        requiredProperties.all { propertyName -> properties.getProperty(propertyName)?.isNotBlank() == true } &&
        keystoreFile?.isFile == true
    ) {
        properties to keystoreFile
    } else {
        null
    }
}

android {
    namespace = "com.elymbot.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.android.buildTools.get()

    defaultConfig {
        applicationId = "com.elymbot.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        localReleaseSigning?.let { (properties, keystoreFile) ->
            create("localRelease") {
                storeFile = keystoreFile
                storePassword = properties.getProperty("storePassword")
                keyAlias = properties.getProperty("keyAlias")
                keyPassword = properties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        if (localReleaseSigning != null) {
            getByName("release") {
                signingConfig = signingConfigs.getByName("localRelease")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jdk.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jdk.get())
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

if (localReleaseSigningPropertiesFile.isFile) {
    tasks.matching { it.name == "preReleaseBuild" }.configureEach {
        if (localReleaseSigning == null) {
            doFirst {
                throw GradleException(
                    "Release signing configuration is incomplete or its keystore file is unavailable. " +
                        "Check local keystore.properties; credential values are not shown."
                )
            }
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.room.compiler)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
}
