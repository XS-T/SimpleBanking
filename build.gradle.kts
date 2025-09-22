plugins {
	java
	id("org.jetbrains.kotlin.jvm") version "2.0.21"
	id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
	id("com.github.johnrengelman.shadow") version "7.1.2"
	id("io.papermc.paperweight.userdev") version "2.0.0-beta.17"
	id("xyz.jpenilla.run-paper") version "2.3.1"
}

val paper_version: String by project
val project_version: String by project
val minecraft_version: String by project
val project_name: String by project
val project_package: String by project
val project_display_name: String by project
val project_plugin_class: String by project
val project_owners: String by project

group = project_package
version = project_version

repositories {
	google()
	mavenCentral()
	maven {
		name = "papermc-repo"
		url = uri("https://repo.papermc.io/repository/maven-public/")
	}

	maven {
		name = "sonatype"
		url = uri("https://oss.sonatype.org/content/groups/public/")
	}

	maven {
		name = "jitpack"
		url = uri("https://jitpack.io")
	}

	maven {
		name = "sonatype-snapshots"
		url = uri("https://oss.sonatype.org/content/repositories/snapshots")
	}

	maven {
		name = "apache"
		url = uri("https://repo.maven.apache.org/maven2/")
	}

	maven {
		name = "spigotmc"
		url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
	}

	maven {
		name = "codemc"
		url = uri("https://repo.codemc.org/repository/maven-public/")
	}

	/*
	maven {
		name = "totallyavirus"
		url = uri("https://repo.totallyavir.us/maven-public")
	}
	*/

	maven {
		name = "sponge"
		url = uri("https://repo.spongepowered.org/repository/maven-public/")
	}

	maven{
		name = "VaultUnlocked"
		url = uri("https://repo.codemc.io/repository/creatorfromhell/")
	}
}

dependencies {
	// Paper
	paperweight.paperDevBundle(paper_version)
	compileOnly("io.papermc.paper:paper-api:$paper_version")

	//LuckPerms
	compileOnly("net.luckperms:api:5.4")

	//MySQL
	implementation("mysql:mysql-connector-java:8.0.28")
	implementation("com.zaxxer:HikariCP:7.0.2")

	// Vault
	compileOnly("net.milkbowl.vault:VaultUnlockedAPI:2.15")

/*
	// Kotlin
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.22")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4")
*/
	// Kotlin
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.10")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")

	// Kotlin Coroutine for Bukkit
	implementation("com.github.shynixn.mccoroutine:mccoroutine-bukkit-api:2.20.0")
	implementation("com.github.shynixn.mccoroutine:mccoroutine-bukkit-core:2.20.0")

	// Dependency Injection
	implementation("com.google.inject:guice:7.0.0")
	implementation("dev.misfitlabs.kotlinguice4:kotlin-guice:3.0.0")

	// Database
	implementation("com.github.jasync-sql:jasync-mysql:2.1.7")

	// Command Framework
	implementation("org.incendo:cloud-paper:2.0.0-beta.10")
	implementation("org.incendo:cloud-bukkit:2.0.0-beta.10")

	implementation("org.incendo:cloud-annotations:2.0.0")
	implementation("org.incendo:cloud-minecraft-extras:2.0.0-beta.10")
	implementation("org.incendo:cloud-kotlin-coroutines:2.0.0")
	implementation("org.incendo:cloud-kotlin-coroutines-annotations:2.0.0")
	implementation("org.incendo:cloud-kotlin-extensions:2.0.0")
	annotationProcessor("org.incendo:cloud-annotations:2.0.0")

	// Adventure
	implementation("net.kyori:adventure-platform-bukkit:4.2.0")
	implementation("net.kyori:adventure-extra-kotlin:4.12.0")
}

java.sourceCompatibility = JavaVersion.VERSION_21
java.targetCompatibility = JavaVersion.VERSION_21

tasks {
	assemble {
		dependsOn(reobfJar)
	}

	javadoc {
		options.encoding = Charsets.UTF_8.name()
	}
	processResources {
		filteringCharset = Charsets.UTF_8.name()
	}

	withType<JavaCompile> {
		options.encoding = "UTF-8"
	}

	compileKotlin {
		kotlinOptions {
			jvmTarget = "21"
		}
	}

	shadowJar {
		archiveFileName.set("$project_name.jar")
		mergeServiceFiles()
	}

	build {
		dependsOn(shadowJar)
	}

	runServer {

		minecraftVersion(minecraft_version)
		jvmArgs("-Dcom.mojang.eula.agree=true")
	}
}

bukkit {
	name = project_display_name
	version = project_version
	authors = listOf("CrewCo Team", *project_owners.split(",").toTypedArray())
	main = "$project_package.$project_plugin_class"
	apiVersion = "1.21"
}