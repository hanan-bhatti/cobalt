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

package com.example.util

data class LoadingMessageSet(
    val categoryName: String,
    val messages: List<String>
)

object OcrLoadingMessages {
    val SET_DIGITAL_QUIRKS = LoadingMessageSet(
        categoryName = "Digital Quirks",
        messages = listOf(
            "Squinting at these pixels like a grandparent without reading glasses...",
            "Hold on, negotiating with your screen resolution in good faith.",
            "Brewing a quick espresso for the character recognizer...",
            "Pretending to read fast so the processor doesn't judge me.",
            "Consulting the ancient scrolls of digital typography...",
            "Hold tight, trying to figure out if that was an 'l', an 'I', or a '1'."
        )
    )

    val SET_POLITE_SARCASM = LoadingMessageSet(
        categoryName = "Polite Sarcasm",
        messages = listOf(
            "Staring very intently at this rectangle. It looks exceptionally profound.",
            "Politely dusting off the syllables and putting them in alphabetical order.",
            "Reading through this with dramatic theatrical flair in my head.",
            "Please hold. Even silicon chips need a second to digest this masterpiece.",
            "Carefully inspecting every single pixel with white-glove service.",
            "Running on pure electricity, curiosity, and mild optimism."
        )
    )

    val SET_HUMAN_VS_TECH = LoadingMessageSet(
        categoryName = "Human vs Tech",
        messages = listOf(
            "Deciphering fonts that probably didn't need to be this cursive.",
            "Doing mental gymnastics to parse this layout without spilling tea.",
            "Reading through this like an urgent email sent on Friday at 4:59 PM.",
            "Checking the fine print so you don't have to strain your eyes.",
            "Translating digital hieroglyphics into actual human words...",
            "Taking our sweet time to make sure we don't misread anything important."
        )
    )

    val SET_RELATABLE_READER = LoadingMessageSet(
        categoryName = "Relatable Reader",
        messages = listOf(
            "Almost there! Good things come to those who don't tap repeatedly.",
            "Parsing pixels diligently... definitely not looking at memes.",
            "Giving your text the undivided attention it genuinely deserves.",
            "Counting pixels one by one... just kidding, but it is very thorough.",
            "Hold on, the letters are taking their designated seats in the theater.",
            "Making sure we didn't mistake a tiny screen smudge for punctuation."
        )
    )

    val SET_PLAYFUL_MUSINGS = LoadingMessageSet(
        categoryName = "Playful Musings",
        messages = listOf(
            "Contemplating the deeper philosophical meaning of this screenshot...",
            "Teaching the text engine how to politely read between the lines...",
            "Patience is a virtue, and right now you're being remarkably virtuous.",
            "Polishing the final sentences so they look their absolute best for you.",
            "Working diligently while looking very busy and important.",
            "Untangling a few stubborn words that refused to stand in line."
        )
    )

    val ALL_SETS: List<LoadingMessageSet> = listOf(
        SET_DIGITAL_QUIRKS,
        SET_POLITE_SARCASM,
        SET_HUMAN_VS_TECH,
        SET_RELATABLE_READER,
        SET_PLAYFUL_MUSINGS
    )

    val ALL_MESSAGES: List<String> = ALL_SETS.flatMap { it.messages }

    fun getRandomMessage(exclude: String? = null): String {
        val pool = if (exclude != null && ALL_MESSAGES.size > 1) {
            ALL_MESSAGES.filter { it != exclude }
        } else {
            ALL_MESSAGES
        }
        return pool.random()
    }
}
