/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.tool;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;

import java.util.HashMap;

/**
 * Inkgram: Kindle-style black-and-white dithered overlay (e-ink).
 * Pure-black dots on transparent background, tiled; dot density encodes the dim factor
 * so the dots themselves stay 1-bit black (no gray-level flicker on e-ink).
 */
public class DitherOverlay {
  /** Grain size in physical pixels per dot. Increase for coarser mosaic. */
  private static final int CELL_PX = 4;

  private static final HashMap<Integer, Bitmap> bitmapCache = new HashMap<>();
  private static final HashMap<Integer, Paint> paintCache = new HashMap<>();

  private static int spacingFor (float density) {
    // Kindle look: evenly spaced isolated dots on a diagonal lattice (no clustering).
    // density ~= 1/spacing: 0.6 -> every 2 cells (50%), 0.3 -> every 3 cells (33%)
    return Math.max(1, Math.min(8, Math.round(1f / Math.max(0.01f, density))));
  }

  private static Bitmap getBitmap (float density) {
    final int spacing = spacingFor(density);
    Bitmap bitmap = bitmapCache.get(spacing);
    if (bitmap == null) {
      final int size = spacing * CELL_PX; // tiles seamlessly: (x+y) % spacing has period spacing in both axes
      bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
      if (spacing <= 1) {
        bitmap.eraseColor(Color.BLACK);
      } else {
        for (int y = 0; y < spacing; y++) {
          for (int x = 0; x < spacing; x++) {
            if ((x + y) % spacing == 0) {
              for (int dy = 0; dy < CELL_PX; dy++) {
                for (int dx = 0; dx < CELL_PX; dx++) {
                  bitmap.setPixel(x * CELL_PX + dx, y * CELL_PX + dy, Color.BLACK);
                }
              }
            }
          }
        }
      }
      bitmapCache.put(spacing, bitmap);
    }
    return bitmap;
  }

  /** Tiled dither paint; density is the dim factor (0 = transparent, 1 = solid black). */
  public static Paint getPaint (float density) {
    final int key = spacingFor(density);
    Paint paint = paintCache.get(key);
    if (paint == null) {
      paint = new Paint(Paint.FILTER_BITMAP_FLAG);
      paint.setShader(new BitmapShader(getBitmap(density), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
      paintCache.put(key, paint);
    }
    return paint;
  }

  /** Tiled dither drawable for View backgrounds. */
  public static BitmapDrawable getDrawable (Resources res, float density) {
    BitmapDrawable drawable = new BitmapDrawable(res, getBitmap(density));
    drawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    return drawable;
  }

  /** Draw dithered overlay over the given area. */
  public static void draw (Canvas c, float density, int left, int top, int right, int bottom) {
    c.drawRect(left, top, right, bottom, getPaint(density));
  }
}
