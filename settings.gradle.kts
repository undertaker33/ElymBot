pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AstrBotAndroidNative"
include(":app")
include(":app-integration")
include(":core:common")
include(":core:db")
include(":core:runtime")
include(":feature:bot:api")
include(":feature:bot:impl")
include(":feature:chat:api")
include(":feature:chat:impl")
include(":feature:config:api")
include(":feature:config:impl")
include(":feature:cron:api")
include(":feature:cron:impl")
include(":feature:persona:api")
include(":feature:persona:impl")
include(":feature:plugin:api")
include(":feature:plugin:impl")
include(":feature:provider:api")
include(":feature:provider:impl")
include(":feature:qq:api")
include(":feature:qq:impl")
include(":feature:resource:api")
include(":feature:resource:impl")
