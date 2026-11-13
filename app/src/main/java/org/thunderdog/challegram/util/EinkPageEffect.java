/*
 * Inkgram: iReader (掌阅) water-ripple page-turn refresh bridge for e-ink.
 */
package org.thunderdog.challegram.util;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.SystemClock;
import android.view.Surface;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Bridge to the iReader (掌阅) SmartOS vendor EPDC interface that arms the firmware
 * water-ripple page-turn animation for the next frame posted to the display controller.
 *
 * Protocol (reverse engineered by the KOReader ireader.koplugin project, legadoM-Ink
 * and Shihon, ported from the Minko reader):
 *
 *     EPDCDevice.nativePostCommand("next-effect-type <effect>")
 *     EPDCDevice.setForceNextPostMode(0x01000063)
 *
 * where effect = direction code | speed flag. Direction codes depend on the current
 * Surface rotation; speed flags are SPEED_SLOW / SPEED_STANDARD / SPEED_FAST.
 *
 * The vendor class is located reflectively: the known exact signatures are tried
 * first, then methods are discovered by shape (name hint + parameter types), because
 * firmware variants differ. Both static and instance methods are supported (an
 * instance is obtained from a static no-argument singleton getter).
 *
 * On non-iReader devices the class simply doesn't exist and every call is a no-op.
 */
public final class EinkPageEffect {
  public static final int SPEED_SLOW = 128;
  public static final int SPEED_STANDARD = 64;
  public static final int SPEED_FAST = 0;

  // Direction modes (stored in Settings): how the ripple direction is chosen per flip.
  public static final int DIRECTION_FOLLOW = 0;
  public static final int DIRECTION_FORWARD = 1;
  public static final int DIRECTION_BACKWARD = 2;

  private static final String EPDC_CLASS_NAME = "android.eink.EPDCDevice";
  private static final int FORCE_NEXT_POST_MODE_PAGE_TURN = 0x01000063;

  // Direction codes indexed by Surface rotation (0..3). Landscape entries are
  // swapped relative to the community reference table, as verified on device
  // by the ireader.koplugin project.
  private static final int[] DIRECTIONS_FORWARD = {1, 3, 2, 4};
  private static final int[] DIRECTIONS_BACKWARD = {2, 4, 1, 3};

  private static boolean probed;
  private static boolean available;

  private static Class<?> epdcClass;
  private static Method postMethod;
  private static Method forceMethod;
  private static Object epdcInstance;

  private EinkPageEffect () {}

  public static boolean isAvailable () {
    return probe();
  }

  private static synchronized boolean probe () {
    if (probed) {
      return available;
    }
    probed = true;

    Class<?> clazz;
    try {
      clazz = Class.forName(EPDC_CLASS_NAME);
    } catch (ClassNotFoundException e) {
      Log.i("EinkPageEffect: %s not found, water-ripple page turn unavailable", EPDC_CLASS_NAME);
      return false;
    }

    try {
      epdcClass = clazz;
      Method[] methods = clazz.getMethods();

      postMethod = bind(findPostMethod(clazz, methods));
      forceMethod = bind(findForceMethod(clazz, methods));

      if (postMethod == null) {
        StringBuilder sb = new StringBuilder("EinkPageEffect: no post-command method found; class declares:");
        int count = Math.min(methods.length, 16);
        for (int i = 0; i < count; i++) {
          sb.append(' ').append(methods[i].getName()).append('(');
          Class<?>[] types = methods[i].getParameterTypes();
          for (int j = 0; j < types.length; j++) {
            if (j > 0) {
              sb.append(", ");
            }
            sb.append(types[j].getSimpleName());
          }
          sb.append(')');
        }
        Log.w(sb.toString());
        return false;
      }

      available = true;
      Log.i("EinkPageEffect: ready via %s, force mode via %s", postMethod.getName(), forceMethod != null ? forceMethod.getName() : "none");
      StringBuilder dump = new StringBuilder("EinkPageEffect: EPDC API surface:");
      for (Method m : methods) {
        dump.append(' ').append(Modifier.isStatic(m.getModifiers()) ? "s:" : "i:").append(m.getName()).append('(');
        Class<?>[] types = m.getParameterTypes();
        for (int j = 0; j < types.length; j++) {
          if (j > 0) {
            dump.append(',');
          }
          dump.append(types[j].getSimpleName());
        }
        dump.append(')');
      }
      Log.i(dump.toString());
    } catch (Throwable t) {
      Log.w("EinkPageEffect: probe failed: %s", t.getMessage());
    }
    return available;
  }

