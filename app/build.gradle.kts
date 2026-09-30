import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.kawach.cloud"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.kawach.cloud"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0-alpha01"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // AI Studio Secrets / Build Environment credentials bridge
    val envApiId = System.getenv("TELEGRAM_API_ID")?.takeIf { it.isNotBlank() && it != "0" && it != "UNCONFIGURED" }
      ?: (project.findProperty("TELEGRAM_API_ID") as? String)?.takeIf { it.isNotBlank() && it != "0" && it != "UNCONFIGURED" }

    val envApiHash = System.getenv("TELEGRAM_API_HASH")?.takeIf { it.isNotBlank() && it != "0" && it != "UNCONFIGURED" }
      ?: (project.findProperty("TELEGRAM_API_HASH") as? String)?.takeIf { it.isNotBlank() && it != "0" && it != "UNCONFIGURED" }

    // Check local .env file fallback for local development if environment variables are not set
    val dotEnvFile = rootProject.file(".env")
    val dotEnvProps = Properties().apply {
      if (dotEnvFile.exists()) {
        dotEnvFile.inputStream().use { load(it) }
      }
    }

    val finalApiId = envApiId
      ?: dotEnvProps.getProperty("TELEGRAM_API_ID")?.takeIf { it.isNotBlank() && it != "0" && it != "UNCONFIGURED" }
      ?: "0"

    val finalApiHash = envApiHash
      ?: dotEnvProps.getProperty("TELEGRAM_API_HASH")?.takeIf { it.isNotBlank() && it != "0" && it != "UNCONFIGURED" }
      ?: "UNCONFIGURED"

    buildConfigField("String", "TELEGRAM_API_ID", "\"$finalApiId\"")
    buildConfigField("String", "TELEGRAM_API_HASH", "\"$finalApiHash\"")
  }

  val releaseKeystorePath = (project.findProperty("KAWACH_RELEASE_STORE_FILE") as? String)
    ?: (project.findProperty("KEYSTORE_PATH") as? String)
    ?: System.getenv("KAWACH_RELEASE_STORE_FILE")
    ?: System.getenv("KEYSTORE_PATH")
  val releaseStorePassword = (project.findProperty("KAWACH_RELEASE_STORE_PASSWORD") as? String)
    ?: (project.findProperty("KEYSTORE_PASSWORD") as? String)
    ?: System.getenv("KAWACH_RELEASE_STORE_PASSWORD")
    ?: System.getenv("KEYSTORE_PASSWORD")
    ?: System.getenv("STORE_PASSWORD")
  val releaseKeyAlias = (project.findProperty("KAWACH_RELEASE_KEY_ALIAS") as? String)
    ?: (project.findProperty("KEY_ALIAS") as? String)
    ?: System.getenv("KAWACH_RELEASE_KEY_ALIAS")
    ?: System.getenv("KEY_ALIAS")
  val releaseKeyPassword = (project.findProperty("KAWACH_RELEASE_KEY_PASSWORD") as? String)
    ?: (project.findProperty("KEY_PASSWORD") as? String)
    ?: System.getenv("KAWACH_RELEASE_KEY_PASSWORD")
    ?: System.getenv("KEY_PASSWORD")

  val releaseStoreFile = releaseKeystorePath?.let { path ->
    val direct = file(path)
    if (direct.exists()) direct else rootProject.file(path)
  }

  val hasReleaseSigning = releaseStoreFile != null &&
    releaseStoreFile.exists() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = releaseStoreFile
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
    create("debugConfig") {
      val debugFile = file("${rootDir}/debug.keystore")
      if (debugFile.exists()) {
        storeFile = debugFile
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    debug {
      val debugConf = signingConfigs.findByName("debugConfig")
      if (debugConf != null && debugConf.storeFile?.exists() == true) {
        signingConfig = debugConf
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  ignoreList.add("TELEGRAM_API_ID")
  ignoreList.add("TELEGRAM_API_HASH")
}

tasks.register("verifyTelegramCredentials") {
  doLast {
    val apiIdEnv = System.getenv("TELEGRAM_API_ID")
    val apiHashEnv = System.getenv("TELEGRAM_API_HASH")
    val isIdConfigured = !apiIdEnv.isNullOrBlank() && apiIdEnv != "0" && apiIdEnv != "UNCONFIGURED" && apiIdEnv.toIntOrNull()?.let { it > 0 } == true
    val isHashConfigured = !apiHashEnv.isNullOrBlank() && apiHashEnv != "UNCONFIGURED" && apiHashEnv.length >= 16

    println("VERIFICATION_REPORT: Telegram API ID present and valid: $isIdConfigured")
    println("VERIFICATION_REPORT: Telegram API Hash present and valid: $isHashConfigured")
    println("VERIFICATION_REPORT: Source: ${if (isIdConfigured && isHashConfigured) "AI Studio Environment Variables" else "Unconfigured"}")
  }
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  implementation(libs.tdlib.core)
  implementation(libs.tdlib.ktx)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
