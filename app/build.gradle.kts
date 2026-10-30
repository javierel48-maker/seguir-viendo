plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.seguirviendo"
    signingConfigs { getByName("debug") { storeFile = file("debug.keystore"); storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android" } }
    compileSdk = 34
    defaultConfig { applicationId = "com.seguirviendo"; minSdk = 26; targetSdk = 34; versionCode = 6; versionName = "1.5" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.tvprovider:tvprovider:1.0.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
