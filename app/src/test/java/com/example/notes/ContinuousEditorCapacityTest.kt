package com.example.notes

import org.junit.Assert.*
import org.junit.Test

class ContinuousEditorCapacityTest {
    @Test fun smallNotesFitAndHeapHeadroomIsRespected() {
        assertEquals(75L, ContinuousEditorCapacity.budget(200, 100))
        assertEquals(0L, ContinuousEditorCapacity.budget(100, 200))
        ContinuousEditorCapacity.check("one line😀".byteInputStream(), 1024)
    }
    @Test fun manyEmptyLinesAreRefusedBeforeConstructingMillionsOfNativeObjects() {
        assertThrows(EditorCapacityException::class.java) {
            ContinuousEditorCapacity.check("\n".repeat(10_000).byteInputStream(), 100_000)
        }
    }
    @Test fun crlfIsOneLineEndingAndCancellationIsHonored() {
        ContinuousEditorCapacity.check("a\r\nb\r\nc".byteInputStream(), 510)
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            ContinuousEditorCapacity.check("body".byteInputStream(), 1000) { throw java.util.concurrent.CancellationException() }
        }
    }
}
