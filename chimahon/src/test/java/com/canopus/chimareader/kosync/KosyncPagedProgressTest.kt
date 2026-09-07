package com.canopus.chimareader.kosync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KosyncPagedProgressTest {
    @Test
    fun `encodes the page the way KOReader does for paged documents`() {
        assertEquals("1", KosyncPagedProgress.progress(0, 200))
        assertEquals("57", KosyncPagedProgress.progress(56, 200))
        assertEquals("200", KosyncPagedProgress.progress(250, 200))
        assertEquals(0.285, KosyncPagedProgress.percentage(56, 200), 1e-9)
        assertEquals(1.0, KosyncPagedProgress.percentage(199, 200), 1e-9)
        assertEquals(0.0, KosyncPagedProgress.percentage(3, 0), 1e-9)
    }

    @Test
    fun `decodes a KOReader page number, falling back to the percentage`() {
        assertEquals(56, KosyncPagedProgress.pageIndex("57", 0.285, 200))
        assertEquals(56, KosyncPagedProgress.pageIndex("57.0", null, 200))
        assertEquals(56, KosyncPagedProgress.pageIndex(null, 0.285, 200))
        assertEquals(56, KosyncPagedProgress.pageIndex("/body/DocFragment[3]/body/p[1]", 0.285, 200))
        assertEquals(199, KosyncPagedProgress.pageIndex("999", 1.0, 200))
        assertEquals(0, KosyncPagedProgress.pageIndex("0", 0.0, 200))
        assertNull(KosyncPagedProgress.pageIndex(null, null, 200))
        assertNull(KosyncPagedProgress.pageIndex("12", 0.5, 0))
    }

    @Test
    fun `a pushed page decodes to the same index`() {
        for (index in listOf(0, 1, 99, 199)) {
            val progress = KosyncPagedProgress.progress(index, 200)
            val percentage = KosyncPagedProgress.percentage(index, 200)
            assertEquals(index, KosyncPagedProgress.pageIndex(progress, percentage, 200))
            assertEquals(index, KosyncPagedProgress.pageIndex(null, percentage, 200))
        }
    }
}
