package ai.drfx.scalperpro.market

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

sealed interface HistoricalCsvParseResult {
    data class Success(
        val candles: List<Candle>,
        val warnings: List<String>
    ) : HistoricalCsvParseResult

    data class Failure(
        val errors: List<String>
    ) : HistoricalCsvParseResult
}

object HistoricalCsvParser {
    private const val MAX_TEXT_CHARS =
        6_000_000
    private const val MAX_ROWS =
        50_000

    private val timestampAliases =
        setOf(
            "time",
            "timestamp",
            "datetime",
            "date",
            "opentime",
            "open_time"
        )

    private val formatters =
        listOf(
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter
                .ofPattern(
                    "yyyy-MM-dd HH:mm:ss"
                ),
            DateTimeFormatter
                .ofPattern(
                    "yyyy-MM-dd HH:mm"
                )
        )

    fun parse(
        text: String,
        symbol: String,
        timeframe: String
    ): HistoricalCsvParseResult {
        if (text.isBlank()) {
            return HistoricalCsvParseResult
                .Failure(
                    listOf(
                        "CSV file is empty."
                    )
                )
        }

        if (text.length > MAX_TEXT_CHARS) {
            return HistoricalCsvParseResult
                .Failure(
                    listOf(
                        "CSV file is too large for the local V1 importer."
                    )
                )
        }

        val lines =
            text
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toList()

        if (lines.size < 2) {
            return HistoricalCsvParseResult
                .Failure(
                    listOf(
                        "CSV needs a header plus at least one candle row."
                    )
                )
        }

        if (lines.size - 1 > MAX_ROWS) {
            return HistoricalCsvParseResult
                .Failure(
                    listOf(
                        "CSV exceeds the local V1 limit of " +
                            MAX_ROWS +
                            " candle rows."
                    )
                )
        }

        val delimiter =
            detectDelimiter(
                lines.first()
            )

        val header =
            split(
                lines.first(),
                delimiter
            ).map {
                normalizeHeader(it)
            }

        val index =
            HeaderIndex.from(header)
                ?: return HistoricalCsvParseResult
                    .Failure(
                        listOf(
                            "CSV header must include time/timestamp, open, high, low and close columns."
                        )
                    )

        val errors =
            mutableListOf<String>()
        val warnings =
            mutableListOf<String>()
        val candles =
            mutableListOf<Candle>()

        lines
            .drop(1)
            .forEachIndexed {
                    rowOffset,
                    line ->
                val rowNumber =
                    rowOffset + 2

                val columns =
                    split(
                        line,
                        delimiter
                    )

                val requiredMax =
                    maxOf(
                        index.time,
                        index.open,
                        index.high,
                        index.low,
                        index.close,
                        index.volume ?: -1
                    )

                if (
                    columns.size <=
                        requiredMax
                ) {
                    errors +=
                        "Row " +
                            rowNumber +
                            " has fewer columns than the header."
                    return@forEachIndexed
                }

                val timestamp =
                    parseTimestamp(
                        columns[index.time]
                    )

                val open =
                    parseNumber(
                        columns[index.open]
                    )
                val high =
                    parseNumber(
                        columns[index.high]
                    )
                val low =
                    parseNumber(
                        columns[index.low]
                    )
                val close =
                    parseNumber(
                        columns[index.close]
                    )

                if (
                    timestamp == null ||
                    open == null ||
                    high == null ||
                    low == null ||
                    close == null
                ) {
                    errors +=
                        "Row " +
                            rowNumber +
                            " contains an invalid time or OHLC value."
                    return@forEachIndexed
                }

                if (
                    !open.isFinite() ||
                    !high.isFinite() ||
                    !low.isFinite() ||
                    !close.isFinite()
                ) {
                    errors +=
                        "Row " +
                            rowNumber +
                            " contains a non-finite OHLC value."
                    return@forEachIndexed
                }

                if (
                    high <
                        maxOf(
                            open,
                            close
                        ) ||
                    low >
                        minOf(
                            open,
                            close
                        ) ||
                    high < low
                ) {
                    errors +=
                        "Row " +
                            rowNumber +
                            " has inconsistent OHLC bounds."
                    return@forEachIndexed
                }

                val volume =
                    index.volume
                        ?.let { volumeIndex ->
                            parseNumber(
                                columns[
                                    volumeIndex
                                ]
                            )
                        }

                candles +=
                    Candle(
                        symbol =
                            symbol.trim()
                                .ifBlank {
                                    "CUSTOM"
                                },
                        timeframe =
                            timeframe.trim()
                                .ifBlank {
                                    "custom"
                                },
                        openTimeEpochMillis =
                            timestamp,
                        open = open,
                        high = high,
                        low = low,
                        close = close,
                        volume = volume
                    )
            }

        if (errors.isNotEmpty()) {
            return HistoricalCsvParseResult
                .Failure(
                    errors.take(24)
                )
        }

        if (candles.size < 2) {
            return HistoricalCsvParseResult
                .Failure(
                    listOf(
                        "CSV produced fewer than two valid candles."
                    )
                )
        }

        val sorted =
            candles.sortedBy {
                it.openTimeEpochMillis
            }

        if (sorted != candles) {
            warnings +=
                "Rows were not sorted by time; Scalper Pro sorted them locally."
        }

        val duplicates =
            sorted
                .groupingBy {
                    it.openTimeEpochMillis
                }
                .eachCount()
                .filterValues {
                    it > 1
                }

        if (duplicates.isNotEmpty()) {
            return HistoricalCsvParseResult
                .Failure(
                    listOf(
                        "CSV contains duplicate candle timestamps."
                    )
                )
        }

        return HistoricalCsvParseResult
            .Success(
                candles = sorted,
                warnings = warnings
            )
    }

