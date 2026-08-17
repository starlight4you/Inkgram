/*
 * Inkgram: page-flip scrolling helpers for e-ink.
 */
package org.thunderdog.challegram.util;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.tool.Screen;

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
    // RecyclerView.scrollBy is instant and clamps at content edges (no overshoot).
    rv.scrollBy(0, delta);
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
