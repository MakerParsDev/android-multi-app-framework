import org.gradle.api.GradleException
import org.gradle.api.Project

fun Project.requiredToolchainProperty(name: String): String =
    providers.gradleProperty(name).orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("Missing required toolchain property: $name")

fun Project.requiredToolchainInt(name: String): Int =
    requiredToolchainProperty(name).toIntOrNull()
        ?: throw GradleException("Toolchain property $name must be an integer")
