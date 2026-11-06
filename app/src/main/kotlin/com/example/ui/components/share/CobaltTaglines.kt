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

package com.example.ui.components.share

import kotlin.math.abs
import kotlin.random.Random

/**
 * Curated witty, minimalist taglines about Cobalt, offline archiving, and hoarding links.
 */
object CobaltTaglines {

    val LINES = listOf(
        "Saved with Cobalt before the tab was closed forever",
        "Archived in Cobalt. Probably won't read it, but at least it's safe",
        "Hoarded with love in Cobalt",
        "Tucked away in Cobalt for a rainy day",
        "Saved with Cobalt (future me says thanks)",
        "Safely forgotten in Cobalt",
        "Rescued from 93 open browser tabs by Cobalt",
        "Cobalt: Where good links go to rest",
        "Preserved in Cobalt. Offline & peaceful",
        "Cobalt caught this one",
        "Sent to Cobalt's offline vault",
        "Stored in Cobalt, safe from 404 errors",
        "Cobalt saved this. Brain can relax now",
        "Captured with Cobalt",
        "Tucked into the second brain via Cobalt",
        "Saved with Cobalt because memory is a finite resource",
        "Kept safe by Cobalt",
        "Filed away in Cobalt",
        "Cobalt: Because bookmarks are where links go to die",
        "Saved with Cobalt. Zero cloud, zero nonsense",
        "Archived offline in Cobalt",
        "Saved with Cobalt before the internet forgot it",
        "Secured in Cobalt's local sanctuary",
        "Cobalt: One less tab to worry about",
        "Saved with Cobalt. You'll thank yourself later",
        "Saved with Cobalt • Read whenever (or never)",
        "Rescued by Cobalt",
        "Cobalt: A quiet corner for curious minds",
        "Pocketed with Cobalt",
        "Another gem saved in Cobalt",
        "Captured with Cobalt for later contemplation",
        "Saved with Cobalt. No algorithm attached",
        "Preserved locally with Cobalt",
        "Cobalt: Digital hoarding, but make it aesthetic",
        "Saved with Cobalt. Just in case",
        "Saved with Cobalt (definitely reading this later)",
        "Stashed away in Cobalt",
        "Cobalt saved this from the infinite scroll",
        "Offline-first memory by Cobalt",
        "Kept in Cobalt's pocket",
        "Saved with Cobalt so you can close that tab",
        "Secured locally in Cobalt",
        "Added to the knowledge pile via Cobalt",
        "Cobalt: For things too good to lose",
        "Captured cleanly with Cobalt",
        "Saved with Cobalt • Peace of mind delivered",
        "Safely vaulted in Cobalt",
        "Cobalt made a copy just for you",
        "Hoarded offline in Cobalt",
        "Saved with Cobalt before battery hit 1%",
        "Cobalt: Your offline intellectual hoard",
        "Caught in Cobalt's net",
        "Saved with Cobalt. No trackers, just bytes",
        "Archived in Cobalt with zero regrets",
        "Saved with Cobalt • Second brain satisfied"
    )

    fun getRandom(): String = LINES[Random.nextInt(LINES.size)]

    fun getForSeed(seed: String?): String {
        if (seed.isNullOrBlank()) return getRandom()
        val index = abs(seed.hashCode()) % LINES.size
        return LINES[index]
    }
}
