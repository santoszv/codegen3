plugins {
    kotlin("jvm")
    `maven-publish`
    signing
}

dependencies {
    compileOnly(gradleApi())
    testImplementation(kotlin("test"))
}

java{
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
    withSourcesJar()
}

kotlin {
    jvmToolchain(11)
}

val kotlinJavadoc by tasks.registering(Jar::class) {
    archiveBaseName.set(project.name)
    archiveClassifier.set("javadoc")
    from(file("${rootProject.projectDir}/javadoc/README"))
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    repositories {
        maven {
            setUrl(file("${rootProject.projectDir}/build/repo"))
        }
    }

    publications {
        create<MavenPublication>("model") {
            artifact(kotlinJavadoc)
            from(components["java"])
            pom {
                name.set("${project.group}:${project.name}")
                description.set("Codegen3 Model")
                url.set("https://github.com/santoszv/codegen3")
                inceptionYear.set("2023")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("santoszv")
                        name.set("Santos Zatarain Vera")
                        email.set("santoszv@inftel.com.mx")
                        url.set("https://www.inftel.com.mx")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/santoszv/codegen3")
                    developerConnection.set("scm:git:https://github.com/santoszv/codegen3")
                    url.set("https://github.com/santoszv/codegen3")
                }
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["model"])
}