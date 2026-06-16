plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    id("com.vanniktech.maven.publish") version "0.36.0"
    id("signing")
}

android {
    namespace = "io.github.codeideal.nestedhorizontalpager"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
}

group = "io.github.codeideal"
version = "1.0.0"

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(
        groupId = "io.github.codeideal",
        artifactId = "nested-horizontal-pager",
        version = "1.0.1"
    )
    pom {
        name.set("Nested Horizontal Pager")
        description.set("A Compose library for nested HorizontalPager gesture handling.")
        url.set("https://github.com/CodeIdeal/nested-horizontal-pager")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("CodeIdeal")
                name.set("CodeIdeal")
                url.set("https://github.com/CodeIdeal")
            }
        }
        scm {
            url.set("https://github.com/CodeIdeal/nested-horizontal-pager")
            connection.set("scm:git:https://github.com/CodeIdeal/nested-horizontal-pager.git")
            developerConnection.set("scm:git:ssh://git@github.com/CodeIdeal/nested-horizontal-pager.git")
        }
    }
}
signing {
    useGpgCmd()
}