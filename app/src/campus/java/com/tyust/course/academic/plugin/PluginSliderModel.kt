package com.tyust.course.academic.plugin

import android.content.Context
import org.json.JSONObject

/** Campus distribution retains the existing on-device pixel matcher. */
internal object PluginSliderModel {
    @Synchronized
    fun match(app: Context, background: PluginImageMatcher.Image, piece: PluginImageMatcher.Image, expectedY: Double?): JSONObject =
        PluginImageMatcher.match(background.bitmap, piece.bitmap, background.width, background.height,
            piece.width, piece.height, expectedY)
}
