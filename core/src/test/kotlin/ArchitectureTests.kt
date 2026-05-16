import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertTrue
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

private val domainPackages = listOf("podcast", "playback", "queue", "settings")
private val allowedUseCaseImportPrefixes = listOf("java.", "kotlin.", "kotlinx.", "shared.", "io.github.oshai.")

class ArchitectureTests {

    // Rule 1: domain use cases only import from their own domain, shared, stdlib, and logging
    @Test
    fun `domain use cases do not import from other domains`() {
        Konsist.scopeFromProject()
            .files
            .withPackage("..core.usecase..")
            .filter { file -> domainPackages.any { file.packagee?.name?.startsWith(it) == true } }
            .assertTrue { file ->
                val domain = file.packagee!!.name.split(".").first()
                val otherDomains = domainPackages.filter { it != domain }
                file.imports.none { import ->
                    otherDomains.any { import.name.startsWith(it) }
                }
            }
    }

    // Rule 2: api layer does not import ports
    @Test
    fun `api layer does not import ports`() {
        Konsist.scopeFromProject()
            .files
            .withPackage("api..")
            .assertFalse { file ->
                file.imports.any { it.name.contains(".ports.") }
            }
    }

    // Rule 4: application layer does not import ports
    @Test
    fun `application layer does not import ports`() {
        Konsist.scopeFromProject()
            .files
            .withPackage("application..")
            .assertFalse { file ->
                file.imports.any { it.name.contains(".ports.") }
            }
    }

    // Rule 5: adapter classes implement a port interface
    @Test
    fun `adapter classes implement a port`() {
        Konsist.scopeFromProduction()
            .classes()
            .withPackage("..adapters..")
            .filter { !it.hasDataModifier }
            .assertTrue { klass ->
                val parentNames = klass.parentInterfaces().map { it.name }.toSet()
                klass.containingFile.imports.any { import ->
                    import.name.contains(".core.ports.") &&
                        parentNames.any { import.name.endsWith(".$it") }
                }
            }
    }

    // Rule 6: only interfaces and data classes live in port packages
    @Test
    fun `port packages contain only interfaces and data classes`() {
        Konsist.scopeFromProject()
            .classes()
            .withPackage("..core.ports..")
            .assertTrue { klass ->
                klass.hasDataModifier
            }
    }
}
