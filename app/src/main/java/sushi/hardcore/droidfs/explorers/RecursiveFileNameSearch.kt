package sushi.hardcore.droidfs.explorers

import java.util.ArrayDeque
import java.util.Locale

/**
 * Builds a filename-only index below a directory and filters it for the explorer search UI.
 *
 * The traversal is iterative so deeply nested directories do not consume the call stack. A
 * caller can stop the traversal between directory reads through [isActive]. Directories that
 * cannot be read are skipped so an inaccessible subtree does not hide results from the rest of
 * the volume.
 */
internal class RecursiveFileNameSearch(
    private val readDirectory: (String) -> List<ExplorerElement>?,
) {
    data class Index(
        val elements: List<ExplorerElement>,
        val unreadableDirectoryCount: Int,
    )

    /**
     * Returns null when [isActive] requests cancellation. The root directory itself is not a
     * result; every visible descendant is included exactly once.
     */
    fun buildIndex(rootPath: String, isActive: () -> Boolean = { true }): Index? {
        val elements = mutableListOf<ExplorerElement>()
        val directoriesToVisit = ArrayDeque<String>()
        val visitedDirectories = mutableSetOf(rootPath)
        val seenElementPaths = mutableSetOf<String>()
        var unreadableDirectoryCount = 0

        directoriesToVisit.add(rootPath)
        while (directoriesToVisit.isNotEmpty()) {
            if (!isActive()) {
                return null
            }

            val directoryPath = directoriesToVisit.removeLast()
            val children = readDirectory(directoryPath)
            if (children == null) {
                unreadableDirectoryCount++
                continue
            }

            for (child in children) {
                if (!isActive()) {
                    return null
                }
                if (child.isParentFolder) {
                    continue
                }

                if (!seenElementPaths.add(child.fullPath)) {
                    continue
                }
                elements.add(child)
                if (child.isDirectory && visitedDirectories.add(child.fullPath)) {
                    directoriesToVisit.add(child.fullPath)
                }
            }
        }

        return Index(elements, unreadableDirectoryCount)
    }

    /** Matches only the filename, without using a path component. */
    fun filter(index: Index, query: String): List<ExplorerElement> {
        if (query.isEmpty()) {
            return index.elements
        }
        val normalizedQuery = query.lowercase(Locale.ROOT)
        return index.elements.filter { element ->
            element.name.lowercase(Locale.ROOT).contains(normalizedQuery)
        }
    }
}
