import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.tasks.BintrayUploadTask

group = "org.jetbrains"
version = "1.0-SNAPSHOT"

plugins {
    id("kotlin2js") version Versions["kotlin_version"] apply false
    id("com.jfrog.bintray") version Versions["bintray_plugin_version"] apply true
    id("com.moowork.node") version Versions["node_plugin_version"] apply false
    `maven-publish`
    java
}

buildscript {
    repositories {
        maven(url = "https://dl.bintray.com/kotlin/kotlin-dev")
        maven(url = "https://kotlin.bintray.com/kotlinx")
        gradlePluginPortal()
        mavenCentral()
        jcenter()
    }

    dependencies {
        classpath(Dependencies.kotlinGradlePlugin)
        classpath(Dependencies.kotlinSerialization)
        classpath(Dependencies.gradleNodePlugin)
        classpath(Dependencies.gradleBintrayPlugin)
    }
}

allprojects {
    repositories {
        maven(url = "https://dl.bintray.com/kotlin/kotlin-dev")
        maven(url = "https://kotlin.bintray.com/kotlinx")
        maven(url = "https://plugins.gradle.org/m2/")
        mavenCentral()
        jcenter()
        maven(url = "http://dl.bintray.com/kotlin/kotlin-js-wrappers")
    }
}

configure(subprojects.filter { !it.name.startsWith("kotlin-css") }) {
    apply {
        plugin("kotlin2js")
    }

    dependencies {
        compile(kotlin("stdlib-js"))
        compile(Dependencies.kotlinxHtmlJs)
    }

    val projectName = name
    val compileKotlin2Js = tasks.getByName<org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile>("compileKotlin2Js")
    compileKotlin2Js.apply {
        kotlinOptions {
            outputFile = "$projectDir/build/classes/main/$projectName.js"
            moduleKind = "commonjs"
            sourceMap = true
            sourceMapEmbedSources = "always"
        }
    }
}

configure(subprojects.filter { it.name != "examples"}) {
    apply {
        plugin("com.moowork.node")
        plugin("com.jfrog.bintray")
        plugin("maven-publish")
        plugin("java")
    }

    val publicationName = "Publication"
    val projectName = name
    val projectVersion = "${projectVersion()}-kotlin-${Versions["kotlin_version"]}"

    tasks.withType<BintrayUploadTask>().configureEach {
        extension = BintrayExtension(project).apply {
            apiUrl = "https://api.bintray.com"
            user = System.getenv("BINTRAY_USER")
            key = System.getenv("BINTRAY_KEY")

            publish = true

            pkg(delegateClosureOf<com.jfrog.bintray.gradle.BintrayExtension.PackageConfig> {
                userOrg = "kotlin"
                repo = "kotlin-js-wrappers"
                setPublications(publicationName)
                name = projectName
                vcsUrl = "https://github.com/JetBrains/kotlin-wrappers.git"
                githubRepo = "JetBrains/kotlin-wrappers"
                setLicenses("Apache-2.0")

                version(delegateClosureOf<com.jfrog.bintray.gradle.BintrayExtension.VersionConfig> {
                    name = projectVersion
                })
            })
        }
    }

    tasks {
        val sourcesJar by creating(Jar::class) {
            dependsOn(JavaPlugin.CLASSES_TASK_NAME)
            classifier = "sources"
            // java.sourceSets -> sourceSets in gradle 4.10+
            from(java.sourceSets["main"].allSource)
        }

        artifacts {
            add("archives", sourcesJar)
        }

        publishing {
            publications.invoke {
                // Doesn't work in Gradle 4.10+
                // https://github.com/bintray/gradle-bintray-plugin/issues/261
                publicationName(MavenPublication::class) {
                    from(components.findByName("java"))
                    groupId = rootProject.group as String
                    artifactId = projectName
                    artifact(sourcesJar)
                    version = projectVersion
                }
            }
        }

        val processPkg by creating(Copy::class) {
            from(".")
            into("build/npm")
            include("package.json")
            expand(Versions)
        }

        val prepublish by creating(Copy::class) {
            from(".")
            into("build/npm")
            exclude("package.json")
            exclude("build/npm")

            dependsOn("build")
        }

        val npm_publish by creating(com.moowork.gradle.node.npm.NpmTask::class) {
            setArgs(listOf("publish", "--access", "public"))
            setWorkingDir(file("${project.buildDir}/npm"))

            dependsOn("processPkg", "prepublish")
        }
    }
}
