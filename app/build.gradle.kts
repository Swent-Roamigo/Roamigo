import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.kotlinCompose)
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.sonar)
  alias(libs.plugins.googleServices)
  id("jacoco")
}

android {
  namespace = "com.swent.roamigo"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.swent.roamigo"
    minSdk = 28
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables { useSupportLibrary = true }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
          getDefaultProguardFile("proguard-android-optimize.txt"),
          "proguard-rules.pro",
      )
    }

    debug {
      enableUnitTestCoverage = true
      enableAndroidTestCoverage = true
    }
  }

  testCoverage { jacocoVersion = "0.8.11" }

  buildFeatures { compose = true }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }

  // Robolectric needs to be run only in debug. But its tests are placed in the shared source set
  // (test)
  // The next lines transfers the src/test/* from shared to the testDebug one
  //
  // This prevent errors from occurring during unit tests
  sourceSets.getByName("testDebug") {
    val test = sourceSets.getByName("test")

    java.directories.clear()
    java.directories.addAll(test.java.directories)
    res.directories.clear()
    res.directories.addAll(test.res.directories)
    resources.directories.clear()
    resources.directories.addAll(test.resources.directories)
  }

  sourceSets.getByName("test") {
    java.directories.clear()
    res.directories.clear()
    resources.directories.clear()
  }
}

dependencyLocking { lockAllConfigurations() }

// With AGP 9+ the JVM target is set outside the Android block.
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

// Match CI and use a JVM supported by Mockito's bytecode instrumentation.
val testJavaLauncher = javaToolchains.launcherFor {
  languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<Test>().configureEach { javaLauncher.set(testJavaLauncher) }

sonar {
  properties {
    property("sonar.projectKey", "Swent-Roamigo_Roamigo")
    property("sonar.projectName", "Roamigo")
    property("sonar.organization", "swent-roamigo")
    property("sonar.host.url", "https://sonarcloud.io")
    // Comma-separated paths to the various directories containing the *.xml JUnit report files.
    // Each path may be absolute or relative to the project base directory.
    property(
        "sonar.junit.reportPaths",
        "${project.layout.buildDirectory.get()}/test-results/testDebugUnitTest/",
    )
    // Paths to xml files with Android Lint issues. If the main flavor is changed, this file will
    // have to be changed too.
    property(
        "sonar.androidLint.reportPaths",
        "${project.layout.buildDirectory.get()}/reports/lint-results-debug.xml",
    )
    // Paths to JaCoCo XML coverage report files.
    property(
        "sonar.coverage.jacoco.xmlReportPaths",
        "${project.layout.buildDirectory.get()}/reports/jacoco/jacocoTestReport/jacocoTestReport.xml",
    )

    // TEMP FIX for code coverage
    property(
        "sonar.coverage.exclusions",
        listOf(
                "**/com/swent/roamigo/model/TripLocation.kt",
                "**/com/swent/roamigo/model/trip/Activity.kt",
                "**/com/swent/roamigo/model/trip/Trip.kt",
                "**/com/swent/roamigo/model/trip/TripMember.kt",
                "**/com/swent/roamigo/model/trip/inviting/TripInvitation.kt",
                "**/com/swent/roamigo/model/trip/voting/Vote.kt",
                "**/com/swent/roamigo/model/trip/voting/VoteBallot.kt",
                "**/com/swent/roamigo/model/trip/voting/VoteOption.kt",
                "**/com/swent/roamigo/model/users/User.kt",
                "**/com/swent/roamigo/ui/theme/Theme.kt",
            )
            .joinToString(","),
    )
  }
}

// When a library is used both by robolectric and connected tests, use this function
fun DependencyHandlerScope.globalTestImplementation(dep: Any) {
  androidTestImplementation(dep)
  testImplementation(dep)
}

dependencies {
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services.auth)
  implementation(libs.google.id)
  implementation(libs.coroutines.play.services)
  testImplementation(libs.mockito.core)
  implementation(libs.kotlinx.coroutines.play.services)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(platform(libs.compose.bom))
  testImplementation(libs.junit)
  testImplementation(libs.mockito.core)
  testImplementation(libs.kotlinx.coroutines.test)
  globalTestImplementation(libs.androidx.junit)
  globalTestImplementation(libs.androidx.espresso.core)

  // ------------- Jetpack Compose ------------------
  val composeBom = platform(libs.compose.bom)
  implementation(composeBom)
  globalTestImplementation(composeBom)

  implementation(libs.compose.ui)
  implementation(libs.compose.ui.graphics)
  // Material Design 3
  implementation(libs.compose.material3)

  implementation(libs.compose.material.icons.extended)
  // Integration with activities
  implementation(libs.compose.activity)
  // Integration with ViewModels
  implementation(libs.compose.viewmodel)
  // Android Studio Preview support
  implementation(libs.compose.preview)
  debugImplementation(libs.compose.tooling)
  // UI Tests
  globalTestImplementation(libs.compose.test.junit)
  debugImplementation(libs.compose.test.manifest)

  // --------- Kaspresso test framework ----------
  globalTestImplementation(libs.kaspresso)
  globalTestImplementation(libs.kaspresso.compose)

  // ----------       Robolectric     ------------
  testImplementation(libs.robolectric)
}

tasks.withType<Test> {
  // Configure Jacoco for each tests
  configure<JacocoTaskExtension> {
    isIncludeNoLocationClasses = true
    excludes = listOf("jdk.internal.*")
  }
}

tasks.register("jacocoTestReport", JacocoReport::class) {
  mustRunAfter("testDebugUnitTest", "connectedDebugAndroidTest")

  reports {
    xml.required = true
    html.required = true
  }

  val fileFilter =
      listOf(
          "**/R.class",
          "**/R$*.class",
          "**/BuildConfig.*",
          "**/Manifest*.*",
          "**/*Test*.*",
          "android/**/*.*",
      )

  val kotlinClassDirs =
      listOf(
          "tmp/kotlin-classes/debug", // standalone Kotlin plugin
          "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes", // AGP 9 built-in
          // Kotlin
      )
  val debugTrees = kotlinClassDirs.map { dir ->
    fileTree(layout.buildDirectory.dir(dir)) { exclude(fileFilter) }
  }

  val mainSrc = listOf("src/main/java", "src/main/kotlin").map { layout.projectDirectory.dir(it) }
  sourceDirectories.setFrom(files(mainSrc))
  classDirectories.setFrom(files(debugTrees))
  executionData.setFrom(
      fileTree(project.layout.buildDirectory.get()) {
        include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        include("outputs/code_coverage/debugAndroidTest/connected/*/coverage.ec")
      }
  )
}
