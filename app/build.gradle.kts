import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services) apply false
}

val isFossBuild = gradle.startParameter.taskNames.isNotEmpty() &&
    gradle.startParameter.taskNames.all { it.contains("foss", ignoreCase = true) }

if (!isFossBuild) {
  apply(plugin = "com.google.gms.google-services")
}

android {
  namespace = "com.example"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.hanan_bhatti.cobalt"
    minSdk = 24
    targetSdk = 37
    versionCode = 14
    versionName = "2.1.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  flavorDimensions += "distribution"
  productFlavors {
    create("foss") {
      dimension = "distribution"
      manifestPlaceholders["appName"] = "Cobalt"
    }
    create("play") {
      dimension = "distribution"
      manifestPlaceholders["appName"] = "Cobalt"
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH")
      if (keystorePath != null) {
        storeFile = file(keystorePath)
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")
        keyPassword = System.getenv("KEY_PASSWORD")
        enableV1Signing = true
        enableV2Signing = true
      } else {
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release")
    }
    debug {
      signingConfig = signingConfigs.findByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  packaging {
    resources {
      excludes += "META-INF/LICENSE*"
      excludes += "META-INF/NOTICE*"
      excludes += "META-INF/AL2.0"
      excludes += "META-INF/LGPL2.1"
      excludes += "META-INF/version-control-info.textproto"
    }
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Disable any Google Services tasks targeting the FOSS flavor
tasks.matching { it.name.contains("GoogleServices") && it.name.contains("Foss", ignoreCase = true) }.configureEach {
  enabled = false
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(libs.jsoup)
  implementation(libs.commonmark)
  implementation(platform(libs.androidx.compose.bom))
  "playImplementation"(platform(libs.firebase.bom))
  "playImplementation"(libs.firebase.ai)
  // Uncomment to use Firestore:
  "playImplementation"(libs.firebase.firestore)
  "playImplementation"("com.google.firebase:firebase-storage")

  // Firebase Auth with Google Sign-In requires all of the following to be uncommented together.
  // If you are using Firebase Auth with other providers (e.g. Email/Password), you may only need
  // firebase-auth.
  "playImplementation"(libs.firebase.auth)
  "playImplementation"(libs.firebase.analytics)
  "playImplementation"(libs.firebase.crashlytics)
  "playImplementation"(libs.firebase.perf)
  "playImplementation"(libs.androidx.credentials)
  "playImplementation"(libs.androidx.credentials.play.services)
  "playImplementation"(libs.googleid)
  "playImplementation"(libs.firebase.appcheck.recaptcha)
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
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.coil.video)
  implementation(libs.converter.moshi)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.glance.material3)
  implementation("androidx.work:work-runtime-ktx:2.9.1")
  implementation("dev.chrisbanes.haze:haze:1.7.2")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

tasks.register<Exec>("syncFdroidMetadata") {
  description = "Auto-syncs F-Droid Fastlane metadata & changelog from version code and CHANGELOG.md"
  group = "fdroid"
  commandLine("python3", "${rootDir}/scripts/update_fdroid_metadata.py")
}

configure<com.google.gms.googleservices.GoogleServicesPlugin.GoogleServicesPluginConfig> {
  missingGoogleServicesStrategy = MissingGoogleServicesStrategy.IGNORE
}
