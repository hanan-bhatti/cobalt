/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.widget

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object WidgetUpdater {
    private val requests = Channel<Context>(Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init {
        scope.launch {
            for (context in requests) {
                delay(150)
                runCatching { FolioWidgetProvider.showSaved(context); ScreeningWidgetProvider.showSaved(context) }
                coroutineScope {
                    val folio = async { runCatching { FolioWidgetProvider.updateAll(context) }.getOrDefault(false) }
                    val screening = async { runCatching { ScreeningWidgetProvider.updateAll(context) }.getOrDefault(false) }
                    if (!folio.await()) runCatching { FolioRefreshWorker.enqueue(context) }
                    if (!screening.await()) runCatching { ScreeningWidgetRefreshWorker.enqueue(context) }
                }
            }
        }
    }
    fun update(context: Context) { requests.trySend(context.applicationContext) }
}
