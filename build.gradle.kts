plugins {
    kotlin("jvm") version "1.9.20"
    application
}

group = "com.backrooms"
version = "0.0.5"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(17)
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

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
