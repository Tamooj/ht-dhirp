plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.highcentrality.htdhirp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.highcentrality.htdhirp"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1-spike"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.usb.serial)
}
