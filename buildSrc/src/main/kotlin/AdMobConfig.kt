import org.gradle.api.Project
import java.util.Properties

object AdMobConfig {
    fun getProperty(rootProject: Project, key: String, fallback: String = ""): String {
        val localProps = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localPropsFile.inputStream().use { stream -> localProps.load(stream) }
        }
        return localProps.getProperty(key)?.trim('"') ?: System.getenv(key) ?: fallback
    }
}
