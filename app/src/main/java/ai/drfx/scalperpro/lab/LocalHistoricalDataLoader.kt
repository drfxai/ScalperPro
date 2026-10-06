package ai.drfx.scalperpro.lab

import android.content.Context
import android.net.Uri
import ai.drfx.scalperpro.market.HistoricalCsvParseResult
import ai.drfx.scalperpro.market.HistoricalCsvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

object LocalHistoricalDataLoader {
    private const val MAX_READ_CHARS =
        6_000_000

    suspend fun loadCsv(
        context: Context,
        uri: Uri,
        symbol: String,
        timeframe: String
    ): HistoricalCsvParseResult =
        withContext(Dispatchers.IO) {
            val stream =
                runCatching {
                    context
                        .contentResolver
                        .openInputStream(uri)
                }.getOrNull()

            if (stream == null) {
                return@withContext
                    HistoricalCsvParseResult
                        .Failure(
                            listOf(
                                "Unable to open the selected file."
                            )
                        )
            }

            val readResult =
                runCatching {
                    stream.use { input ->
                        InputStreamReader(
                            input,
                            Charsets.UTF_8
                        ).use { reader ->
                            val output =
                                StringBuilder()
                            val buffer =
                                CharArray(8_192)
                            var tooLarge =
                                false

                            while (true) {
                                val read =
                                    reader.read(
                                        buffer
                                    )

                                if (read < 0) {
                                    break
                                }

                                if (
                                    output.length +
                                        read >
                                        MAX_READ_CHARS
                                ) {
                                    tooLarge =
                                        true
                                    break
                                }

                                output.append(
                                    buffer,
                                    0,
                                    read
                                )
                            }

                            if (tooLarge) {
                                null
                            } else {
                                output.toString()
                            }
                        }
                    }
                }

            val text =
                readResult
                    .getOrNull()

            if (
                readResult.isFailure
            ) {
                return@withContext
                    HistoricalCsvParseResult
                        .Failure(
                            listOf(
                                "Unable to read the selected CSV file."
                            )
                        )
            }

            if (text == null) {
                return@withContext
                    HistoricalCsvParseResult
                        .Failure(
                            listOf(
                                "Selected CSV exceeds the local importer size limit."
                            )
                        )
            }

            HistoricalCsvParser.parse(
                text = text,
                symbol = symbol,
                timeframe = timeframe
            )
        }
}
