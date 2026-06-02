plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":core"))
            implementation(project(":pairing"))
            implementation(libs.sqlite.jdbc)
        }
    }
}
