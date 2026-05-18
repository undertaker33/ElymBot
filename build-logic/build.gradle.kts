plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
}

gradlePlugin {
    plugins {
        register("elymbotKotlinJvm") {
            id = "elymbot.kotlin.jvm"
            implementationClass = "ElymBotKotlinJvmConventionPlugin"
        }
    }
}
