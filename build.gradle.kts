import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.20"
    application
}

group = "com.backrooms"
version = "0.0.6"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

// 目标运行环境包含 Windows 7 + JRE 8：字节码固定为 Java 8（class 52），不使用 JDK 9+ 的 API。
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        // -Xjdk-release 只在 JDK 9+ 上可用；用 JDK 8 跑 CI 时靠 jvmTarget 就够了
        if (JavaVersion.current().isJava9Compatible) {
            freeCompilerArgs = listOf("-Xjdk-release=1.8")
        }
    }
}

application {
    mainClass.set("com.backrooms.cardjourney.MainKt")
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.backrooms.cardjourney.MainKt",
            "Implementation-Title" to "BackroomsCardJourney",
            "Implementation-Version" to project.version
        )
    }
}

/**
 * 单文件可执行 jar：把 kotlin-stdlib 一起打进去，
 * 方便在没有 jpackage 的 Windows 7 / JRE 8 环境里 `java -jar` 直接启动。
 */
tasks.register<Jar>("fatJar") {
    archiveBaseName.set("BackroomsCardJourney-pc")
    archiveClassifier.set("win7")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "Main-Class" to "com.backrooms.cardjourney.MainKt",
            "Implementation-Title" to "BackroomsCardJourney",
            "Implementation-Version" to project.version
        )
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
