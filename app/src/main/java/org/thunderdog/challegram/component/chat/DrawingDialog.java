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
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Shader;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import org.thunderdog.challegram.util.IReaderPen;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
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
import org.thunderdog.challegram.util.IReaderHandwriting;

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
  private final IconButton sendButton, undoButton, redoButton, clearButton, eraserButton;
  private final View brushPreview;
  private final IReaderHandwriting handwriting;
  private boolean saving, erasing;
  private int penColor = Color.BLACK;
  private final ArrayList<File> exports = new ArrayList<>();

  public DrawingDialog (Context context, SendListener listener) {
    super(context);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setCanceledOnTouchOutside(false);

    FrameLayout backdrop = new FrameLayout(context);
    int cell = Screen.dp(3);
    Bitmap tile = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888);
    Canvas tileCanvas = new Canvas(tile);
    Paint tilePaint = new Paint();
    tilePaint.setColor(Color.BLACK);
    tileCanvas.drawRect(0, 0, cell, cell, tilePaint);
    tileCanvas.drawRect(cell, cell, cell * 2, cell * 2, tilePaint);
    BitmapDrawable mosaic = new BitmapDrawable(context.getResources(), tile);
    mosaic.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    mosaic.setFilterBitmap(false);
    backdrop.setBackground(mosaic);
    int margin = Screen.dp(16);
    backdrop.setPadding(margin, margin, margin, margin);
    setContentView(backdrop);

    LinearLayout content = new LinearLayout(context);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(Screen.dp(12), Screen.dp(8), Screen.dp(12), Screen.dp(12));
    GradientDrawable panel = new GradientDrawable();
    panel.setColor(Color.WHITE);
    panel.setCornerRadius(Screen.dp(8));
    panel.setStroke(Screen.dp(2), Color.BLACK);
    content.setBackground(panel);
    backdrop.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    LinearLayout header = row(context);
    IconButton cancel = new IconButton(context, IconButton.CLOSE, R.string.Cancel);
    cancel.setOnClickListener(v -> dismiss());
    header.addView(cancel);
    TextView title = label(context, Lang.getString(R.string.DrawingTitle));
    title.setGravity(Gravity.CENTER);
    title.setTextSize(22);
    header.addView(title, new LinearLayout.LayoutParams(0, Screen.dp(52), 1));
    View balance = new View(context);
    header.addView(balance, new LinearLayout.LayoutParams(Screen.dp(48), 1));
    content.addView(header);

    drawing = new DrawingView(context);
    FrameLayout board = new FrameLayout(context);
    board.setBackgroundColor(Color.WHITE);
    board.addView(drawing, new FrameLayout.LayoutParams(-1, -1));
    content.addView(board, new LinearLayout.LayoutParams(-1, 0, 1));
    handwriting = IReaderHandwriting.create(context, board, new IReaderHandwriting.Listener() {
      @Override public void onPoint (int action, float x, float y, long time, float pressure) { drawing.hardwarePoint(action, x, y, time, pressure); }
      @Override public void onDraw (Canvas canvas) { drawing.render(canvas); }
      @Override public void onAvailabilityChanged (boolean active) { drawing.hardwareActive = active; }
    });
    drawing.handwriting = handwriting;

    LinearLayout sizeRow = row(context);
    brushPreview = new View(context) {
      private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      @Override protected void onDraw (Canvas canvas) {
        float radius = drawing.brushSize / 2;
        paint.setColor(drawing.erasing ? Color.WHITE : drawing.brushColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, paint);
        if (drawing.erasing || drawing.brushColor == Color.WHITE) {
          paint.setColor(Color.BLACK);
          paint.setStyle(Paint.Style.STROKE);
          paint.setStrokeWidth(Screen.dp(1));
          canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, paint);
        }
      }
    };
    sizeRow.addView(brushPreview, new LinearLayout.LayoutParams(Screen.dp(52), Screen.dp(52)));
    SeekBar size = new SeekBar(context) {
      private final Paint rail = new Paint(Paint.ANTI_ALIAS_FLAG);
      @Override protected synchronized void onDraw (Canvas canvas) {
        rail.setColor(Color.BLACK);
        rail.setStrokeWidth(Screen.dp(2));
        rail.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(getPaddingLeft(), getHeight() / 2f, getWidth() - getPaddingRight(), getHeight() / 2f, rail);
        super.onDraw(canvas);
      }
    };
    size.setProgressDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    GradientDrawable thumb = new GradientDrawable();
    thumb.setShape(GradientDrawable.OVAL);
    thumb.setColor(Color.BLACK);
    thumb.setSize(Screen.dp(14), Screen.dp(14));
    size.setThumb(thumb);
    size.setSplitTrack(false);
    size.setMax(39);
    size.setProgress(3);
    size.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.BLACK));
    size.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.BLACK));
    size.setContentDescription(Lang.getString(R.string.BrushSize, "4"));
    sizeRow.addView(size, new LinearLayout.LayoutParams(0, Screen.dp(52), 1));
    size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged (SeekBar bar, int progress, boolean fromUser) {
        drawing.brushSize = Screen.dp(progress + 1);
        size.setContentDescription(Lang.getString(R.string.BrushSize, Integer.toString(progress + 1)));
        brushPreview.invalidate();
        handwriting.setPen(drawing.brushSize, drawing.brushColor);
      }
      @Override public void onStartTrackingTouch (SeekBar bar) { }
      @Override public void onStopTrackingTouch (SeekBar bar) { }
    });
    content.addView(sizeRow);

    HorizontalScrollView paletteScroll = new HorizontalScrollView(context);
    paletteScroll.setHorizontalScrollBarEnabled(false);
    LinearLayout palette = row(context);
    paletteScroll.addView(palette);
    int[] colors = {Color.BLACK, Color.WHITE, 0xffd32f2f, 0xffef6c00, 0xff2e7d32, 0xff1565c0, 0xff7b1fa2};
    int[] names = {R.string.DrawingBlack, R.string.DrawingWhite, R.string.DrawingRed, R.string.DrawingOrange, R.string.DrawingGreen, R.string.DrawingBlue, R.string.DrawingPurple};
    for (int i = 0; i < colors.length; i++) {
      final int color = colors[i];
      TextView swatch = label(context, Lang.getString(names[i]));
      swatch.setTextColor(color == Color.WHITE || color == 0xffef6c00 ? Color.BLACK : Color.WHITE);
      swatch.setTextSize(14);
      swatch.setSingleLine(true);
      swatch.setTypeface(null, color == Color.BLACK ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
      swatch.setGravity(Gravity.CENTER);
      swatch.setContentDescription(Lang.getString(R.string.DrawingColor) + ": " + Lang.getString(names[i]));
      GradientDrawable background = new GradientDrawable();
      background.setColor(color);
      background.setCornerRadius(Screen.dp(5));
      background.setStroke(Screen.dp(color == Color.BLACK ? 3 : 1), Color.BLACK);
      swatch.setBackground(background);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Screen.dp(56), Screen.dp(44));
      params.setMargins(Screen.dp(3), Screen.dp(4), Screen.dp(3), Screen.dp(4));
      palette.addView(swatch, params);
      swatch.setSelected(color == Color.BLACK);
      swatch.setOnClickListener(v -> {
        penColor = color;
        setEraser(false);
        for (int j = 0; j < palette.getChildCount(); j++) {
          TextView item = (TextView) palette.getChildAt(j);
          item.setTypeface(null, item == swatch ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
          item.setPaintFlags(item == swatch ? item.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG : item.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
          ((GradientDrawable) item.getBackground()).setStroke(Screen.dp(item == swatch ? 3 : 1), Color.BLACK);
          item.setSelected(item == swatch);
        }
      });
    }
    content.addView(paletteScroll);

    LinearLayout actions = row(context);
    undoButton = new IconButton(context, IconButton.UNDO, R.string.DrawingUndo);
    redoButton = new IconButton(context, IconButton.REDO, R.string.DrawingRedo);
    clearButton = new IconButton(context, IconButton.CLEAR, R.string.ClearDrawing);
    eraserButton = new IconButton(context, IconButton.ERASER, R.string.DrawingEraser);
    sendButton = new IconButton(context, IconButton.SEND, R.string.Send);
    actions.addView(undoButton);
    actions.addView(redoButton);
    actions.addView(clearButton);
    actions.addView(eraserButton);
    actions.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1));
    actions.addView(sendButton, new LinearLayout.LayoutParams(Screen.dp(56), Screen.dp(56)));
    content.addView(actions);
    drawing.onChanged = this::updateButtons;
    undoButton.setOnClickListener(v -> drawing.undo());
    redoButton.setOnClickListener(v -> drawing.redo());
    clearButton.setOnClickListener(v -> drawing.clear());
    eraserButton.setOnClickListener(v -> setEraser(!erasing));
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
      handwriting.setActive(false);
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
          handwriting.setActive(isShowing() && getWindow() != null && getWindow().getDecorView().hasWindowFocus());
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
      window.setBackgroundDrawableResource(android.R.color.transparent);
      window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      window.setDimAmount(0f);
      window.setWindowAnimations(0);
      window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
      window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
  }

  @Override
  public void onWindowFocusChanged (boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    handwriting.setActive(hasFocus && !saving);
  }

  @Override
  public void dismiss () {
    handwriting.close();
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

  private void setEraser (boolean enabled) {
    erasing = enabled;
    drawing.erasing = enabled;
    drawing.brushColor = penColor;
    eraserButton.setSelected(enabled);
    eraserButton.invalidate();
    brushPreview.invalidate();
    handwriting.setErasing(enabled);
    handwriting.setPen(drawing.brushSize, drawing.brushColor);
  }

  private void updateButtons () {
    eraserButton.setEnabled(!saving);
    boolean hasDrawing = !drawing.strokes.isEmpty();
    sendButton.setEnabled(!saving && hasDrawing);
    undoButton.setEnabled(!saving && !drawing.undone.isEmpty());
    redoButton.setEnabled(!saving && !drawing.redone.isEmpty());
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

  /** Monochrome symbols drawn without tinted framework button backgrounds. */
  private static final class IconButton extends View {
    static final int CLOSE = 0, UNDO = 1, REDO = 2, CLEAR = 3, SEND = 4, ERASER = 5;
    private final int icon;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    IconButton (Context context, int icon, int label) {
      super(context);
      this.icon = icon;
      setContentDescription(Lang.getString(label));
      setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(48), Screen.dp(52)));
      setClickable(true);
      setFocusable(true);
    }
    @Override public void setEnabled (boolean enabled) { super.setEnabled(enabled); invalidate(); }
    @Override protected void onDraw (Canvas canvas) {
      super.onDraw(canvas);
      canvas.save();
      canvas.translate(getWidth() / 2f, getHeight() / 2f);
      canvas.scale(Screen.dp(1), Screen.dp(1));
      paint.setColor(Color.BLACK);
      paint.setStrokeWidth(2.4f);
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setStrokeJoin(Paint.Join.ROUND);
      paint.setStyle(Paint.Style.STROKE);
      if (icon == SEND) {
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(0, 0, 24, paint);
        paint.setColor(Color.WHITE);
        Path arrow = new Path();
        arrow.moveTo(-9, -10); arrow.lineTo(12, 0); arrow.lineTo(-9, 10); arrow.lineTo(-5, 0); arrow.close();
        canvas.drawPath(arrow, paint);
      } else if (icon == CLOSE) {
        canvas.drawLine(-8, -8, 8, 8, paint); canvas.drawLine(-8, 8, 8, -8, paint);
      } else if (icon == ERASER) {
        if (isSelected()) {
          paint.setStyle(Paint.Style.FILL);
          canvas.drawRoundRect(-20, -20, 20, 20, 5, 5, paint);
          paint.setColor(Color.WHITE);
          paint.setStyle(Paint.Style.STROKE);
        }
        Path eraser = new Path();
        eraser.moveTo(-11, 3); eraser.lineTo(2, -10); eraser.lineTo(12, 0);
        eraser.lineTo(1, 11); eraser.lineTo(-4, 11); eraser.close();
        canvas.drawPath(eraser, paint);
        canvas.drawLine(-5, -3, 5, 7, paint);
        canvas.drawLine(-4, 11, 12, 11, paint);
      } else if (icon == CLEAR) {
        canvas.drawLine(-10, -7, 10, -7, paint);
        canvas.drawLine(-4, -11, 4, -11, paint);
        canvas.drawRect(-7, -7, 7, 11, paint);
        canvas.drawLine(-2, -2, -2, 6, paint); canvas.drawLine(2, -2, 2, 6, paint);
      } else {
        if (icon == REDO) canvas.scale(-1, 1);
        Path arrow = new Path();
        arrow.moveTo(-10, -2); arrow.cubicTo(3, -13, 14, -2, 8, 9);
        canvas.drawPath(arrow, paint);
        canvas.drawLine(-10, -2, -10, -10, paint); canvas.drawLine(-10, -2, -2, -2, paint);
      }
      // Disabled controls stay black; a small slash distinguishes unavailable actions.
      if (!isEnabled()) {
        paint.setColor(icon == SEND ? Color.WHITE : Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        canvas.drawLine(-12, 14, 12, 14, paint);
      }
      canvas.restore();
    }
  }

  private static final class Stroke {
    final Path path = new Path();
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final ArrayList<float[]> samples = new ArrayList<>();
    final IReaderPen nativePen;
    Path outline;
    Stroke (int color, float size, float x, float y) {
      nativePen = new IReaderPen(size, color);
      paint.setColor(color);
      paint.setStrokeWidth(size);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setStrokeJoin(Paint.Join.ROUND);
      path.moveTo(x, y);
      path.lineTo(x + .01f, y); // A tap draws a dot too.
    }
    void point (int action, float x, float y, long time, float pressure) {
      path.lineTo(x, y);
      float p = Math.max(0.05f, Math.min(1f, pressure));
      samples.add(new float[] {x, y, p});
      nativePen.point(action, x, y, time, pressure);
      if (action == MotionEvent.ACTION_UP) outline = nativePen.finish();
    }
    void draw (Canvas canvas) {
      if (outline != null) {
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(outline, paint);
        return;
      }
      paint.setStyle(Paint.Style.STROKE);
      float base = paint.getStrokeWidth();
      for (int i = 0; i < samples.size(); i++) {
        float[] b = samples.get(i), a = samples.get(Math.max(0, i - 1));
        paint.setStrokeWidth(base * (0.25f + 0.75f * (a[2] + b[2]) / 2));
        canvas.drawLine(a[0], a[1], b[0] + .01f, b[1], paint);
      }
      paint.setStrokeWidth(base);
    }
  }

  private static final class DrawingView extends View {
    // Retain vector strokes, rather than a bitmap per undo step.
    final ArrayList<Stroke> strokes = new ArrayList<>();
    final ArrayList<ArrayList<Stroke>> undone = new ArrayList<>();
    final ArrayList<ArrayList<Stroke>> redone = new ArrayList<>();
    ArrayList<Stroke> before;
    boolean erasing;
    float lastEraseX, lastEraseY;
    IReaderHandwriting handwriting;
    boolean hardwareActive, stylusUsed;
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
      render(canvas);
    }

    void render (Canvas canvas) {
      canvas.drawColor(Color.WHITE);
      if (canvasWidth == 0 || viewport.isEmpty()) return;
      int save = canvas.save();
      Path clip = new Path();
      clip.addRoundRect(viewport, Screen.dp(8), Screen.dp(8), Path.Direction.CW);
      canvas.clipPath(clip);
      canvas.translate(viewport.left, viewport.top);
      canvas.scale(scale(), scale());
      drawStrokes(canvas);
      canvas.restoreToCount(save);
      canvas.drawRoundRect(new RectF(viewport.left + 1, viewport.top + 1, viewport.right - 1, viewport.bottom - 1), Screen.dp(8), Screen.dp(8), border);
    }

    private void drawStrokes (Canvas canvas) {
      for (Stroke stroke : strokes) stroke.draw(canvas);
    }

    @Override
    public boolean onTouchEvent (MotionEvent event) {
      if (!isEnabled() || viewport.isEmpty()) return false;
      int tool = event.getToolType(event.getActionIndex());
      boolean pen = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER;
      if (pen && event.getActionMasked() == MotionEvent.ACTION_DOWN) lockToStylus();
      if (stylusUsed && !pen) return true;
      if (hardwareActive && pen) return true;
      if (event.getActionMasked() == MotionEvent.ACTION_DOWN && android.os.Build.VERSION.SDK_INT >= 21) requestUnbufferedDispatch(event);
      int action = event.getActionMasked();
      if (action == MotionEvent.ACTION_DOWN) {
        if (!viewport.contains(event.getX(), event.getY())) return false;
        getParent().requestDisallowInterceptTouchEvent(true);
        pointerId = event.getPointerId(0);
      }
      int index = event.findPointerIndex(pointerId);
      if (index >= 0) {
        for (int i = 0; action == MotionEvent.ACTION_MOVE && i < event.getHistorySize(); i++) {
          point(MotionEvent.ACTION_MOVE, event.getHistoricalX(index, i), event.getHistoricalY(index, i), event.getHistoricalEventTime(i), tool == MotionEvent.TOOL_TYPE_FINGER ? 1 : event.getHistoricalPressure(index, i));
        }
        point(action, event.getX(index), event.getY(index), event.getEventTime(), tool == MotionEvent.TOOL_TYPE_FINGER ? 1 : event.getPressure(index));
      }
      if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_UP) {
        pointerId = -1;
        getParent().requestDisallowInterceptTouchEvent(false);
      }
      invalidate();
      if (handwriting != null && hardwareActive) handwriting.render();
      return true;
    }

    private float x (float x) { return Math.max(0, Math.min(canvasWidth, (x - viewport.left) / scale())); }
    private float y (float y) { return Math.max(0, Math.min(canvasHeight, (y - viewport.top) / scale())); }

    private void lockToStylus () {
      if (stylusUsed) return;
      stylusUsed = true;
      // Roll back any finger gesture already in flight when the pen arrives.
      if (pointerId != -1 && before != null) {
        strokes.clear(); strokes.addAll(before);
        before = null; current = null; pointerId = -1;
        changed();
      }
    }

    void hardwarePoint (int action, float px, float py, long time, float pressure) {
      if (action == MotionEvent.ACTION_DOWN) lockToStylus();
      point(action, px, py, time, pressure);
    }

    private void point (int action, float px, float py, long time, float pressure) {
      if (!isEnabled() || viewport.isEmpty()) return;
      float x = x(px), y = y(py);
      if (action == MotionEvent.ACTION_DOWN) {
        if (!viewport.contains(px, py)) return;
        before = new ArrayList<>(strokes);
        redone.clear();
        if (!erasing) {
          current = new Stroke(brushColor, brushSize / scale(), x, y);
          strokes.add(current);
        }
        lastEraseX = x; lastEraseY = y;
      }
      if (before == null) return;
      if (erasing) {
        erase(lastEraseX, lastEraseY, x, y);
        lastEraseX = x; lastEraseY = y;
        changed();
      } else if (current != null) {
        current.point(action, x, y, time, pressure);
      }
      if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
        if (action == MotionEvent.ACTION_CANCEL) {
          strokes.clear(); strokes.addAll(before);
        } else {
          undone.add(before);
          if (undone.size() > 100) undone.remove(0);
        }
        before = null;
        current = null;
        changed();
      }
    }

    private void erase (float ax, float ay, float bx, float by) {
      float radius = brushSize / (2 * scale());
      float[] xy = new float[2];
      for (int i = strokes.size() - 1; i >= 0; i--) {
        Stroke stroke = strokes.get(i);
        float tolerance = radius + stroke.paint.getStrokeWidth() / 2;
        RectF bounds = new RectF();
        stroke.path.computeBounds(bounds, true);
        bounds.inset(-tolerance, -tolerance);
        if (Math.max(ax, bx) < bounds.left || Math.min(ax, bx) > bounds.right ||
            Math.max(ay, by) < bounds.top || Math.min(ay, by) > bounds.bottom) continue;
        PathMeasure measure = new PathMeasure(stroke.path, false);
        float length = measure.getLength();
        for (float d = 0; d <= length + 1; d += Math.max(1, Math.min(4, tolerance / 2))) {
          measure.getPosTan(Math.min(d, length), xy, null);
          float dx = bx - ax, dy = by - ay;
          float t = dx * dx + dy * dy == 0 ? 0 : Math.max(0, Math.min(1, ((xy[0] - ax) * dx + (xy[1] - ay) * dy) / (dx * dx + dy * dy)));
          if (Math.hypot(xy[0] - ax - t * dx, xy[1] - ay - t * dy) <= tolerance) {
            strokes.remove(i);
            break;
          }
        }
      }
    }

    private void changed () {
      invalidate();
      onChanged.run();
      if (handwriting != null) handwriting.render();
    }

    void undo () {
      if (undone.isEmpty()) return;
      redone.add(new ArrayList<>(strokes));
      strokes.clear(); strokes.addAll(undone.remove(undone.size() - 1));
      changed();
    }

    void redo () {
      if (redone.isEmpty()) return;
      undone.add(new ArrayList<>(strokes));
      strokes.clear(); strokes.addAll(redone.remove(redone.size() - 1));
      changed();
    }

    void clear () {
      if (strokes.isEmpty()) return;
      undone.add(new ArrayList<>(strokes));
      redone.clear();
      strokes.clear();
      changed();
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
