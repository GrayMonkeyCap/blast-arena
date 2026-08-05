plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.lifeledger.core.testing"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Every dependency here is `api`: this module exists purely to be depended on by other
    // modules' test source sets, so its own test tooling must be visible transitively — a
    // consumer should only need `testImplementation(projects.core.testing)`.
    api(projects.core.model)
    implementation(projects.core.common)

    api(libs.junit)
    api(libs.truth)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.androidx.test.core)
    api(libs.robolectric)
}
