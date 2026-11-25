plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services") version "4.4.4" apply false
}

if (file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "org.wit.fightfinder"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.wit.fightfinder"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.timber)
    implementation(libs.coil)
    implementation(libs.osmdroid)
    implementation("com.google.code.gson:gson:2.13.2")
    implementation(libs.play.services.location)
    implementation("androidx.core:core-splashscreen:1.2.0")
    // Firebase Auth 25 requires Kotlin metadata newer than our Android plugin supports.
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("androidx.navigation:navigation-fragment:2.9.7")
    implementation("androidx.navigation:navigation-ui:2.9.7")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    testImplementation(libs.junit)
}
