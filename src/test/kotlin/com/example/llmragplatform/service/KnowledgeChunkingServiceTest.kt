package com.example.llmragplatform.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KnowledgeChunkingServiceTest {

    @Test
    fun `chunk splits long content with overlap`() {
        val content = (0 until 220).joinToString("") { (it % 10).toString() }

        val chunks = KnowledgeChunkingService().chunk(content, chunkSize = 100, overlap = 20)

        assertEquals(3, chunks.size)
        assertEquals(100, chunks[0].length)
        assertEquals(100, chunks[1].length)
        assertEquals(60, chunks[2].length)
    }
}