  private static @Nullable Method findPostMethod (Class<?> clazz, Method[] methods) {
    try {
      return clazz.getMethod("nativePostCommand", String.class);
    } catch (NoSuchMethodException ignored) {}
    Method best = null;
    for (Method method : methods) {
      String name = method.getName().toLowerCase(Locale.US);
      if (!(name.contains("postcommand") || name.contains("post"))) {
        continue;
      }
      Class<?>[] types = method.getParameterTypes();
      if (types.length != 1 || types[0] != String.class) {
        continue;
      }
      if (best == null || (!Modifier.isStatic(best.getModifiers()) && Modifier.isStatic(method.getModifiers()))) {
        best = method;
      }
    }
    return best;
  }

  private static @Nullable Method findForceMethod (Class<?> clazz, Method[] methods) {
    try {
      return clazz.getMethod("setForceNextPostMode", int.class);
    } catch (NoSuchMethodException ignored) {}
    Method best = null;
    for (Method method : methods) {
      if (!method.getName().toLowerCase(Locale.US).contains("force")) {
        continue;
      }
      Class<?>[] types = method.getParameterTypes();
      if (types.length != 1 || types[0] != int.class) {
        continue;
      }
      if (best == null || (!Modifier.isStatic(best.getModifiers()) && Modifier.isStatic(method.getModifiers()))) {
        best = method;
      }
    }
    return best;
  }

  private static @Nullable Method bind (@Nullable Method method) {
    if (method == null) {
      return null;
    }
    if (Modifier.isStatic(method.getModifiers())) {
      return method;
    }
    return instance() != null ? method : null;
  }

