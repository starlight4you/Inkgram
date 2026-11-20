/*
 * This file is a part of Telegram X
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.component.chat;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.core.Media;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/** A large, white drawing surface. Only the exported image leaves this dialog. */
public final class DrawingDialog extends Dialog {
  public interface SendListener {
    void onSend (File file, Runnable onSent);
  }

  private final DrawingView drawing;
  private final Button sendButton, undoButton, clearButton;
  private boolean saving;
  private final ArrayList<File> exports = new ArrayList<>();

  public DrawingDialog (Context context, SendListener listener) {
    super(context);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setCanceledOnTouchOutside(false);

    LinearLayout content = new LinearLayout(context);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(Screen.dp(8), Screen.dp(8), Screen.dp(8), Screen.dp(8));
    content.setBackgroundColor(Color.WHITE);
    setContentView(content);

    LinearLayout header = row(context);
    Button cancel = button(context, R.string.Cancel);
    cancel.setOnClickListener(v -> dismiss());
    header.addView(cancel);
    TextView title = label(context, Lang.getString(R.string.DrawingTitle));
    title.setGravity(Gravity.CENTER);
    header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    sendButton = button(context, R.string.Send);
    header.addView(sendButton);
    content.addView(header);

    drawing = new DrawingView(context);
    content.addView(drawing, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

    LinearLayout sizeRow = row(context);
    TextView sizeLabel = label(context, "");
    sizeRow.addView(sizeLabel);
    SeekBar size = new SeekBar(context);
    size.setMax(39);
    size.setProgress(3);
    size.setContentDescription(Lang.getString(R.string.BrushSize, "4"));
    sizeLabel.setText(Lang.getString(R.string.BrushSize, "4"));
    sizeRow.addView(size, new LinearLayout.LayoutParams(0, Screen.dp(48), 1));
    size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged (SeekBar bar, int progress, boolean fromUser) {
        drawing.brushSize = Screen.dp(progress + 1);
        String text = Lang.getString(R.string.BrushSize, Integer.toString(progress + 1));
        sizeLabel.setText(text);
        size.setContentDescription(text);
      }
      @Override public void onStartTrackingTouch (SeekBar bar) { }
      @Override public void onStopTrackingTouch (SeekBar bar) { }
    });
    content.addView(sizeRow);

