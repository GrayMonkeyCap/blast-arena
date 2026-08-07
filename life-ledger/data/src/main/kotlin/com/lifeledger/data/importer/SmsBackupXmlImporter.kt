package com.lifeledger.data.importer

import android.util.Xml
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.ingest.SmsReader
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/**
 * Reads the XML export produced by the popular "SMS Backup & Restore" app:
 * `<smses count="N"><sms protocol="0" address="HDFCBK" date="…" type="1" body="…" .../>…</smses>`.
 *
 * Uses [XmlPullParser] in pull mode rather than a DOM parser (or a hand-rolled reader — XML
 * unlike CSV is not something worth re-deriving by hand, and [XmlPullParser] ships on every
 * Android device with zero extra dependency) so a backup with hundreds of thousands of messages
 * is processed one `<sms>` element at a time: each element is a handful of attributes with no
 * child text, so [XmlPullParser.next] never has to buffer more than the current tag.
 *
 * Only received messages (`type="1"`) become [SmsRecord]s — sent messages (`type="2"`) are
 * counted as skipped rather than imported, because Life Ledger only ever reasons about messages
 * the user received, exactly like [SmsReader] reading the live provider.
 */
@Singleton
class SmsBackupXmlImporter @Inject constructor() : DataImporter<SmsRecord> {

    override val format: ImportFormat = ImportFormat.SMS_BACKUP_XML

    override fun canHandle(fileName: String, header: ByteArray): Boolean {
        if (fileName.substringAfterLast('.', "").lowercase() == "xml") return true
        val text = String(header, StandardCharsets.UTF_8).trimStart('﻿').trim()
        return text.startsWith("<?xml") || text.startsWith("<smses")
    }

    override suspend fun import(input: InputStream, options: ImportOptions): ImportResult<SmsRecord> =
        withContext(Dispatchers.IO) {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            // Encoding is left to be auto-detected from the XML declaration/BOM; "SMS Backup &
            // Restore" writes UTF-8 but some other exporters of the same schema use UTF-16.
            parser.setInput(input, null)

            val items = mutableListOf<SmsRecord>()
            val rowErrors = mutableListOf<RowError>()
            val seenFingerprints = HashSet<String>()
            var imported = 0
            var skipped = 0
            var failed = 0
            var elementIndex = 0

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "sms") {
                    elementIndex++
                    if (elementIndex % 2000 == 0) coroutineContext.ensureActive()

                    val address = parser.getAttributeValue(null, "address")
                    val body = parser.getAttributeValue(null, "body")
                    val dateAttr = parser.getAttributeValue(null, "date")
                    val typeAttr = parser.getAttributeValue(null, "type") ?: "1"

                    when {
                        address == null || body == null || dateAttr == null -> {
                            failed++
                            rowErrors += RowError(
                                elementIndex,
                                "Missing address/body/date attribute",
                                describeAttributes(parser),
                            )
                        }
                        typeAttr != RECEIVED_TYPE -> skipped++
                        else -> {
                            val millis = dateAttr.toLongOrNull()
                            if (millis == null) {
                                failed++
                                rowErrors += RowError(elementIndex, "Non-numeric date '$dateAttr'", describeAttributes(parser))
                            } else {
                                val fingerprint = SmsReader.fingerprintOf(address, body, millis)
                                if (options.skipDuplicates && !seenFingerprints.add(fingerprint)) {
                                    skipped++
                                } else {
                                    items += SmsRecord(
                                        fingerprint = fingerprint,
                                        sender = address,
                                        body = body,
                                        receivedAt = Instant.ofEpochMilli(millis),
                                    )
                                    imported++
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            ImportResult(items, imported, skipped, failed, emptyList(), rowErrors)
        }

    private fun describeAttributes(parser: XmlPullParser): String =
        (0 until parser.attributeCount).joinToString(" ") { i ->
            "${parser.getAttributeName(i)}=\"${parser.getAttributeValue(i)}\""
        }

    private companion object {
        const val RECEIVED_TYPE = "1"
    }
}