  private static @Nullable Object instance () {
    if (epdcInstance == null) {
      List<Method> candidates = new ArrayList<>();
      for (Method method : epdcClass.getMethods()) {
        if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0 && method.getReturnType() == epdcClass) {
          candidates.add(method);
        }
      }
      Collections.sort(candidates, new Comparator<Method>() {
        @Override
        public int compare (Method a, Method b) {
          return Integer.compare(rank(a.getName()), rank(b.getName()));
        }

        private int rank (String name) {
          name = name.toLowerCase(Locale.US);
          if (name.contains("getinstance")) {
            return 0;
          }
          if (name.contains("instance")) {
            return 1;
          }
          if (name.contains("get")) {
            return 2;
          }
          return 3;
        }
      });
      for (Method candidate : candidates) {
        try {
          epdcInstance = candidate.invoke(null);
          if (epdcInstance != null) {
            break;
          }
        } catch (Throwable ignored) {}
      }
    }
    return epdcInstance;
  }

  /**
   * Arms the water-ripple animation for the next posted frame. Must be called right
   * before the page turn is committed so the effect lands on that frame.
   *
   * @param forward true for a turn to the next page, false for the previous page
   * @param speedFlag SPEED_SLOW, SPEED_STANDARD or SPEED_FAST
   */
  public static void onPageTurn (Context context, boolean forward, int speedFlag) {
    if (!probe()) {
      return;
    }
    ensureAnimationGateOpen();

    int rotation = Surface.ROTATION_0;
    Activity activity = findActivity(context);
    if (activity != null) {
      try {
        @SuppressWarnings("deprecation")
        int r = activity.getWindowManager().getDefaultDisplay().getRotation();
        rotation = r;
      } catch (Throwable ignored) {}
    }
    int direction = (forward ? DIRECTIONS_FORWARD : DIRECTIONS_BACKWARD)[rotation % 4];
    int effect = direction | speedFlag;

    try {
      invoke(postMethod, "next-effect-type " + effect);
      if (forceMethod != null) {
        invoke(forceMethod, FORCE_NEXT_POST_MODE_PAGE_TURN);
      }
      Log.i("EinkPageEffect: armed effect %d (direction %d, speed %d, rotation %d)", effect, direction, speedFlag, rotation);
    } catch (Throwable t) {
      Log.w("EinkPageEffect: page turn command failed: %s", t.getMessage());
    }
  }

  // ---- Inkgram: third-party animation gate ----
  //
  // The vendor display engine classifies the foreground app through
  // sys.eink.enter.thrid. While it is 1, IddisplayEpdcDevice::shouldIgnoreAnimate()
  // silently cancels every armed page-turn effect unless the screen was almost idle
  // (fps <= 10) AND the update covered >= 50% of the panel while changing < 10% of
  // its pixels - conditions a real page flip can never meet. iReader's own reader
  // apps run with the property at 0, which is why their ripple is reliable.
  //
  // On SmartOS builds the property is writable by ordinary apps (SELinux is
  // permissive), so we open the gate once per process instead of trying to game the
  // fps/diff heuristics. setprop is used as a fallback when hidden-API reflection is
  // unavailable.

  private static final String THIRD_PARTY_GATE_PROP = "sys.eink.enter.thrid";
  private static final long GATE_CHECK_INTERVAL_MS = 1000;
  private static final long GATE_SET_RETRY_MS = 5000;

  private static boolean systemPropertiesProbed;
  private static Method systemPropertiesGet;
  private static Method systemPropertiesSet;

  private static long lastGateCheck;
  private static long lastGateSetAttempt;
  private static boolean gateOpen;
  private static boolean gateThreadRunning;

  /**
   * Clears the vendor "ignore all refresh" flag.
   *
   * Older Inkgram builds held this flag around page turns. If such a build was killed
   * inside the hold window, the flag survives in the display server and freezes the app
   * layer until it is explicitly cleared. The current build never sets the flag.
   */
  public static void resetRefreshGate () {
    if (!probe()) {
      return;
    }
    try {
      invoke(postMethod, "set-ignore-refresh 0");
    } catch (Throwable t) {
      Log.w("EinkPageEffect: reset set-ignore-refresh failed: %s", t.getMessage());
    }
  }

  /** Opens the firmware's third-party animation gate if it is still closed. */
  public static void ensureAnimationGateOpen () {
    long now = SystemClock.elapsedRealtime();
    if (gateOpen && now - lastGateCheck < 30000) {
      return;
    }
    if (now - lastGateCheck < GATE_CHECK_INTERVAL_MS) {
      return;
    }
    lastGateCheck = now;

    if ("0".equals(getSystemProperty(THIRD_PARTY_GATE_PROP))) {
      gateOpen = true;
      return;
    }
    gateOpen = false;

    if (now - lastGateSetAttempt < GATE_SET_RETRY_MS) {
      return;
    }
    lastGateSetAttempt = now;

    if (setSystemProperty(THIRD_PARTY_GATE_PROP, "0")) {
      gateOpen = true;
      Log.i("EinkPageEffect: opened third-party animation gate");
    } else {
      requestGateOpenViaSetprop();
    }
  }

  private static synchronized void requestGateOpenViaSetprop () {
    if (gateThreadRunning) {
      return;
    }
    gateThreadRunning = true;
    Thread thread = new Thread(new Runnable () {
      @Override
      public void run () {
        Process process = null;
        try {
          process = new ProcessBuilder("/system/bin/setprop", THIRD_PARTY_GATE_PROP, "0")
            .redirectErrorStream(true)
            .start();
          BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
          String line;
          while ((line = reader.readLine()) != null) {
            Log.i("EinkPageEffect: setprop: %s", line);
          }
          int exit = process.waitFor();
          if (exit == 0) {
            gateOpen = true;
          }
          Log.i("EinkPageEffect: setprop exit %d", exit);
        } catch (Throwable t) {
          Log.w("EinkPageEffect: setprop failed: %s", t.getMessage());
        } finally {
          if (process != null) {
            process.destroy();
          }
          synchronized (EinkPageEffect.class) {
            gateThreadRunning = false;
          }
          lastGateCheck = 0L;
        }
      }
    }, "EinkAnimationGate");
    thread.setDaemon(true);
    thread.start();
  }

  private static String getSystemProperty (String key) {
    resolveSystemProperties();
    if (systemPropertiesGet != null) {
      try {
        return (String) systemPropertiesGet.invoke(null, key, "");
      } catch (Throwable ignored) {}
    }
    return execGetprop(key);
  }

  private static boolean setSystemProperty (String key, String value) {
    resolveSystemProperties();
    if (systemPropertiesSet == null) {
      return false;
    }
    try {
      systemPropertiesSet.invoke(null, key, value);
      return true;
    } catch (Throwable t) {
      Log.w("EinkPageEffect: SystemProperties.set failed: %s", t.getMessage());
      return false;
    }
  }

  private static synchronized void resolveSystemProperties () {
    if (systemPropertiesProbed) {
      return;
    }
    systemPropertiesProbed = true;
    try {
      Class<?> clazz = Class.forName("android.os.SystemProperties");
      systemPropertiesGet = clazz.getMethod("get", String.class, String.class);
      systemPropertiesSet = clazz.getMethod("set", String.class, String.class);
    } catch (Throwable t) {
      Log.w("EinkPageEffect: SystemProperties unavailable: %s", t.getMessage());
    }
  }

  private static String execGetprop (String key) {
    Process process = null;
    try {
      process = new ProcessBuilder("/system/bin/getprop", key)
        .redirectErrorStream(true)
        .start();
      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line = reader.readLine();
      process.waitFor();
      return line != null ? line.trim() : null;
    } catch (Throwable t) {
      return null;
    } finally {
      if (process != null) {
        process.destroy();
      }
    }
  }

  private static @Nullable Activity findActivity (Context context) {
    while (context instanceof ContextWrapper) {
      if (context instanceof Activity) {
        return (Activity) context;
      }
      context = ((ContextWrapper) context).getBaseContext();
    }
    return null;
  }

  private static void invoke (Method method, Object arg) throws Exception {
    if (Modifier.isStatic(method.getModifiers())) {
      method.invoke(null, arg);
    } else {
      method.invoke(epdcInstance, arg);
    }
  }
}
