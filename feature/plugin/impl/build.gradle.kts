plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.feature.plugin.impl"

    sourceSets {
        getByName("main") {
            res.srcDir("../../../app/src/main/res")
        }
    }
}

dependencies {
    val quickJsVersion = "3.2.3"

    implementation(project(":core:common"))
    implementation(project(":core:db"))
    implementation(project(":core:logging"))
    implementation(project(":core:runtime"))
    implementation(project(":core:runtime-context"))
    implementation(project(":core:runtime-search"))
    implementation(project(":core:runtime-tool"))
    implementation(project(":download:api"))
    implementation(project(":feature:config:api"))
    implementation(project(":feature:conversation:api"))
    implementation(project(":feature:cron:api"))
    implementation(project(":feature:persona:api"))
    implementation(project(":feature:plugin:api"))
    implementation(project(":feature:resource:api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.google.dagger:hilt-android:2.52")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("wang.harlon.quickjs:wrapper-android:$quickJsVersion")
    implementation("javax.inject:javax.inject:1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.json:json:20240303")
}
