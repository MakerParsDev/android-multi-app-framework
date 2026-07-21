import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class VerifyQualityGateTaskCoverage : DefaultTask() {
    @get:Input
    abstract val expectedAppLintTasks: ListProperty<String>

    @get:Input
    abstract val actualAppLintTasks: ListProperty<String>

    @get:Input
    abstract val expectedAppUnitTestTasks: ListProperty<String>

    @get:Input
    abstract val actualAppUnitTestTasks: ListProperty<String>

    @get:Input
    abstract val expectedLibraryLintTasks: ListProperty<String>

    @get:Input
    abstract val actualLibraryLintTasks: ListProperty<String>

    @get:Input
    abstract val expectedLibraryUnitTestTasks: ListProperty<String>

    @get:Input
    abstract val actualLibraryUnitTestTasks: ListProperty<String>

    @get:Input
    abstract val expectedVersionValidationTasks: ListProperty<String>

    @get:Input
    abstract val actualVersionValidationTasks: ListProperty<String>

    @get:Input
    abstract val testsDisabled: Property<Boolean>

    @TaskAction
    fun verifyTaskCoverage() {
        val failures = mutableListOf<String>()

        fun verifyExactTaskSet(label: String, expectedValues: List<String>, actualValues: List<String>) {
            val expected = expectedValues.toSortedSet()
            val actual = actualValues.toSortedSet()
            val missing = expected - actual
            val unexpected = actual - expected
            if (missing.isNotEmpty()) failures += "$label missing: ${missing.joinToString()}"
            if (unexpected.isNotEmpty()) failures += "$label unexpected: ${unexpected.joinToString()}"
        }

        verifyExactTaskSet("app lint", expectedAppLintTasks.get(), actualAppLintTasks.get())
        verifyExactTaskSet("app unit tests", expectedAppUnitTestTasks.get(), actualAppUnitTestTasks.get())
        verifyExactTaskSet("library lint", expectedLibraryLintTasks.get(), actualLibraryLintTasks.get())
        verifyExactTaskSet(
            "library unit tests",
            expectedLibraryUnitTestTasks.get(),
            actualLibraryUnitTestTasks.get(),
        )
        verifyExactTaskSet(
            "version validation",
            expectedVersionValidationTasks.get(),
            actualVersionValidationTasks.get(),
        )
        if (testsDisabled.get()) {
            failures += "-PdisableTests=true cannot be used with the blocking qualityCheck gate"
        }

        logger.lifecycle("Quality gate task coverage:")
        logger.lifecycle("  app flavors: ${expectedAppLintTasks.get().distinct().size}")
        logger.lifecycle("  app lint tasks: ${actualAppLintTasks.get().distinct().size}")
        logger.lifecycle("  app unit-test tasks: ${actualAppUnitTestTasks.get().distinct().size}")
        logger.lifecycle("  Android library modules: ${expectedLibraryLintTasks.get().distinct().size}")
        logger.lifecycle("  library lint tasks: ${actualLibraryLintTasks.get().distinct().size}")
        logger.lifecycle("  library unit-test tasks: ${actualLibraryUnitTestTasks.get().distinct().size}")
        logger.lifecycle("  version validation tasks: ${actualVersionValidationTasks.get().distinct().size}")

        if (failures.isNotEmpty()) {
            throw GradleException(
                "Quality gate task discovery is incomplete:\n" + failures.joinToString(separator = "\n"),
            )
        }
    }
}
