plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(project(":core:runtime-tool"))

    implementation("javax.inject:javax.inject:1")
    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
}
