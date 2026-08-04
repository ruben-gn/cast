package cast.android.service

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowsePageTest {

    private val children = (1..25).toList()

    @Test fun `each page holds its own slice of the children`() {
        assertEquals((1..10).toList(), children.browsePage(page = 0, pageSize = 10))
        assertEquals((11..20).toList(), children.browsePage(page = 1, pageSize = 10))
    }

    @Test fun `the last page is short rather than padded`() =
        assertEquals((21..25).toList(), children.browsePage(page = 2, pageSize = 10))

    @Test fun `a page past the end is empty so the browser stops asking`() =
        assertEquals(emptyList<Int>(), children.browsePage(page = 3, pageSize = 10))

    @Test fun `a browser asking for everything gets everything`() =
        assertEquals(children, children.browsePage(page = 0, pageSize = Int.MAX_VALUE))
}
