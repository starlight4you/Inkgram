/*
 * Inkgram: converts vertical drag gestures into whole-page flips for e-ink.
 */
package org.thunderdog.challegram.v;

import android.view.MotionEvent;

import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.PageFlipHelper;

import me.vkryl.android.animator.FactorAnimator;

/**
 * Attach to a RecyclerView (call {@link #onIntercept} from onInterceptTouchEvent and
 * {@link #onTouch} from onTouchEvent) to replace smooth scrolling with page flips:
 *
 *  - vertical drag beyond the threshold triggers ONE page flip on release,
 *    without any follow-finger scrolling in between;
 *  - taps, long-presses and horizontal gestures pass through untouched;
 *  - fling inertia is swallowed (see also {@link #fling}).
 */
public class PageFlipTouchController {
  private static final int STATE_UNDECIDED = 0;
  private static final int STATE_PAGING = 1;
  private static final int STATE_PASSTHROUGH = 2;

  private final RecyclerView recyclerView;

  private int state = STATE_UNDECIDED;
  private float startX, startY, lastY;

  public PageFlipTouchController (RecyclerView recyclerView) {
    this.recyclerView = recyclerView;
  }

  private static boolean enabled () {
    return FactorAnimator.FORCE_INSTANT;
  }

  /** @return true when the touch sequence must be intercepted by the RecyclerView */
  public boolean onIntercept (MotionEvent e) {
    if (!enabled()) {
      return false;
    }
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        state = STATE_UNDECIDED;
        startX = e.getX();
        startY = lastY = e.getY();
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (state == STATE_UNDECIDED) {
          float dx = e.getX() - startX;
          float dy = e.getY() - startY;
          float slop = Screen.getTouchSlop();
          if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) {
            state = STATE_PAGING;
            return true; // intercept: children get CANCEL, further events go to onTouch
          } else if (Math.abs(dx) > slop) {
            state = STATE_PASSTHROUGH;
          }
        } else if (state == STATE_PAGING) {
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        if (state == STATE_PAGING) {
          return true;
        }
        break;
      }
    }
    return false;
  }

  /** @return true when the event was fully handled (super.onTouchEvent must be skipped) */
  public boolean onTouch (MotionEvent e) {
    if (!enabled()) {
      return false;
    }
    // Events may also be injected via dispatchTouchEvent, skipping onInterceptTouchEvent.
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      state = STATE_UNDECIDED;
      startX = e.getX();
      startY = lastY = e.getY();
      return false;
    }
    if (state == STATE_UNDECIDED && e.getActionMasked() == MotionEvent.ACTION_MOVE) {
      float dx = e.getX() - startX;
      float dy = e.getY() - startY;
      float slop = Screen.getTouchSlop();
      if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) {
        state = STATE_PAGING;
      } else if (Math.abs(dx) > slop) {
        state = STATE_PASSTHROUGH;
      } else {
        return false;
      }
    }
    if (state != STATE_PAGING) {
      return false;
    }
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_MOVE: {
        lastY = e.getY();
        return true; // swallow: no follow-finger scrolling
      }
      case MotionEvent.ACTION_UP: {
        float totalDy = e.getY() - startY;
        if (Math.abs(totalDy) >= Screen.dp(PageFlipHelper.FLING_THRESHOLD_DP)) {
          // finger moved UP (negative dy) => content moves up (next page)
          PageFlipHelper.pageBy(recyclerView, totalDy < 0f ? 1 : -1);
        }
        state = STATE_UNDECIDED;
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        state = STATE_UNDECIDED;
        return true;
      }
    }
    return true;
  }

  /** Call from RecyclerView.fling() to swallow inertia scrolling. */
  public boolean fling (int velocityX, int velocityY) {
    return enabled();
  }
}
