import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertTrue
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

// Discovered, not hand-listed: a domain is any top-level package that declares ports.
// A new domain is covered by these rules the moment it exists.
private val domainPackages: List<String> =
    Konsist.scopeFromProduction()
        .files
        .mapNotNull { it.packagee?.name }
        .filter { it.contains(".core.ports") }
        .map { it.substringBefore(".") }
        .distinct()
        .sorted()

private val allowedUseCaseImportPrefixes = listOf("java.", "kotlin.", "kotlinx.", "shared.", "io.github.oshai.")
private val frameworkPackages = listOf("io.ktor.", "org.xerial.")

class ArchitectureTests {

    // Rule 0: every discovered domain is wired into Application.module()
    @Test
    fun `every domain is installed by the application`() {
        check(domainPackages.isNotEmpty()) { "No domain packages discovered — the scan is broken" }
        val module = Konsist.scopeFromProduction()
            .functions()
            .first { it.name == "module" && it.containingFile.name == "Application" }
        domainPackages.forEach { domain ->
            val install = "install${domain.replaceFirstChar(Char::uppercaseChar)}Module()"
            check(module.text.contains(install)) { "Application.module() never calls $install" }
        }
    }

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

    // Rule A: core does not depend on adapters
    @Test
    fun `core does not depend on adapters`() {
        Konsist.scopeFromProject()
            .files
            .withPackage("..core..")
            .assertFalse { file ->
                file.imports.any { it.name.contains(".adapters.") }
            }
    }

    // Rule B: core does not depend on framework
    @Test
    fun `core does not depend on framework`() {
        Konsist.scopeFromProject()
            .files
            .withPackage("..core..")
            .assertFalse { file ->
                file.imports.any { import ->
                    frameworkPackages.any { import.name.startsWith(it) }
                }
            }
    }

    // Rule C: adapters do not depend on other adapters
    @Test
    fun `adapters do not depend on other adapters`() {
        Konsist.scopeFromProduction()
            .files
            .withPackage("..adapters..")
            .assertFalse { file ->
                val domain = file.packagee!!.name.split(".").first()
                file.imports.any { import ->
                    import.name.contains(".adapters.") && !import.name.startsWith("$domain.")
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
