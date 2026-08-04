package cast.android.service

/**
 * The slice of browse children a browser asked for. Browsers (Android Auto) request the browse tree
 * one page at a time and cache each page separately: answering every request with the whole list
 * left Auto holding a page 1 that was never replaced, so episodes cached there stayed on screen long
 * after the backend had dropped them from /recent, while page 0 kept picking up new ones.
 *
 * Past the end the page is empty, which is how a browser learns to stop asking. The offset is
 * computed as [Long] because browsers may pass a [Int.MAX_VALUE] page size to mean "everything".
 */
fun <T> List<T>.browsePage(page: Int, pageSize: Int): List<T> {
    val from = page.toLong() * pageSize.toLong()
    return if (from >= size) emptyList() else drop(from.toInt()).take(pageSize)
}
