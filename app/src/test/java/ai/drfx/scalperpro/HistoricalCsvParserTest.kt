package ai.drfx.scalperpro

import ai.drfx.scalperpro.market.HistoricalCsvParseResult
import ai.drfx.scalperpro.market.HistoricalCsvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalCsvParserTest {
    @Test
    fun parsesStandardCsvAndEpochSeconds() {
        val csv =
            """
            timestamp,open,high,low,close,volume
            1735689600,100,102,99,101,1200
            1735690500,101,103,100,102,1300
            """.trimIndent()

        val result =
            HistoricalCsvParser.parse(
                text = csv,
                symbol = "XAUUSD",
                timeframe = "15m"
            )

        assertTrue(
            result is
                HistoricalCsvParseResult
                    .Success
        )

        val success =
            result as
                HistoricalCsvParseResult
                    .Success

        assertEquals(
            2,
            success.candles.size
        )
        assertEquals(
            1_735_689_600_000L,
            success.candles[0]
                .openTimeEpochMillis
        )
        assertEquals(
            1200.0,
            success.candles[0]
                .volume ?: 0.0,
            0.0
        )
    }

    @Test
    fun parsesSemicolonCsvAndIsoTimestamp() {
        val csv =
            """
            time;open;high;low;close
            2025-01-01T00:00:00Z;100;101;99;100.5
            2025-01-01T00:15:00Z;100.5;102;100;101.5
            """.trimIndent()

        val result =
            HistoricalCsvParser.parse(
                text = csv,
                symbol = "TEST",
                timeframe = "15m"
            )

        assertTrue(
            result is
                HistoricalCsvParseResult
                    .Success
        )
    }

    @Test
    fun handlesBomAndDecimalCommaWithSemicolonDelimiter() {
        val csv =
            "\uFEFFtime;open;high;low;close\n" +
                "2025-01-01 00:00;100,5;101,2;99,9;100,8\n" +
                "2025-01-01 00:15;100,8;102,0;100,1;101,6"

        val result =
            HistoricalCsvParser.parse(
                text = csv,
                symbol = "TEST",
                timeframe = "15m"
            )

        assertTrue(
            result is
                HistoricalCsvParseResult
                    .Success
        )

        val success =
            result as
                HistoricalCsvParseResult
                    .Success

        assertEquals(
            100.5,
            success.candles[0].open,
            0.0001
        )
    }

    @Test
    fun rejectsInvalidOhlcBounds() {
        val csv =
            """
            timestamp,open,high,low,close
            1735689600,100,99,98,101
            1735690500,101,102,100,101
            """.trimIndent()

        val result =
            HistoricalCsvParser.parse(
                text = csv,
                symbol = "TEST",
                timeframe = "15m"
            )

        assertTrue(
            result is
                HistoricalCsvParseResult
                    .Failure
        )

        val failure =
            result as
                HistoricalCsvParseResult
                    .Failure

        assertTrue(
            failure.errors.any {
                it.contains(
                    "OHLC bounds"
                )
            }
        )
    }

    @Test
    fun sortsUnorderedRowsWithWarning() {
        val csv =
            """
            timestamp,open,high,low,close
            1735690500,101,103,100,102
            1735689600,100,102,99,101
            """.trimIndent()

        val result =
            HistoricalCsvParser.parse(
                text = csv,
                symbol = "TEST",
                timeframe = "15m"
            )

        assertTrue(
            result is
                HistoricalCsvParseResult
                    .Success
        )

        val success =
            result as
                HistoricalCsvParseResult
                    .Success

        assertTrue(
            success.warnings.any {
                it.contains(
                    "sorted"
                )
            }
        )

        assertTrue(
            success.candles[0]
                .openTimeEpochMillis <
                success.candles[1]
                    .openTimeEpochMillis
        )
    }

    @Test
    fun rejectsDuplicateTimestamps() {
        val csv =
            """
            timestamp,open,high,low,close
            1735689600,100,102,99,101
            1735689600,101,103,100,102
            """.trimIndent()

        val result =
            HistoricalCsvParser.parse(
                text = csv,
                symbol = "TEST",
                timeframe = "15m"
            )

        assertTrue(
            result is
                HistoricalCsvParseResult
                    .Failure
        )
    }
}
