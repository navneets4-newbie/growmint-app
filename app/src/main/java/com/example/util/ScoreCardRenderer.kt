package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.FinancialCalculator
import com.example.model.ScoreCardBarLevel
import com.example.model.ScoreCardData
import com.example.model.ScoreCardThemeStyle
import java.io.File
import java.io.FileOutputStream

object ScoreCardRenderer {

    const val CARD_SIZE_PX = 1080

    fun renderScoreCardBitmap(card: ScoreCardData): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_SIZE_PX, CARD_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val isNight = card.style == ScoreCardThemeStyle.NIGHT

        // Palette
        val bgTop = if (isNight) Color.parseColor("#0A1310") else Color.parseColor("#EDF7F2")
        val bgBottom = if (isNight) Color.parseColor("#11211B") else Color.parseColor("#F9FCFA")
        val cardSurface = if (isNight) Color.parseColor("#162822") else Color.parseColor("#FFFFFF")
        val cardBorder = if (isNight) Color.parseColor("#264339") else Color.parseColor("#D5E9DF")
        val inkPrimary = if (isNight) Color.parseColor("#ECF7F1") else Color.parseColor("#0F281E")
        val inkMuted = if (isNight) Color.parseColor("#9AB4A8") else Color.parseColor("#4F675C")
        val mintAccent = if (isNight) Color.parseColor("#48D8A0") else Color.parseColor("#00875A")
        val mintSoft = if (isNight) Color.parseColor("#1D3B30") else Color.parseColor("#DFF4EA")
        val trackColor = if (isNight) Color.parseColor("#243C33") else Color.parseColor("#E3EFE9")
        val goodColor = if (isNight) Color.parseColor("#4ADE80") else Color.parseColor("#1B8A4C")
        val okayColor = if (isNight) Color.parseColor("#FBBF24") else Color.parseColor("#C77D0A")
        val lowColor = if (isNight) Color.parseColor("#F87171") else Color.parseColor("#C63D3D")

