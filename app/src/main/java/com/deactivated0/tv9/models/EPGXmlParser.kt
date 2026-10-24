package com.deactivated0.tv9.models

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale


class EPGXmlParser {

    private val ns: String? = null

    private val epg = HashMap<String, MutableList<EPG>>(64)
    private val fallbackDateFormat by lazy {
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    }

    /**
     * Fast-path parser for XMLTV timestamp format "yyyyMMddHHmmss Z" (e.g. "20261024080000 +0800").
     * Avoids allocating a new SimpleDateFormat and Date object on every <programme> tag.
     */
    private fun formatFTime(s: String?): Int {
        if (s.isNullOrEmpty()) return 0
        if (s.length >= 14) {
            var validDigits = true
            for (i in 0 until 14) {
                val c = s[i]
                if (c < '0' || c > '9') {
                    validDigits = false
                    break
                }
            }
            if (validDigits) {
                val year = (s[0] - '0') * 1000 + (s[1] - '0') * 100 + (s[2] - '0') * 10 + (s[3] - '0')
                val month = (s[4] - '0') * 10 + (s[5] - '0')
                val day = (s[6] - '0') * 10 + (s[7] - '0')
                val hour = (s[8] - '0') * 10 + (s[9] - '0')
                val minute = (s[10] - '0') * 10 + (s[11] - '0')
                val second = (s[12] - '0') * 10 + (s[13] - '0')

                // Days from 1970-01-01 using civil_from_days algorithm (Howard Hinnant)
                val y = if (month <= 2) year - 1 else year
                val era = if (y >= 0) y / 400 else (y - 399) / 400
                val yoe = y - era * 400
                val m = if (month > 2) month - 3 else month + 9
                val doy = (153 * m + 2) / 5 + day - 1
                val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
                val days = era * 146097 + doe - 719468

                var epochSeconds = days * 86400L + hour * 3600L + minute * 60L + second

                // Parse optional timezone offset e.g. " +0800" or "-0500"
                var tzIdx = 14
                while (tzIdx < s.length && s[tzIdx] == ' ') tzIdx++
                if (tzIdx + 4 < s.length) {
                    val sign = s[tzIdx]
                    if ((sign == '+' || sign == '-') &&
                        s[tzIdx + 1] in '0'..'9' && s[tzIdx + 2] in '0'..'9' &&
                        s[tzIdx + 3] in '0'..'9' && s[tzIdx + 4] in '0'..'9'
                    ) {
                        val tzHours = (s[tzIdx + 1] - '0') * 10 + (s[tzIdx + 2] - '0')
                        val tzMins = (s[tzIdx + 3] - '0') * 10 + (s[tzIdx + 4] - '0')
                        val tzOffsetSeconds = tzHours * 3600L + tzMins * 60L
                        if (sign == '+') {
                            epochSeconds -= tzOffsetSeconds
                        } else {
                            epochSeconds += tzOffsetSeconds
                        }
                        return epochSeconds.toInt()
                    }
                }
            }
        }
        return try {
            val date = fallbackDateFormat.parse(s)
            if (date != null) (date.time / 1000).toInt() else 0
        } catch (e: Exception) {
            0
        }
    }

    fun parse(inputStream: InputStream): MutableMap<String, MutableList<EPG>> {
        java.io.BufferedInputStream(inputStream, 32768).use { input ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, null)
            parser.nextTag()
            var channel = ""
            var currentList: MutableList<EPG>? = null
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "channel" -> {
                            parser.nextTag()
                            channel = parser.nextText()
                            currentList = ArrayList()
                            epg[channel] = currentList
                        }
                        "programme" -> {
                            val start = parser.getAttributeValue(ns, "start")
                            parser.nextTag()
                            val title = parser.nextText()
                            val list = if (currentList != null) {
                                currentList
                            } else {
                                epg[channel]
                            }
                            list?.add(EPG(title, formatFTime(start)))
                        }
                    }
                }
                eventType = parser.next()
            }
        }
        return epg
    }
}