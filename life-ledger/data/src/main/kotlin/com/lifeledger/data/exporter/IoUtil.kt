package com.lifeledger.data.exporter

import java.io.FilterOutputStream
import java.io.OutputStream

/**
 * Wraps [sink] to count bytes written without buffering them, so every exporter can report an
 * accurate [ExportSummary.bytesWritten] without a second pass over the output.
 */
internal class CountingOutputStream(sink: OutputStream) : FilterOutputStream(sink) {
    var count: Long = 0
        private set

    override fun write(b: Int) {
        out.write(b)
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        count += len
    }

    // Deliberately does not close [out]: the caller owns the sink's lifecycle (it may be a
    // ZipOutputStream entry or an encrypting stream that needs to finish after we're done).
    override fun close() {
        flush()
    }
}
