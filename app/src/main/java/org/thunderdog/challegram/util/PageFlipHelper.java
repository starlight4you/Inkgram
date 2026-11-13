/*
 * Inkgram: page-flip scrolling helpers for e-ink.
 */
package org.thunderdog.challegram.util;

import android.content.Context;
import android.view.Choreographer;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.v.MessagesRecyclerView;

/**
 * Whole-page scrolling without any animated/follow-finger motion.
 *
 * Direction convention (visual, independent of layout direction):
 *   +1 => content moves UP   (settings list: next page; chat history: older messages)
 *   -1 => content moves DOWN (settings list: previous page; chat history: newer messages)
 */
public class PageFlipHelper {
  private static final float OVERLAP_DP = 56f; // ~one row of overlap for reading continuity
  private static final float MIN_PAGE_DP = 48f;
  public static final float FLING_THRESHOLD_DP = 48f;

  public static boolean pageBy (@Nullable RecyclerView rv, int direction) {
    if (rv == null || direction == 0) {
      return false;
    }
    int viewport = rv.getHeight() - rv.getPaddingTop() - rv.getPaddingBottom();
    if (viewport <= 0) {
      return false;
    }
    int overlap = Screen.dp(OVERLAP_DP);
    int delta = direction * Math.max(Screen.dp(MIN_PAGE_DP), viewport - overlap);
    // Inkgram: arm the firmware water-ripple refresh so it lands on the frame this flip
    // posts. ChatsRecyclerView extends CustomRecyclerView, so both are covered below.
    // Skipped at the content edges, where the flip is a no-op and a full-screen
    // ripple would only flash.
    if ((rv instanceof MessagesRecyclerView || rv instanceof CustomRecyclerView)
      && rv.canScrollVertically(direction)
      && Settings.instance().needPageFlipRipple()
      && armRipple(rv, direction, delta)) {
      // The scroll is performed inside the armed frame, so the effect always lands on a
      // frame whose content actually changed (see armRipple).
      return true;
    }
    // RecyclerView.scrollBy is instant and clamps at content edges (no overshoot).
    rv.scrollBy(0, delta);
    return true;
  }

  /**
   * Arms the firmware water-ripple and scrolls in the same frame.
   *
   * The EPDC command applies to the next frame posted to the display controller. If we
   * scrolled first and armed from an OnPreDrawListener, a draw pass that was already in
   * flight could carry the content change; the armed effect then landed on a no-op frame
   * and the ripple was skipped. Running from a Choreographer frame callback (start of the
   * frame, before layout/draw) makes the scroll and the arm command belong to one frame.
   */
  private static boolean armRipple (RecyclerView rv, int direction, int delta) {
    Settings settings = Settings.instance();
    final boolean forward;
    switch (settings.getPageFlipRippleDirection()) {
      case EinkPageEffect.DIRECTION_FORWARD:
        forward = true;
        break;
      case EinkPageEffect.DIRECTION_BACKWARD:
        forward = false;
        break;
      case EinkPageEffect.DIRECTION_FOLLOW:
      default:
        forward = direction > 0;
        break;
    }
    final int speedFlag = settings.getPageFlipRippleSpeedFlag();
    final Context context = rv.getContext();
    if (!rv.isAttachedToWindow()) {
      return false;
    }
    Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
      @Override
      public void doFrame (long frameTimeNanos) {
        rv.scrollBy(0, delta);
        EinkPageEffect.onPageTurn(context, forward, speedFlag);
      }
    });
    return true;
  }

  public static boolean pageDown (@Nullable RecyclerView rv) {
    return pageBy(rv, 1);
  }

  public static boolean pageUp (@Nullable RecyclerView rv) {
    return pageBy(rv, -1);
  }

  /** Whole-page instant scroll for any View (e.g. WebView in the in-app browser). */
  public static boolean pageByView (@Nullable android.view.View view, int direction) {
    if (view == null || direction == 0) {
      return false;
    }
    int viewport = view.getHeight() - view.getPaddingTop() - view.getPaddingBottom();
    if (viewport <= 0) {
      return false;
    }
    int delta = direction * Math.max(Screen.dp(MIN_PAGE_DP), viewport - Screen.dp(OVERLAP_DP));
    view.scrollBy(0, delta);
    return true;
  }

  /**
   * Finds the first page-flip capable RecyclerView (CustomRecyclerView or MessagesRecyclerView)
   * visible on screen within the given view hierarchy.
   */
  public static @Nullable RecyclerView findPagingRecyclerView (@Nullable android.view.View root) {
    if (root == null || !root.isShown()) {
      return null;
    }
    if ((root instanceof org.thunderdog.challegram.v.CustomRecyclerView || root instanceof org.thunderdog.challegram.v.MessagesRecyclerView)
      && root.getHeight() > 0 && root.getVisibility() == android.view.View.VISIBLE) {
      return (RecyclerView) root;
    }
    if (root instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        RecyclerView found = findPagingRecyclerView(group.getChildAt(i));
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }
}
