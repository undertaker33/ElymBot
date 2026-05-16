plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
}

gradlePlugin {
    plugins {
        register("elymbotKotlinJvm") {
            id = "elymbot.kotlin.jvm"
            implementationClass = "ElymBotKotlinJvmConventionPlugin"
        }
    }
}
