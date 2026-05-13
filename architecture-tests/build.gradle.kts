plugins {
    id("elymbot.kotlin.jvm")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

sourceSets {
    test {
        kotlin.srcDir(rootProject.file("app/src/test/java/com/astrbot/android/architecture"))
        resources.srcDir(rootProject.file("app/src/test/resources"))
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    workingDir = rootProject.projectDir
}
