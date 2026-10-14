package dev.jellude.core

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class VerifiedTransferTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var transfer: VerifiedTransfer
    @Before fun setup() {
        server = MockWebServer(); server.start()
        transfer = VerifiedTransfer(OkHttpClient.Builder().retryOnConnectionFailure(false).readTimeout(2, TimeUnit.SECONDS).build())
    }
    @After fun teardown() { server.shutdown() }
    private fun request() = Request.Builder().url(server.url("/Items/id/Download")).build()
    private fun partial(contents: String = ""): File = temp.newFile().apply { writeText(contents) }
    private fun tag(file: File, value: String = "\"version-1\"") { File(file.path + ".etag").writeText(value) }
    private fun fails(block: () -> Unit) {
        try { block(); fail("Expected an IOException") } catch (_: IOException) { }
    }
    @Test fun successfulDownloadProducesKnownHashAndBytes() {
        server.enqueue(MockResponse().setBody("abc"))
        val file = partial()
        val result = transfer.download(request(), file)
        assertEquals(3L, result.bytes)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result.sha256)
    }
    @Test fun rangeIgnoredRestartsRatherThanAppending() {
        val file = partial("abc"); tag(file)
        server.enqueue(MockResponse().setBody("abcdef").setHeader("ETag", "\"version-2\""))
        transfer.download(request(), file)
        assertEquals("abcdef", file.readText())
        assertEquals("bytes=3-", server.takeRequest().getHeader("Range"))
    }
    @Test fun strongValidatorResumesAtExactOffset() {
        val file = partial("abc"); tag(file)
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 3-5/6")
            .setHeader("ETag", "\"version-1\"").setBody("def"))
        transfer.download(request(), file)
        assertEquals("abcdef", file.readText())
        assertEquals("\"version-1\"", server.takeRequest().getHeader("If-Range"))
    }
    @Test fun weakValidatorForcesCleanRestart() {
        val file = partial("garbage"); tag(file, "W/\"version-1\"")
        server.enqueue(MockResponse().setBody("abc"))
        transfer.download(request(), file)
        assertNull(server.takeRequest().getHeader("Range"))
        assertEquals("abc", file.readText())
    }
    @Test fun wrongRangeCannotCorruptExistingPartial() {
        val file = partial("abc"); tag(file)
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 2-4/5").setBody("def"))
        fails { transfer.download(request(), file) }
        assertEquals("abc", file.readText())
    }
    @Test fun changedValidatorCannotAppendDifferentSource() {
        val file = partial("abc"); tag(file)
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 3-5/6")
            .setHeader("ETag", "\"new-source\"").setBody("def"))
        fails { transfer.download(request(), file) }
        assertEquals("abc", file.readText())
    }
    @Test fun shortRangeIsNotCompleteEvenWhenResponseFinishes() {
        val file = partial("abc"); tag(file)
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 3-4/6")
            .setHeader("ETag", "\"version-1\"").setBody("de"))
        fails { transfer.download(request(), file) }
    }
    @Test fun htmlErrorCannotBecomeDownloadedAudio() {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>login</html>"))
        fails { transfer.download(request(), partial()) }
    }
    @Test fun interruptedBodyDoesNotProduceSuccess() {
        server.enqueue(MockResponse().setBody("a".repeat(10000)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        fails { transfer.download(request(), partial()) }
    }
    @Test fun emptyResponseIsRejected() {
        server.enqueue(MockResponse().setBody(""))
        fails { transfer.download(request(), partial()) }
    }
    @Test fun permissionFailureIsStructured() {
        server.enqueue(MockResponse().setResponseCode(403))
        try { transfer.download(request(), partial()); fail("Expected permission failure") }
        catch (error: TransferFailure) { assertEquals(403, error.status) }
    }
    @Test fun cancellationLeavesAnIncompleteFile() {
        server.enqueue(MockResponse().setBody("abc"))
        fails { transfer.download(request(), partial()) { true } }
    }
}