    HorizontalScrollView paletteScroll = new HorizontalScrollView(context);
    LinearLayout palette = row(context);
    paletteScroll.addView(palette);
    int[] colors = {Color.BLACK, Color.WHITE, 0xffd32f2f, 0xffef6c00, 0xff2e7d32, 0xff1565c0, 0xff7b1fa2};
    int[] names = {R.string.DrawingBlack, R.string.DrawingWhite, R.string.DrawingRed, R.string.DrawingOrange, R.string.DrawingGreen, R.string.DrawingBlue, R.string.DrawingPurple};
    for (int i = 0; i < colors.length; i++) {
      final int color = colors[i];
      TextView swatch = label(context, color == Color.BLACK ? "✓" : "");
      swatch.setTextColor(color == Color.BLACK || color == 0xff1565c0 || color == 0xff7b1fa2 ? Color.WHITE : Color.BLACK);
      swatch.setTextSize(24);
      swatch.setGravity(Gravity.CENTER);
      swatch.setContentDescription(Lang.getString(R.string.DrawingColor) + ": " + Lang.getString(names[i]));
      GradientDrawable background = new GradientDrawable();
      background.setColor(color);
      background.setStroke(Screen.dp(1), Color.BLACK);
      swatch.setBackground(background);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Screen.dp(44), Screen.dp(44));
      params.setMargins(Screen.dp(3), Screen.dp(4), Screen.dp(3), Screen.dp(4));
      palette.addView(swatch, params);
      swatch.setSelected(color == Color.BLACK);
      swatch.setOnClickListener(v -> {
        drawing.brushColor = color;
        for (int j = 0; j < palette.getChildCount(); j++) {
          TextView item = (TextView) palette.getChildAt(j);
          item.setText(item == swatch ? "✓" : "");
          item.setSelected(item == swatch);
        }
      });
    }
    content.addView(paletteScroll);

    LinearLayout actions = row(context);
    undoButton = button(context, R.string.DrawingUndo);
    clearButton = button(context, R.string.ClearDrawing);
    actions.addView(undoButton);
    actions.addView(clearButton);
    content.addView(actions);
    drawing.onChanged = this::updateButtons;
    undoButton.setOnClickListener(v -> drawing.undo());
    clearButton.setOnClickListener(v -> drawing.clear());
    sendButton.setOnClickListener(v -> {
      if (saving || drawing.strokes.isEmpty()) return;
      final Bitmap bitmap;
      try {
        bitmap = drawing.export();
      } catch (RuntimeException | OutOfMemoryError error) {
        showSaveError();
        return;
      }
      saving = true;
      drawing.setEnabled(false);
      updateButtons();
      Media.instance().post(() -> {
        File file = null;
        boolean success = false;
        try {
          file = File.createTempFile("drawing_", ".png", context.getCacheDir());
          try (FileOutputStream out = new FileOutputStream(file)) {
            success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
          }
        } catch (IOException | RuntimeException error) {
          success = false;
        } finally {
          bitmap.recycle();
        }
        final File result = file;
        final boolean saved = success;
        UI.post(() -> {
          saving = false;
          drawing.setEnabled(true);
          updateButtons();
          if (!isShowing() || !saved) {
            if (result != null) result.delete();
            if (isShowing()) showSaveError();
            return;
          }
          exports.add(result);
          listener.onSend(result, () -> markSent(result));
        });
      });
    });
    updateButtons();
  }

  @Override
  protected void onStart () {
    super.onStart();
    Window window = getWindow();
    if (window != null) {
      window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
      window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
  }

  @Override
  public void dismiss () {
    for (File file : exports) file.delete();
    exports.clear();
    super.dismiss();
  }

  private void markSent (File file) {
    // TDLib's asynchronous file generation still needs the submitted file.
    exports.remove(file);
    dismiss();
  }

  private void showSaveError () {
    Toast.makeText(getContext(), Lang.getString(R.string.DrawingSaveError), Toast.LENGTH_SHORT).show();
  }

  private void updateButtons () {
    boolean hasDrawing = !drawing.strokes.isEmpty();
    sendButton.setEnabled(!saving && hasDrawing);
    undoButton.setEnabled(!saving && hasDrawing);
    clearButton.setEnabled(!saving && hasDrawing);
  }

  private static LinearLayout row (Context context) {
    LinearLayout row = new LinearLayout(context);
    row.setGravity(Gravity.CENTER_VERTICAL);
    return row;
  }

  private static TextView label (Context context, String text) {
    TextView label = new TextView(context);
    label.setText(text);
    label.setTextColor(Color.BLACK);
    label.setTextSize(16);
    return label;
  }

  private static Button button (Context context, int text) {
    Button button = new Button(context);
    button.setText(Lang.getString(text));
    button.setTextColor(Color.BLACK);
    button.setAllCaps(false);
    return button;
  }

  private static final class Stroke {
    final Path path = new Path();
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Stroke (int color, float size, float x, float y) {
      paint.setColor(color);
      paint.setStrokeWidth(size);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setStrokeJoin(Paint.Join.ROUND);
      path.moveTo(x, y);
      path.lineTo(x + .01f, y); // A tap draws a dot too.
    }
  }

  private static final class DrawingView extends View {
    // Retain vector strokes, rather than a bitmap per undo step.
    final ArrayList<Stroke> strokes = new ArrayList<>();
    final Paint border = new Paint();
    final RectF viewport = new RectF();
    Stroke current;
    int pointerId = -1;
    int brushColor = Color.BLACK;
    float brushSize = Screen.dp(4);
    int canvasWidth, canvasHeight;
    Runnable onChanged;

    DrawingView (Context context) {
      super(context);
      setBackgroundColor(Color.WHITE);
      setLayerType(View.LAYER_TYPE_SOFTWARE, null);
      border.setColor(Color.BLACK);
      border.setStyle(Paint.Style.STROKE);
      border.setStrokeWidth(Screen.dp(1));
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
      if (canvasWidth == 0 && w > 0 && h > 0) {
        canvasWidth = w;
        canvasHeight = h;
      }
      if (canvasWidth > 0 && canvasHeight > 0) {
        float scale = Math.min((float) w / canvasWidth, (float) h / canvasHeight);
        float width = canvasWidth * scale, height = canvasHeight * scale;
        viewport.set((w - width) / 2, (h - height) / 2, (w + width) / 2, (h + height) / 2);
      }
    }

    private float scale () { return viewport.width() / canvasWidth; }

    @Override
    protected void onDraw (Canvas canvas) {
      super.onDraw(canvas);
      if (canvasWidth == 0 || viewport.isEmpty()) return;
      int save = canvas.save();
      canvas.clipRect(viewport);
      canvas.translate(viewport.left, viewport.top);
      canvas.scale(scale(), scale());
      drawStrokes(canvas);
      canvas.restoreToCount(save);
      canvas.drawRect(viewport.left + 1, viewport.top + 1, viewport.right - 1, viewport.bottom - 1, border);
    }

    private void drawStrokes (Canvas canvas) {
      for (Stroke stroke : strokes) canvas.drawPath(stroke.path, stroke.paint);
    }

    @Override
    public boolean onTouchEvent (MotionEvent event) {
      if (!isEnabled() || viewport.isEmpty()) return false;
      int action = event.getActionMasked();
      if (action == MotionEvent.ACTION_DOWN) {
        if (!viewport.contains(event.getX(), event.getY())) return false;
        getParent().requestDisallowInterceptTouchEvent(true);
        pointerId = event.getPointerId(0);
        current = new Stroke(brushColor, brushSize / scale(), x(event.getX()), y(event.getY()));
        strokes.add(current);
        onChanged.run();
      } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
        int index = event.findPointerIndex(pointerId);
        if (current != null && index >= 0) {
          for (int i = 0; i < event.getHistorySize(); i++) {
            current.path.lineTo(x(event.getHistoricalX(index, i)), y(event.getHistoricalY(index, i)));
          }
          current.path.lineTo(x(event.getX(index)), y(event.getY(index)));
        }
      }
      if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP ||
          (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex()) == pointerId)) {
        if (action == MotionEvent.ACTION_CANCEL && current != null) {
          strokes.remove(current);
          onChanged.run();
        }
        current = null;
        pointerId = -1;
        getParent().requestDisallowInterceptTouchEvent(false);
      }
      invalidate();
      return true;
    }

    private float x (float x) { return Math.max(0, Math.min(canvasWidth, (x - viewport.left) / scale())); }
    private float y (float y) { return Math.max(0, Math.min(canvasHeight, (y - viewport.top) / scale())); }

    void undo () {
      if (!strokes.isEmpty()) strokes.remove(strokes.size() - 1);
      invalidate();
      onChanged.run();
    }

    void clear () {
      strokes.clear();
      invalidate();
      onChanged.run();
    }

    Bitmap export () {
      float scale = Math.min(1f, 2048f / Math.max(canvasWidth, canvasHeight));
      Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(canvasWidth * scale)), Math.max(1, Math.round(canvasHeight * scale)), Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      canvas.drawColor(Color.WHITE);
      canvas.scale(scale, scale);
      drawStrokes(canvas);
      return bitmap;
    }
  }
}
