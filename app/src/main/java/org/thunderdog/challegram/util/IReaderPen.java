/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.util;

import android.graphics.Path;

/** Replays raw pressure samples through the firmware pen, matching its live ink. */
public final class IReaderPen {
  private Object pen;
  private java.lang.reflect.Method touch, track, path;

  public IReaderPen (float width, int color) {
    try {
      Class<?> type = Class.forName("android.eink.view.HandPen");
      pen = type.getConstructor(int.class, boolean.class).newInstance(IReaderHandwriting.PRESSURE_PEN, true);
      // The framebuffer's monochrome live pass appears slightly narrower than
      // the antialiased saved outline; compensate during replay, retaining pressure.
      type.getMethod("setStrokeWidth", float.class).invoke(pen, width * 0.9f);
      type.getMethod("setStrokeColor", int.class).invoke(pen, color);
      touch = type.getMethod("onTouchEvent", int.class, float.class, float.class, long.class, float.class);
      track = type.getMethod("getTrack");
      path = Class.forName("android.eink.view.IPenTrack").getMethod("getPath");
    } catch (Throwable ignored) { pen = null; }
  }

  public void point (int action, float x, float y, long time, float pressure) {
    if (pen == null) return;
    try { touch.invoke(pen, action, x, y, time, pressure); }
    catch (Throwable ignored) { pen = null; }
  }

  public Path finish () {
    if (pen == null) return null;
    try {
      Object value = track.invoke(pen);
      Path result = value == null ? null : (Path) path.invoke(value);
      return result == null || result.isEmpty() ? null : new Path(result);
    } catch (Throwable ignored) { return null; }
  }
}
