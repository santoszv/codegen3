allprojects {
    group = "mx.com.inftel.codegen3"
    version = "3.0.0-SNAPSHOT"
}

plugins {
    kotlin("jvm") version "1.8.20"
    `java-gradle-plugin`
    `maven-publish`
    signing
}

dependencies {
    compileOnly(gradleApi())
    implementation(project(":model"))
    testImplementation(kotlin("test"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
    withSourcesJar()
}

kotlin {
    jvmToolchain(11)
}

gradlePlugin {
    plugins {
        create("codegen3") {
            id = "mx.com.inftel.codegen3"
            implementationClass = "mx.com.inftel.codegen.plugin.CodegenPlugin"
        }
    }
}

val kotlinJavadoc by tasks.registering(Jar::class) {
    archiveBaseName.set(project.name)
    archiveClassifier.set("javadoc")
    from(file("$projectDir/javadoc/README"))
}

tasks.test {
    useJUnitPlatform()
}

afterEvaluate {
    publishing {
        repositories {
            maven {
                setUrl(file("$projectDir/build/repo"))
            }
        }

        publications.withType(MavenPublication::class) {
            if (!name.contains("marker", ignoreCase = true)) {
                artifact(kotlinJavadoc)
            }
            pom {
                name.set("${project.group}:${project.name}")
                description.set("Codegen3 Gradle Plugin")
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

    signing {
        useGpgCmd()
        sign(publishing.publications["pluginMaven"])
        sign(publishing.publications["codegen3PluginMarkerMaven"])
    }

    tasks.named("publishCodegen3PluginMarkerMavenPublicationToMavenRepository").configure {
        this.mustRunAfter("signPluginMavenPublication")
    }

    tasks.named("publishPluginMavenPublicationToMavenRepository").configure {
        this.mustRunAfter("signCodegen3PluginMarkerMavenPublication")
    }

}