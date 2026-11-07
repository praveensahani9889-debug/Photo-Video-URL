package com.praveen.studio.photovideo.url.data.remote

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.io.InputStream

/**
 * Streams a file to the network without loading it fully into memory
 * (the old code used readBytes(), which crashed on big videos / APKs).
 */
class StreamingRequestBody(
    private val openStream: () -> InputStream?,
    private val mediaType: MediaType?,
    private val length: Long
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = length

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        val input = openStream() ?: throw IOException("Unable to open file for upload")
        input.source().use { source ->
            sink.writeAll(source)
        }
    }
}
