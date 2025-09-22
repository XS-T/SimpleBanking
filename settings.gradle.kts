val project_name: String by settings

rootProject.name = project_name

pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
		maven("https://repo.papermc.io/repository/maven-public/")
	}
}
