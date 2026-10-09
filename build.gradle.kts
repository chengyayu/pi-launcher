plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "com.chengyayu.pilauncher"
version = "0.2.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        bundledPlugin("org.jetbrains.plugins.terminal")
        pluginVerifier()
        zipSigner()
    }

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    patchPluginXml {
        sinceBuild.set("243")
        // No upper bound on purpose. verifyPlugin reports the plugin as
        // compatible with the newest IDE it can resolve, and a pinned
        // until-build turns into a hard "incompatible" wall for users on a
        // newer IDE until a new version happens to be published.
        untilBuild.set(provider { null })
    }

    buildSearchableOptions {
        enabled = false
    }

    test {
        useJUnitPlatform()
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        // PUBLISH_TOKEN for CI, publishToken in ~/.gradle/gradle.properties locally.
        // The properties file lives outside the repository, so the token never gets committed.
        token.set(
            providers.environmentVariable("PUBLISH_TOKEN")
                .orElse(providers.gradleProperty("publishToken"))
        )
    }
}
