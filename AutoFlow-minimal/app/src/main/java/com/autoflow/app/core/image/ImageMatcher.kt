package com.autoflow.app.core.image

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.abs

class ImageMatcher {

    companion object {
        private const val TAG = "ImageMatcher"
    }

    /**
     * Find template in source bitmap using template matching algorithm
     * @param source The source bitmap (screenshot)
     * @param template The template bitmap to find
     * @param threshold Match threshold (0.0 to 1.0)
     * @return PointF with coordinates of match center, or null if not found
     */
    fun findTemplate(source: Bitmap, template: Bitmap, threshold: Float = 0.8f): android.graphics.PointF? {
        if (source.width < template.width || source.height < template.height) {
            Log.w(TAG, "Template larger than source")
            return null
        }

        val resultWidth = source.width - template.width + 1
        val resultHeight = source.height - template.height + 1

        // Use normalized cross-correlation for template matching
        var bestMatchX = -1
        var bestMatchY = -1
        var bestScore = -1.0

        // Sample-based search for performance (check every 4th pixel)
        val step = 4
        for (y in 0 until resultHeight step step) {
            for (x in 0 until resultWidth step step) {
                val score = calculateNCC(source, template, x, y)
                if (score > bestScore) {
                    bestScore = score.toDouble()
                    bestMatchX = x
                    bestMatchY = y
                }
            }
        }

        // If no good match found, try finer search around best match
        if (bestScore < threshold && bestMatchX >= 0) {
            val refinedStep = 2
            val searchRadius = 10
            val startX = maxOf(0, bestMatchX - searchRadius)
            val startY = maxOf(0, bestMatchY - searchRadius)
            val endX = minOf(resultWidth - 1, bestMatchX + searchRadius)
            val endY = minOf(resultHeight - 1, bestMatchY + searchRadius)

            for (y in startY until endY step refinedStep) {
                for (x in startX until endX step refinedStep) {
                    val score = calculateNCC(source, template, x, y)
                    if (score > bestScore) {
                        bestScore = score.toDouble()
                        bestMatchX = x
                        bestMatchY = y
                    }
                }
            }
        }

        return if (bestScore >= threshold) {
            val centerX = bestMatchX + template.width / 2f
            val centerY = bestMatchY + template.height / 2f
            Log.d(TAG, "Found match at ($centerX, $centerY) with score $bestScore")
            android.graphics.PointF(centerX, centerY)
        } else {
            Log.w(TAG, "No match found, best score: $bestScore")
            null
        }
    }

    /**
     * Calculate Normalized Cross-Correlation between template and image region
     */
    private fun calculateNCC(source: Bitmap, template: Bitmap, startX: Int, startY: Int): Float {
        var sumSource = 0.0
        var sumTemplate = 0.0
        var sumSourceSq = 0.0
        var sumTemplateSq = 0.0
        var sumProduct = 0.0

        val templateWidth = template.width
        val templateHeight = template.height

        for (ty in 0 until templateHeight) {
            for (tx in 0 until templateWidth) {
                val sx = startX + tx
                val sy = startY + ty

                if (sx >= source.width || sy >= source.height) continue

                val sPixel = source.getPixel(sx, sy)
                val tPixel = template.getPixel(tx, ty)

                // Convert to grayscale (simple average)
                val sGray = (Color.red(sPixel) + Color.green(sPixel) + Color.blue(sPixel)) / 3.0
                val tGray = (Color.red(tPixel) + Color.green(tPixel) + Color.blue(tPixel)) / 3.0

                sumSource += sGray
                sumTemplate += tGray
                sumSourceSq += sGray * sGray
                sumTemplateSq += tGray * tGray
                sumProduct += sGray * tGray
            }
        }

        val n = (templateWidth * templateHeight).toDouble()

        val numerator = sumProduct - (sumSource * sumTemplate / n)
        val denominator = kotlin.math.sqrt(
            (sumSourceSq - sumSource * sumSource / n) *
                    (sumTemplateSq - sumTemplate * sumTemplate / n)
        )

        return if (denominator > 0) {
            (numerator / denominator).toFloat()
        } else {
            0f
        }
    }

    /**
     * Find all occurrences of template in source
     */
    fun findAllTemplates(
        source: Bitmap,
        template: Bitmap,
        threshold: Float = 0.8f
    ): List<android.graphics.PointF> {
        val matches = mutableListOf<android.graphics.PointF>()
        val result = findTemplate(source, template, threshold)
        result?.let { matches.add(it) }
        return matches
    }

    /**
     * Wait for template to appear on screen
     */
    fun waitForTemplate(
        sourceProvider: () -> Bitmap?,
        template: Bitmap,
        timeoutMs: Long = 10000,
        intervalMs: Long = 500
    ): android.graphics.PointF? {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val source = sourceProvider()
            if (source != null) {
                val match = findTemplate(source, template)
                if (match != null) {
                    return match
                }
            }
            Thread.sleep(intervalMs)
        }

        return null
    }
}
