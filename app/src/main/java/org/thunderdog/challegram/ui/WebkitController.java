/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 15/11/2016
 */
package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.PageFlipHelper;

import me.vkryl.android.widget.FrameLayoutFix;

public class WebkitController<T> extends ViewController<T> {
  private WebView webView;
  private DoubleHeaderView headerCell;

  public WebkitController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_webkit;
  }

  @SuppressLint("SetJavaScriptEnabled")
  @Override
  protected final View onCreateView (Context context) {
    headerCell = new DoubleHeaderView(context());
    headerCell.setThemedTextColor(this);
    headerCell.initWithMargin(Screen.dp(49f), true);

    FrameLayoutFix contentView = new FrameLayoutFix(context) {
      @Override
      public boolean onTouchEvent (MotionEvent event) {
        return true;
      }
    };
    ViewSupport.setThemedBackground(contentView, ColorId.filling, this);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    // FIXME android.webkit.WebViewFactory$MissingWebViewPackageException
    webView = new WebView(context) {
      // Inkgram: page-flip scrolling instead of smooth scrolling (e-ink).
      private static final int STATE_UNDECIDED = 0;
      private static final int STATE_PAGING = 1;
      private static final int STATE_PASSTHROUGH = 2;

      private int touchState = STATE_UNDECIDED;
      private float touchStartX, touchStartY;

      @Override
      public boolean onInterceptTouchEvent (MotionEvent e) {
        if (me.vkryl.android.animator.FactorAnimator.FORCE_INSTANT) {
          switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
              touchState = STATE_UNDECIDED;
              touchStartX = e.getX();
              touchStartY = e.getY();
              break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
              touchState = STATE_PASSTHROUGH; // pinch zoom and other multi-touch gestures
              break;
            }
            case MotionEvent.ACTION_MOVE: {
              if (touchState == STATE_UNDECIDED) {
                float dx = e.getX() - touchStartX;
                float dy = e.getY() - touchStartY;
                float slop = Screen.getTouchSlop();
                if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) {
                  touchState = STATE_PAGING;
                  return true; // intercept: web content gets CANCEL, no follow-finger scrolling
                } else if (Math.abs(dx) > slop) {
                  touchState = STATE_PASSTHROUGH;
                }
              } else if (touchState == STATE_PAGING) {
                return true;
              }
              break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
              if (touchState == STATE_PAGING) {
                return true;
              }
              break;
            }
          }
        }
        return super.onInterceptTouchEvent(e);
      }

      @SuppressLint("ClickableViewAccessibility")
      @Override
      public boolean onTouchEvent (MotionEvent e) {
        if (me.vkryl.android.animator.FactorAnimator.FORCE_INSTANT && touchState == STATE_PAGING) {
          switch (e.getActionMasked()) {
            case MotionEvent.ACTION_UP: {
              float totalDy = e.getY() - touchStartY;
              if (Math.abs(totalDy) >= Screen.dp(PageFlipHelper.FLING_THRESHOLD_DP)) {
                // finger moved UP (negative dy) => content moves up (next page)
                PageFlipHelper.pageByView(this, totalDy < 0f ? 1 : -1);
              }
              touchState = STATE_UNDECIDED;
              return true;
            }
            case MotionEvent.ACTION_CANCEL: {
              touchState = STATE_UNDECIDED;
              return true;
            }
            default: {
              return true; // swallow MOVEs: no follow-finger scrolling
            }
          }
        }
        return super.onTouchEvent(e);
      }
    };
    webView.getSettings().setJavaScriptEnabled(true);
    webView.getSettings().setDomStorageEnabled(true);
    webView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      // FIXME maybe better to remove?
      webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
      CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
    }

    if (hasSpecialProcessing()) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        webView.setWebViewClient(new WebViewClient() {
          @Override
          public void onPageFinished (WebView view, String url) {
            Uri uri;
            try {
              uri = Uri.parse(url);
            } catch (Throwable t) {
              uri = null;
            }
            if (uri == null || !processSpecial(uri))
              super.onPageFinished(view, url);
          }

          @Override
          public boolean shouldOverrideUrlLoading (WebView view, WebResourceRequest request) {
            return processSpecial(request.getUrl()) || super.shouldOverrideUrlLoading(view, request);
          }
        });
      } else {
        webView.setWebViewClient(new WebViewClient() {
          @Override
          public void onPageFinished (WebView view, String url) {
            Uri uri;
            try {
              uri = Uri.parse(url);
            } catch (Throwable t) {
              uri = null;
            }
            if (uri == null || !processSpecial(uri))
              super.onPageFinished(view, url);
          }

          @Override
          public boolean shouldOverrideUrlLoading (WebView view, String url) {
            Uri uri;
            try {
              uri = Uri.parse(url);
            } catch (Throwable t) {
              uri = null;
            }
            return (uri != null && processSpecial(uri)) || super.shouldOverrideUrlLoading(view, url);
          }
        });
      }
    } else {
      webView.setWebViewClient(new WebViewClient());
    }
    webView.setWebChromeClient(new WebChromeClient() {
      @Override
      public void onProgressChanged (WebView view, int newProgress) {
        onPageProgress((float) newProgress / 100f);
      }
    });
    onCreateWebView(headerCell, webView);

    contentView.addView(webView);

    return contentView;
  }

  @Override
  public View getViewForApplyingOffsets () {
    return webView;
  }

  protected void onCreateWebView (DoubleHeaderView headerCell, WebView webView) {
    if (getArguments() != null && getArguments() instanceof String) {
      headerCell.setSubtitle((String) getArguments());
      loadUrl((String) getArguments());
    }
  }

  protected final void loadUrl (String url) {
    webView.loadUrl(url);
  }

  protected void onPageProgress (float progress) {
    if (headerCell != null) {
      headerCell.animateProgress(progress);
    }
  }

  // Inkgram: volume keys flip pages in the in-app browser (e-ink).
  @Override
  public boolean onKeyDown (int keyCode, android.view.KeyEvent event) {
    if (me.vkryl.android.animator.FactorAnimator.FORCE_INSTANT) {
      if (dismissKeyboardByVolumeKey(keyCode)) {
        return true;
      }
      if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
        if (webView != null && PageFlipHelper.pageByView(webView, keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN ? 1 : -1)) {
          return true;
        }
      }
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp (int keyCode, android.view.KeyEvent event) {
    // Inkgram: swallow the key-up as well, so the system volume panel does not show.
    if (me.vkryl.android.animator.FactorAnimator.FORCE_INSTANT && (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) && webView != null) {
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }

  @Override
  public void destroy () {
    super.destroy();
    webView.destroy();
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  protected boolean hasSpecialProcessing () {
    return false;
  }

  protected boolean processSpecial (Uri url) {
    return false; // override
  }
}
