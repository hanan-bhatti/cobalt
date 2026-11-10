import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

// Local credentials stay in the ignored root .env; CI environment variables take priority.
val localSigningProperties = Properties().apply {
  providers.fileContents(rootProject.layout.projectDirectory.file(".env")).asText.orNull
    ?.reader()?.use { load(it) }
}

fun signingSetting(name: String): String? =
  providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
    ?: localSigningProperties.getProperty(name)?.takeIf { it.isNotBlank() }

android {
  namespace = "com.example"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.hanan_bhatti.cobalt"
    minSdk = 24
    targetSdk = 37
    versionCode = 20
    versionName = "2.3.0"
    buildConfigField("boolean", "WEB_COMPANION_ENABLED", "false")

    manifestPlaceholders["appName"] = "Cobalt"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = signingSetting("KEYSTORE_PATH")
      if (keystorePath != null) {
        storeFile = file(keystorePath)
        storePassword = signingSetting("KEYSTORE_PASSWORD")
        keyAlias = signingSetting("KEY_ALIAS")
        keyPassword = signingSetting("KEY_PASSWORD")
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
      isDebuggable = false
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release")
    }
    debug {
      // Explicit local opt-in; release builds always inherit false above.
      buildConfigField("boolean", "WEB_COMPANION_ENABLED",
        providers.gradleProperty("cobalt.enableWebCompanion")
          .map { it.toBooleanStrict() }.orElse(false).get().toString())
      applicationIdSuffix = ".debug"
      manifestPlaceholders["appName"] = "Cobalt Debug"
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

tasks.withType<Test>().configureEach {
  maxParallelForks = 1
  maxHeapSize = "512m"
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
  }
}


dependencies {
  // Native continuous document/IME/selection engine. LGPL-2.1-or-later; no language packs or network modules.
  implementation("io.github.rosemoe:editor:0.24.6") {
    // The Java editor core uses the app's existing Kotlin stdlib; do not upgrade the Kotlin toolchain.
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
  }
  implementation(libs.jsoup)
  implementation(libs.commonmark)
  implementation(libs.commonmark.strikethrough)
  implementation(libs.commonmark.tables)
  implementation(libs.androidx.browser)
  implementation(platform(libs.androidx.compose.bom))

  // If you are using Firebase Auth with other providers (e.g. Email/Password), you may only need
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

tasks.register<Exec>("syncIssueVersions") {
  description = "Auto-syncs GitHub issue templates & security policy versions from build.gradle.kts and CHANGELOG.md"
  group = "documentation"
  commandLine("python3", "${rootDir}/scripts/sync_issue_versions.py")
}

tasks.matching { it.name == "preBuild" }.configureEach {
  dependsOn("syncIssueVersions")
}

