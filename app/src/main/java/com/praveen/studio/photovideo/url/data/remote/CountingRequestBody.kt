package com.praveen.studio.photovideo.url.data.remote

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import java.io.IOException

class CountingRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (bytesWritten: Long, totalBytes: Long, speedKBps: Double) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long {
        return try {
            delegate.contentLength()
        } catch (_: IOException) {
            -1L
        }
    }

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        val countingSink = CountingSink(sink)
        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }

    private inner class CountingSink(delegate: Sink) : ForwardingSink(delegate) {
        private var bytesWritten = 0L
        private val startTime = System.currentTimeMillis()

        @Throws(IOException::class)
        override fun write(source: Buffer, byteCount: Long) {
            super.write(source, byteCount)
            bytesWritten += byteCount
            val total = contentLength()
            val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
            val speedKBps = if (elapsedSec > 0.1) (bytesWritten / 1024.0) / elapsedSec else 0.0
            onProgress(bytesWritten, total, speedKBps)
        }
    }
}
