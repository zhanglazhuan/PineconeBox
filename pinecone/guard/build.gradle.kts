plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.pinecone.guard"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.security.crypto)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
