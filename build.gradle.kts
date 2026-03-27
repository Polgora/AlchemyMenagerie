/**
 * NOTE: This is entirely optional and basics can be done in `settings.gradle.kts`
 */

// Read version from manifest.json
val manifestFile = file("src/main/resources/manifest.json")
val manifestJson = groovy.json.JsonSlurper().parse(manifestFile) as Map<*, *>
version = manifestJson["Version"] as String

repositories {
    // Any external repositories besides: MavenLocal, MavenCentral, HytaleMaven, and CurseMaven
}

dependencies {
    // Any external dependency you also want to include
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}