    private fun detectDelimiter(
        header: String
    ): Char {
        val candidates =
            listOf(
                ',',
                ';',
                '\t'
            )

        return candidates.maxBy {
            delimiter ->
            header.count {
                it == delimiter
            }
        }
    }

    private fun split(
        line: String,
        delimiter: Char
    ): List<String> =
        line
            .split(delimiter)
            .map {
                it.trim()
                    .trim('"')
                    .trim()
            }

    private fun normalizeHeader(
        value: String
    ): String =
        value
            .removePrefix("\uFEFF")
            .lowercase()
            .replace(" ", "")
            .replace("-", "")
            .replace(".", "")
            .replace("/", "")

    private fun parseNumber(
        value: String
    ): Double? {
        val raw =
            value.trim()

        val normalized =
            when {
                raw.contains(',') &&
                    raw.contains('.') ->
                    raw.replace(
                        ",",
                        ""
                    )

                raw.contains(',') ->
                    raw.replace(
                        ',',
                        '.'
                    )

                else -> raw
            }

        return normalized
            .toDoubleOrNull()
    }

    private fun parseTimestamp(
        value: String
    ): Long? {
        val text =
            value.trim()

        text.toLongOrNull()?.let {
            numeric ->
            return when {
                numeric >
                    10_000_000_000L ->
                    numeric

                numeric >
                    1_000_000_000L ->
                    numeric * 1_000L

                else ->
                    null
            }
        }

        runCatching {
            Instant
                .parse(text)
                .toEpochMilli()
        }.getOrNull()
            ?.let {
                return it
            }

        formatters.forEach {
            formatter ->
            try {
                val parsed =
                    LocalDateTime
                        .parse(
                            text,
                            formatter
                        )

                return parsed
                    .toInstant(
                        ZoneOffset.UTC
                    )
                    .toEpochMilli()
            } catch (
                _: DateTimeParseException
            ) {
                Unit
            }
        }

        return null
    }

    private data class HeaderIndex(
        val time: Int,
        val open: Int,
        val high: Int,
        val low: Int,
        val close: Int,
        val volume: Int?
    ) {
        companion object {
            fun from(
                header: List<String>
            ): HeaderIndex? {
                fun find(
                    names: Set<String>
                ): Int? =
                    header.indexOfFirst {
                        it in names
                    }.takeIf {
                        it >= 0
                    }

                val time =
                    find(
                        setOf(
                            "time",
                            "timestamp",
                            "datetime",
                            "date",
                            "opentime",
                            "open_time"
                        )
                    ) ?: return null

                val open =
                    find(setOf("open"))
                        ?: return null

                val high =
                    find(setOf("high"))
                        ?: return null

                val low =
                    find(setOf("low"))
                        ?: return null

                val close =
                    find(setOf("close"))
                        ?: return null

                val volume =
                    find(
                        setOf(
                            "volume",
                            "vol",
                            "tickvolume",
                            "realvolume"
                        )
                    )

                return HeaderIndex(
                    time = time,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume
                )
            }
        }
    }
}
