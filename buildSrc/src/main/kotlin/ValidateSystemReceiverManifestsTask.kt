import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class ValidateSystemReceiverManifestsTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:Input
    abstract val pythonExecutable: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val validatorScript: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val zikirmatikManifest: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val namazvakitleriManifest: RegularFileProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun validate() {
        val report = reportFile.get().asFile
        report.parentFile.mkdirs()
        execOperations.exec {
            commandLine(
                pythonExecutable.get(),
                validatorScript.get().asFile.absolutePath,
                "--zikirmatik-manifest",
                zikirmatikManifest.get().asFile.absolutePath,
                "--namazvakitleri-manifest",
                namazvakitleriManifest.get().asFile.absolutePath,
                "--report",
                report.absolutePath,
            )
        }
    }
}
