plugins {
    id("java")
    id("application")
}

group = "chat"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Ice dependencies
    implementation("com.zeroc:ice:3.7.10")
    implementation("com.zeroc:icestorm:3.7.10")
    
    // Testing dependencies
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

application {
    mainClass.set("ui.MainServer")
}

// Generar automáticamente clases Ice desde archivos .ice
tasks.register("generateIce") {
    doLast {
        val iceFiles = fileTree("src/main/java") {
            include("**/*.ice")
        }
        
        iceFiles.forEach { file ->
            val cmd = arrayOf(
                "slice2java",
                "-I" + file.parent,
                file.absolutePath
            )
            println("Generando: ${cmd.joinToString(" ")}")
            Runtime.getRuntime().exec(cmd).waitFor()
        }
    }
}

tasks.getByName("compileJava").dependsOn("generateIce")
