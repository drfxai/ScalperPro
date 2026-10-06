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
                context
                    .contentResolver
                    .openInputStream(uri)
                    ?: return@withContext
                        HistoricalCsvParseResult
                            .Failure(
                                listOf(
                                    "Unable to open the selected file."
                                )
                            )

            stream.use { input ->
                InputStreamReader(
                    input,
                    Charsets.UTF_8
                ).use { reader ->
                    val output =
                        StringBuilder()
                    val buffer =
                        CharArray(8_192)

                    while (true) {
                        val read =
                            reader.read(buffer)

                        if (read < 0) {
                            break
                        }

                        if (
                            output.length +
                                read >
                                MAX_READ_CHARS
                        ) {
                            return@withContext
                                HistoricalCsvParseResult
                                    .Failure(
                                        listOf(
                                            "Selected CSV exceeds the local importer size limit."
                                        )
                                    )
                        }

                        output.append(
                            buffer,
                            0,
                            read
                        )
                    }

                    HistoricalCsvParser.parse(
                        text =
                            output.toString(),
                        symbol = symbol,
                        timeframe =
                            timeframe
                    )
                }
            }
        }
}
