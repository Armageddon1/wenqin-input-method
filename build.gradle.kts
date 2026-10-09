plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.wenqin"
version = "1.0.5"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
        localPlatformArtifacts()
    }
}

dependencies {
    intellijPlatform {
        local("/Applications/IntelliJ IDEA.app")
        bundledModule("com.intellij.modules.vcs")
    }

    implementation("net.java.dev.jna:jna:5.17.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
