package cast.android.work

import org.junit.Test

/**
 * @HiltWorker is processed by androidx.hilt:hilt-compiler, not Dagger's hilt-compiler. When that
 * KSP processor is missing the workers still compile, but nothing binds them into
 * HiltWorkerFactory's map — WorkManager then falls back to reflection, finds no
 * (Context, WorkerParameters) constructor, and the work never runs. Nothing else fails loudly,
 * so assert the generated bindings exist.
 */
class HiltWorkerBindingTest {

    @Test
    fun `every HiltWorker has its generated Hilt bindings`() {
        listOf(RefreshFeedsWorker::class.java, DownloadCleanupWorker::class.java).forEach { worker ->
            listOf("_AssistedFactory", "_HiltModule").forEach { suffix ->
                val generated = worker.name + suffix
                try {
                    Class.forName(generated)
                } catch (e: ClassNotFoundException) {
                    throw AssertionError(
                        "$generated was not generated — androidx.hilt:hilt-compiler is missing " +
                            "from the KSP classpath, so ${worker.simpleName} cannot be " +
                            "instantiated at runtime.",
                        e,
                    )
                }
            }
        }
    }
}