        // Background gradient
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, CARD_SIZE_PX.toFloat(), CARD_SIZE_PX.toFloat(),
                bgTop, bgBottom, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_SIZE_PX.toFloat(), CARD_SIZE_PX.toFloat(), bgPaint)

        // Subtle decorative aura circles
        val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mintAccent
            alpha = if (isNight) 22 else 18
        }
        canvas.drawCircle(930f, 150f, 220f, auraPaint)
        canvas.drawCircle(140f, 940f, 180f, auraPaint)

        // Main rounded container card
        val containerRect = RectF(56f, 56f, 1024f, 1024f)
        val containerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardSurface }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBorder
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(containerRect, 44f, 44f, containerPaint)
        canvas.drawRoundRect(containerRect, 44f, 44f, borderPaint)

        // === Header: GrowMint logo + optional nickname ===
        val logoCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mintAccent }
        canvas.drawCircle(132f, 134f, 34f, logoCirclePaint)

        // Simple leaf emblem inside logo circle
        val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (isNight) Color.parseColor("#0A1310") else Color.WHITE }
        val leafPath = Path().apply {
            moveTo(116f, 148f)
            cubicTo(116f, 122f, 136f, 114f, 150f, 116f)
            cubicTo(150f, 138f, 138f, 152f, 116f, 148f)
            close()
        }
        canvas.drawPath(leafPath, leafPaint)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkPrimary
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("GrowMint", 184f, 132f, brandPaint)

        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkMuted
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText("MONEY HEALTH SCORE", 184f, 162f, subHeaderPaint)

        if (card.showNickname && card.nickname.isNotBlank()) {
            val nickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = mintAccent
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val nickWidth = nickPaint.measureText(card.nickname)
            val pillW = nickWidth + 44f
            val pillLeft = 972f - pillW
            val pillRect = RectF(pillLeft, 104f, 972f, 158f)
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mintSoft }
            canvas.drawRoundRect(pillRect, 27f, 27f, pillPaint)
            canvas.drawText(card.nickname, pillLeft + 22f, 140f, nickPaint)
        }

        // === Hero Score Ring + Score Number + Code Label ===
        val hasBars = card.showComponentBars && card.componentBars.isNotEmpty()
        val ringCenterX = 540f
        val ringCenterY = if (hasBars) 355f else 460f
        val ringRadius = if (hasBars) 136f else 172f

        val ringTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = trackColor
            style = Paint.Style.STROKE
            strokeWidth = if (hasBars) 22f else 26f
            strokeCap = Paint.Cap.ROUND
        }
        val ringArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mintAccent
            style = Paint.Style.STROKE
            strokeWidth = if (hasBars) 22f else 26f
            strokeCap = Paint.Cap.ROUND
        }
        val ringBounds = RectF(
            ringCenterX - ringRadius,
            ringCenterY - ringRadius,
            ringCenterX + ringRadius,
            ringCenterY + ringRadius
        )
        canvas.drawArc(ringBounds, 0f, 360f, false, ringTrackPaint)
        val sweep = (card.score.coerceIn(0, 100) / 100f) * 360f
        canvas.drawArc(ringBounds, -90f, sweep, false, ringArcPaint)

        val scoreTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkPrimary
            textSize = if (hasBars) 96f else 124f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("${card.score}", ringCenterX, ringCenterY + (if (hasBars) 24f else 32f), scoreTextPaint)

        val outOfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkMuted
            textSize = if (hasBars) 24f else 28f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("out of 100", ringCenterX, ringCenterY + (if (hasBars) 64f else 82f), outOfPaint)

        // Score label pill (Getting started / Building / Strong / Excellent)
        val labelY = ringCenterY + ringRadius + 56f
        val gradeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mintAccent
            textSize = 34f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelMeasured = gradeTextPaint.measureText(card.scoreLabel)
        val gradePillRect = RectF(
            ringCenterX - (labelMeasured / 2f) - 36f,
            labelY - 38f,
            ringCenterX + (labelMeasured / 2f) + 36f,
            labelY + 16f
        )
        val softPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mintSoft }
        canvas.drawRoundRect(gradePillRect, 28f, 28f, softPillPaint)
        canvas.drawText(card.scoreLabel, ringCenterX, labelY, gradeTextPaint)

        // Optional Monthly Delta Pill (ONLY when previousScore exists and toggle is ON)
        var currentBottomY = labelY + 26f
        if (card.showMonthlyChange && card.monthDelta != null) {
            val deltaStr = FinancialCalculator.formatMonthDeltaForCard(card.monthDelta)
            val deltaColor = when {
                card.monthDelta > 0 -> goodColor
                card.monthDelta < 0 -> lowColor
                else -> mintAccent
            }
            val deltaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = deltaColor
                textSize = 26f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val deltaW = deltaPaint.measureText(deltaStr)
            val deltaTop = currentBottomY + 12f
            val deltaRect = RectF(
                ringCenterX - (deltaW / 2f) - 28f,
                deltaTop,
                ringCenterX + (deltaW / 2f) + 28f,
                deltaTop + 44f
            )
            val deltaBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = deltaColor
                alpha = if (isNight) 40 else 28
            }
            canvas.drawRoundRect(deltaRect, 22f, 22f, deltaBg)
            canvas.drawText(deltaStr, ringCenterX, deltaTop + 31f, deltaPaint)
            currentBottomY = deltaTop + 56f
        }

        // === Optional 4 Component Bars (Level ONLY: Low / Okay / Good) ===
        if (hasBars) {
            val barsTop = maxOf(currentBottomY + 20f, 608f)
            val barsBox = RectF(108f, barsTop, 972f, barsTop + 280f)
            val barsBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isNight) Color.parseColor("#11201B") else Color.parseColor("#F5FAF7")
            }
            canvas.drawRoundRect(barsBox, 26f, 26f, barsBoxPaint)

            val barNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = inkPrimary
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val barLevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 24f
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val barTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trackColor }
            val barFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

            val barLeft = 140f
            val barRight = 940f
            val barWidth = barRight - barLeft
            card.componentBars.take(4).forEachIndexed { idx, bar ->
                val rowY = barsTop + 38f + idx * 62f
                val levelColor = when (bar.level) {
                    ScoreCardBarLevel.GOOD -> goodColor
                    ScoreCardBarLevel.OKAY -> okayColor
                    ScoreCardBarLevel.LOW -> lowColor
                }
                canvas.drawText(bar.name, barLeft, rowY, barNamePaint)
                barLevelPaint.color = levelColor
                canvas.drawText(bar.level.label, barRight, rowY, barLevelPaint)

                val trackRect = RectF(barLeft, rowY + 12f, barRight, rowY + 26f)
                canvas.drawRoundRect(trackRect, 7f, 7f, barTrackPaint)

                barFillPaint.color = levelColor
                val fillW = barWidth * bar.level.fillFraction
                val fillRect = RectF(barLeft, rowY + 12f, barLeft + fillW, rowY + 26f)
                canvas.drawRoundRect(fillRect, 7f, 7f, barFillPaint)
            }
        }

        // === Small Footer Disclaimer + App Link ===
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBorder
            strokeWidth = 2f
        }
        canvas.drawLine(108f, 922f, 972f, 922f, dividerPaint)

        val disclaimerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkMuted
            textSize = 22f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(card.footerDisclaimer, 540f, 958f, disclaimerPaint)

        val urlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mintAccent
            textSize = 18f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(card.appUrl, 540f, 990f, urlPaint)

        return bitmap
    }

    /**
     * Zero-permission image export:
     * - API 29+: Saves via MediaStore directly into Pictures/GrowMint (returns saved Uri).
     * - API < 29: Returns Result.success(null) so caller launches SAF CreateDocument("image/png").
     */
    fun saveBitmapToGalleryZeroPermission(
        context: Context,
        bitmap: Bitmap,
        fileName: String = "growmint_score_${System.currentTimeMillis()}.png"
    ): Result<Uri?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Result.success(null)
        }
        return try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/GrowMint")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return Result.failure(IllegalStateException("Unable to create MediaStore entry."))
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw IllegalStateException("PNG compression failed.")
                }
            } ?: throw IllegalStateException("Unable to open gallery output stream.")

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun writeBitmapToUri(context: Context, bitmap: Bitmap, uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun writeBitmapToShareCache(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val dir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
            val file = File(dir, "growmint_score_card.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            null
        }
    }

    fun buildScoreCardShareChooser(
        context: Context,
        imageUri: Uri,
        shareText: String
    ): Intent? {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return if (sendIntent.resolveActivity(context.packageManager) != null) {
            Intent.createChooser(sendIntent, "Share GrowMint Score Card")
        } else {
            null
        }
    }

    fun buildTextInviteShareChooser(
        context: Context,
        inviteText: String
    ): Intent? {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, inviteText)
        }
        return if (sendIntent.resolveActivity(context.packageManager) != null) {
            Intent.createChooser(sendIntent, "Invite a friend to GrowMint")
        } else {
            null
        }
    }
}
