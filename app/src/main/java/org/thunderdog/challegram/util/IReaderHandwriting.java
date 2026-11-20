/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import org.thunderdog.challegram.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Optional bridge to the same framebuffer handwriting surface used by iReader Notes.
 * No vendor classes are linked on devices that do not provide the API. */
public final class IReaderHandwriting implements SurfaceHolder.Callback, AutoCloseable {
  public interface Listener {
    void onPoint (int action, float x, float y, long time, float pressure);
    void onDraw (Canvas canvas);
    void onAvailabilityChanged (boolean active);
  }

  private final FrameLayout host;
  private final Listener listener;
  private SurfaceView view;
  private Object surface, callback;
  private Class<?> api;
  private Method lockCanvas, unlockCanvas;
  private boolean ready, focused, closed, failed;
  private float width = 4;
  private int color = Color.BLACK;
  private boolean erasing;
  public static final int PRESSURE_PEN = 4;

  public static IReaderHandwriting create (Context context, FrameLayout host, Listener listener) {
    IReaderHandwriting bridge = new IReaderHandwriting(host, listener);
    try {
      Class<?> type = Class.forName("android.eink.view.HWSurfaceView");
      bridge.api = Class.forName("android.eink.view.IFBHandSurface");
      bridge.view = (SurfaceView) type.getConstructor(Context.class).newInstance(context);
      bridge.surface = type.getMethod("getFBSurface").invoke(bridge.view);
      bridge.lockCanvas = type.getMethod("lockActiveCanvas");
      bridge.unlockCanvas = type.getMethod("unlockActiveCanvas");
      Class<?> callbackType = Class.forName("android.eink.view.IAhwTouchListener");
      bridge.callback = Proxy.newProxyInstance(callbackType.getClassLoader(), new Class<?>[] {callbackType}, (proxy, method, args) -> {
        switch (method.getName()) {
          case "hashCode": return System.identityHashCode(proxy);
          case "equals": return proxy == args[0];
          case "toString": return "InkgramHandwriting";
          case "onAhwAddPoint":
            // Vendor callbacks can arrive off the UI thread; copy values before enqueueing.
            int action = ((Number) args[0]).intValue();
            float x = ((Number) args[1]).floatValue(), y = ((Number) args[2]).floatValue();
            long time = ((Number) args[3]).longValue();
            float pressure = ((Number) args[4]).floatValue();
            host.post(() -> {
              if (bridge.ready && bridge.focused && !bridge.closed && !bridge.failed) listener.onPoint(action, x, y, time, pressure);
            });
            break;
        }
        return null;
      });
      bridge.call("setAutoHandWrite", new Class<?>[] {boolean.class}, false);
      bridge.call("setAhwTouchListener", new Class<?>[] {callbackType}, bridge.callback);
      bridge.call("setAhwToolTypes", new Class<?>[] {int[].class}, (Object) new int[] {MotionEvent.TOOL_TYPE_STYLUS});
      bridge.call("setNeedAddDownEvent", new Class<?>[] {boolean.class}, true);
      bridge.view.getHolder().addCallback(bridge);
      // Stylus points come from the native listener. Let fingers use the normal canvas below.
      bridge.view.setOnTouchListener((v, event) -> event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS);
      host.addView(bridge.view, new FrameLayout.LayoutParams(-1, -1));
      Log.i("IReaderHandwriting: HWSurfaceView initialized");
    } catch (Throwable error) {
      bridge.fail(error);
    }
    return bridge;
  }

  private IReaderHandwriting (FrameLayout host, Listener listener) {
    this.host = host;
    this.listener = listener;
  }

  private Object call (String name, Class<?>[] types, Object... args) throws Exception {
    return api.getMethod(name, types).invoke(surface, args);
  }

  public void setPen (float width, int color) {
    this.width = width;
    this.color = color;
    if (surface == null || failed || closed) return;
    try {
      // Use the same pressure-sensitive fountain pen for live ink and replay.
      // Transparent live ink keeps whole-stroke erasing from painting white patches.
      call("setAhwPen", new Class<?>[] {int.class, float.class, int.class}, PRESSURE_PEN, width, erasing ? Color.TRANSPARENT : color);
    } catch (Throwable error) { fail(error); }
  }

  public void setErasing (boolean erasing) {
    this.erasing = erasing;
    setPen(width, color);
  }

  public void setActive (boolean active) {
    focused = active;
    if (!ready || failed || closed) return;
    try {
      call("setAutoHandWrite", new Class<?>[] {boolean.class}, active);
      listener.onAvailabilityChanged(active);
      Log.i("IReaderHandwriting: native handwriting %s", active ? "enabled" : "disabled");
    } catch (Throwable error) { fail(error); }
  }

  public void render () {
    if (!ready || failed || closed) return;
    boolean locked = false;
    try {
      Canvas canvas = (Canvas) lockCanvas.invoke(view);
      if (canvas == null) return;
      locked = true;
      listener.onDraw(canvas);
    } catch (Throwable error) {
      fail(error);
    } finally {
      if (locked) {
        try { unlockCanvas.invoke(view); }
        catch (Throwable error) { fail(error); }
      }
    }
  }

  @Override public void surfaceCreated (SurfaceHolder holder) {
    ready = true;
    setPen(width, color);
    host.post(() -> {
      if (closed || failed) return;
      render();
      setActive(focused);
    });
  }

  @Override public void surfaceChanged (SurfaceHolder holder, int format, int w, int h) {
    if (closed || failed) return;
    try {
      Path clip = new Path();
      clip.addRect(0, 0, w, h, Path.Direction.CW);
      call("setClip", new Class<?>[] {Path.class}, clip);
      render();
    } catch (Throwable error) { fail(error); }
  }

  @Override public void surfaceDestroyed (SurfaceHolder holder) {
    boolean wasFocused = focused;
    setActive(false);
    focused = wasFocused;
    ready = false;
    listener.onAvailabilityChanged(false);
  }

  private void fail (Throwable error) {
    if (failed) return;
    failed = true;
    disable();
    listener.onAvailabilityChanged(false);
    if (view != null) host.post(() -> host.removeView(view));
    Log.w("IReaderHandwriting: using standard canvas: %s", error.toString());
  }

  private void disable () {
    if (surface == null) return;
    try { call("setAutoHandWrite", new Class<?>[] {boolean.class}, false); } catch (Throwable ignored) { }
    try { call("setAhwTouchListener", new Class<?>[] {Class.forName("android.eink.view.IAhwTouchListener")}, (Object) null); } catch (Throwable ignored) { }
  }

  @Override public void close () {
    if (closed) return;
    closed = true;
    disable();
    listener.onAvailabilityChanged(false);
    if (view != null) host.removeView(view);
    callback = null;
  }
}
