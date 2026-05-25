package org.telegram.ui.eink;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;

public class EinkTheme {
    // Pure Black and Pure White Colors
    public static final int COLOR_BLACK = 0xFF000000;
    public static final int COLOR_WHITE = 0xFFFFFFFF;

    // Line spacing multiplier
    public static final float LINE_SPACING_MULTIPLIER = 1.6f;

    // Serif Fonts
    public static final Typeface SERIF_REGULAR = Typeface.SERIF;
    public static final Typeface SERIF_BOLD = Typeface.create(Typeface.SERIF, Typeface.BOLD);

    // Text Sizes in SP
    public static final float TEXT_SIZE_BODY_SP = 16.0f;
    public static final float TEXT_SIZE_USERNAME_SP = 12.0f;
    public static final float TEXT_SIZE_META_SP = 11.0f; // for dates/page info
    public static final float TEXT_SIZE_TITLE_SP = 18.0f;

    /**
     * Get 1px physical stroke Paint for crisp e-ink borders
     */
    public static Paint getBorderPaint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(COLOR_BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.0f); // 1px solid line
        return paint;
    }

    /**
     * Get Paint with pure black fill
     */
    public static Paint getBlackFillPaint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(COLOR_BLACK);
        paint.setStyle(Paint.Style.FILL);
        return paint;
    }

    /**
     * Get Paint with pure white fill
     */
    public static Paint getWhiteFillPaint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(COLOR_WHITE);
        paint.setStyle(Paint.Style.FILL);
        return paint;
    }

    /**
     * TextPaint helper for the Serif Regular body text
     */
    public static TextPaint getTextPaintBody() {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setColor(COLOR_BLACK);
        paint.setTypeface(SERIF_REGULAR);
        paint.setTextSize(AndroidUtilities.dp(TEXT_SIZE_BODY_SP));
        return paint;
    }

    /**
     * TextPaint helper for Serif Bold usernames
     */
    public static TextPaint getTextPaintUsername() {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setColor(COLOR_BLACK);
        paint.setTypeface(SERIF_BOLD);
        paint.setTextSize(AndroidUtilities.dp(TEXT_SIZE_USERNAME_SP));
        return paint;
    }

    /**
     * TextPaint helper for metadata (timestamp, etc.)
     */
    public static TextPaint getTextPaintMeta() {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setColor(COLOR_BLACK);
        paint.setTypeface(SERIF_REGULAR);
        paint.setTextSize(AndroidUtilities.dp(TEXT_SIZE_META_SP));
        return paint;
    }

    /**
     * Draws a 1px solid border around any view's bounds
     */
    public static void drawBorder(Canvas canvas, int width, int height) {
        Paint borderPaint = getBorderPaint();
        canvas.drawRect(0, 0, width - 1, height - 1, borderPaint);
    }
}
