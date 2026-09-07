package com.itantra.app.transport

import com.itantra.app.core.model.Language
import com.itantra.app.core.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageCoderTest {

    private val encoder = MessageEncoder()
    private val decoder = MessageDecoder()

    @Test
    fun `test message round trip with Hindi text`() {
        val originalMessage = Message(
            id = "123",
            sender = "UserA",
            language = Language.HINDI,
            text = "मुझे मदद चाहिए",
            timestamp = 1693245600000L
        )

        val encoded = encoder.encode(originalMessage)
        val result = decoder.decode(encoded)

        assertTrue("Decoding should be successful", result.isSuccess)
        assertEquals("Decoded message should match original", originalMessage, result.getOrNull())
    }

    @Test
    fun `test decode malformed JSON returns failure`() {
        val malformedData = "not a json".toByteArray(Charsets.UTF_8)
        val result = decoder.decode(malformedData)
        
        assertTrue("Decoding malformed data should fail", result.isFailure)
    }

    @Test
    fun `test decode missing fields returns failure`() {
        val incompleteJson = "{\"i\":\"123\"}".toByteArray(Charsets.UTF_8)
        val result = decoder.decode(incompleteJson)
        
        assertTrue("Decoding incomplete data should fail", result.isFailure)
    }
}
