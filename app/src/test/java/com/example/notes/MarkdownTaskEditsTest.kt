package com.example.notes

import org.junit.Assert.*
import org.junit.Test

class MarkdownTaskEditsTest {
    @Test fun changesOneCharacterWithoutNormalizingMixedEndings() {
        val body = "# 😀\r\n- [ ] first\r- [X] second\nlast"
        val updated = MarkdownTaskEdits.toggle(body, 1)
        assertEquals(body.replace("[X]", "[ ]"), updated)
        assertEquals(1, body.indices.count { body[it] != updated[it] })
        assertEquals(body, MarkdownTaskEdits.toggle(updated, 1).replace("[x]", "[X]"))
    }
    @Test fun skipsTildeBacktickAndIndentedCodeAndFindsQuotedOrderedTasks() {
        val body = "~~~md\n- [ ] literal\n~~~\n\n````\n```\n- [ ] literal\n````\n\n    - [ ] indented code\n\n> 1) [ ] actual😀\n"
        assertEquals(body.replace("[ ] actual", "[x] actual"), MarkdownTaskEdits.toggle(body, 0))
        assertEquals(body, MarkdownTaskEdits.toggle(body, 1))
    }
    @Test fun nestedTasksFollowDocumentOrder() {
        val body = "- [ ] parent\n  - [x] nested\n- [ ] last\n"
        assertEquals(body.replace("[x] nested", "[ ] nested"), MarkdownTaskEdits.toggle(body, 1))
        assertEquals(body, MarkdownTaskEdits.toggle(body, -1))
    }
}
