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
        mavenLocal()
        google()
        mavenCentral()

        // Maven de LKLPay SDK
        maven {
            name = "LklPayPaxSdk"
            url = uri("https://maven.pkg.github.com/Lkl-Pay/bridge-sdk-pax")
            credentials {
                // Usa gradle.properties o variables de entorno
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
        // Repo donde vive el vendor A920 (payment-nexus/pax-a920sdk)
        maven {
            name = "GitHubPackagesA920Vendor"
            url = uri("https://maven.pkg.github.com/payment-nexus/pax-a920sdk")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_USER")
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

rootProject.name = "LklPayPaxSdkDemo"
include(":app")
