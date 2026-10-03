package sushi.hardcore.droidfs.explorers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sushi.hardcore.droidfs.filesystems.Stat

class RecursiveFileNameSearchTest {
    @Test
    fun `finds filename matches in nested directories`() {
        val search = RecursiveFileNameSearch(directoryReader(
            "/" to listOf(directory("documents", "/"), file("root.txt", "/")),
            "/documents" to listOf(directory("2026", "/documents"), file("Quarterly Report.pdf", "/documents")),
            "/documents/2026" to listOf(file("final-report.txt", "/documents/2026")),
        ))

        val index = search.buildIndex("/")!!

        assertEquals(
            listOf("/documents/Quarterly Report.pdf", "/documents/2026/final-report.txt"),
            search.filter(index, "REPORT").map { it.fullPath },
        )
    }

    @Test
    fun `matches filenames but not directory path components`() {
        val search = RecursiveFileNameSearch(directoryReader(
            "/" to listOf(directory("reports", "/")),
            "/reports" to listOf(file("budget.xlsx", "/reports")),
        ))

        val index = search.buildIndex("/")!!

        assertEquals(listOf("/reports"), search.filter(index, "reports").map { it.fullPath })
        assertEquals(emptyList<String>(), search.filter(index, "/reports").map { it.fullPath })
    }

    @Test
    fun `skips unreadable subtrees while preserving readable results`() {
        val search = RecursiveFileNameSearch(directoryReader(
            "/" to listOf(directory("available", "/"), directory("blocked", "/")),
            "/available" to listOf(file("needle.txt", "/available")),
        ))

        val index = search.buildIndex("/")!!

        assertEquals(1, index.unreadableDirectoryCount)
        assertEquals(listOf("/available/needle.txt"), search.filter(index, "needle").map { it.fullPath })
    }

    @Test
    fun `stops traversal when cancelled`() {
        var reads = 0
        val search = RecursiveFileNameSearch { path ->
            reads++
            if (path == "/") listOf(directory("child", "/")) else emptyList()
        }

        val index = search.buildIndex("/") { reads == 0 }

        assertNull(index)
        assertEquals(1, reads)
    }

    @Test
    fun `does not revisit a directory when a filesystem returns a cycle`() {
        val search = RecursiveFileNameSearch { path ->
            when (path) {
                "/" -> listOf(directory("loop", "/"))
                "/loop" -> listOf(directory("loop", ""), file("needle.txt", "/loop"))
                else -> emptyList()
            }
        }

        val index = search.buildIndex("/")!!

        assertEquals(listOf("/loop", "/loop/needle.txt"), index.elements.map { it.fullPath })
    }

    private fun directoryReader(vararg entries: Pair<String, List<ExplorerElement>>): (String) -> List<ExplorerElement>? {
        val directories = entries.toMap()
        return { path -> directories[path] }
    }

    private fun directory(name: String, parentPath: String) = ExplorerElement(
        name,
        Stat(Stat.S_IFDIR, 0, 0),
        parentPath,
    )

    private fun file(name: String, parentPath: String) = ExplorerElement(
        name,
        Stat(Stat.S_IFREG, 0, 0),
        parentPath,
    )
}
