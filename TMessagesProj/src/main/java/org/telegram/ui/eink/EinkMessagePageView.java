package org.telegram.ui.eink;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;

public class EinkMessagePageView extends View {

    private final int currentAccount = UserConfig.selectedAccount;

    public interface OnPhotoClickListener {
        void onPhotoClick(MessageObject message);
    }

    public static class Page {
        public ArrayList<MessageObject> messages = new ArrayList<>();
        public ArrayList<Boolean> showSenders = new ArrayList<>();
    }

    public static class PhotoClickTarget {
        public MessageObject message;
        public Rect rect;

        public PhotoClickTarget(MessageObject message, Rect rect) {
            this.message = message;
            this.rect = rect;
        }
    }

    private Page currentPage;
    private ArrayList<PhotoClickTarget> photoClickTargets = new ArrayList<>();
    private OnPhotoClickListener photoClickListener;

    public EinkMessagePageView(Context context) {
        super(context);
        init();
    }

    public EinkMessagePageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setBackgroundColor(EinkTheme.COLOR_WHITE);
    }

    public void setPage(Page page) {
        this.currentPage = page;
        invalidate();
    }

    public void setOnPhotoClickListener(OnPhotoClickListener listener) {
        this.photoClickListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(EinkTheme.COLOR_WHITE);
        photoClickTargets.clear();

        if (currentPage == null || currentPage.messages.isEmpty()) {
            return;
        }

        int yCursor = AndroidUtilities.dp(16);
        int paddingX = AndroidUtilities.dp(16);
        int availableWidth = getWidth() - paddingX * 2;

        for (int i = 0; i < currentPage.messages.size(); i++) {
            MessageObject msg = currentPage.messages.get(i);
            boolean showSender = currentPage.showSenders.get(i);

            // Draw Sender Name if sequence first
            if (showSender) {
                long senderId = msg.getSenderId();
                String senderName = "";
                if (senderId > 0) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(senderId);
                    if (user != null) {
                        senderName = ContactsController.formatName(user.first_name, user.last_name);
                    }
                } else if (senderId < 0) {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-senderId);
                    if (chat != null) {
                        senderName = chat.title;
                    }
                }
                if (TextUtils.isEmpty(senderName)) {
                    senderName = "User " + senderId;
                }

                TextPaint usernamePaint = EinkTheme.getTextPaintUsername();
                Paint.FontMetrics fm = usernamePaint.getFontMetrics();
                float usernameHeight = fm.bottom - fm.top;
                canvas.drawText(senderName, paddingX, yCursor - fm.top, usernamePaint);
                yCursor += usernameHeight + AndroidUtilities.dp(4);
            }

            // Draw Message Body
            if (msg.isPhoto()) {
                // Draw elegant interactive square border photo placeholder
                int boxWidth = availableWidth;
                int boxHeight = AndroidUtilities.dp(60);
                int boxLeft = paddingX;
                int boxTop = yCursor;
                int boxRight = boxLeft + boxWidth;
                int boxBottom = boxTop + boxHeight;

                canvas.drawRect(boxLeft, boxTop, boxRight, boxBottom, EinkTheme.getBorderPaint());

                TextPaint textPaint = EinkTheme.getTextPaintBody();
                textPaint.setTypeface(EinkTheme.SERIF_BOLD);
                float textWidth = textPaint.measureText("[图片]");
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float textX = boxLeft + (boxWidth - textWidth) / 2.0f;
                float textY = boxTop + (boxHeight - (fm.bottom - fm.top)) / 2.0f - fm.top;

                canvas.drawText("[图片]", textX, textY, textPaint);

                // Register touch click target
                photoClickTargets.add(new PhotoClickTarget(msg, new Rect(boxLeft, boxTop, boxRight, boxBottom)));
                yCursor += boxHeight + AndroidUtilities.dp(8);
            } else {
                String bodyText = "";
                if (msg.isSticker()) {
                    String emoji = msg.getStickerEmoji();
                    bodyText = "[贴纸 " + (emoji != null ? emoji : "") + "]";
                } else {
                    CharSequence txt = msg.messageText;
                    if (txt == null && msg.messageTextShort != null) {
                        txt = msg.messageTextShort;
                    }
                    bodyText = txt != null ? txt.toString() : "";
                }

                TextPaint paint = EinkTheme.getTextPaintBody();
                StaticLayout layout = new StaticLayout(bodyText, paint, availableWidth, Layout.Alignment.ALIGN_NORMAL, EinkTheme.LINE_SPACING_MULTIPLIER, 0.0f, false);
                canvas.save();
                canvas.translate(paddingX, yCursor);
                layout.draw(canvas);
                canvas.restore();

                yCursor += layout.getHeight() + AndroidUtilities.dp(8);
            }

            // Draw Meta (Timestamp) right-aligned
            String timeStr = LocaleController.getInstance().getFormatterDay().format((long) (msg.messageOwner.date) * 1000);
            TextPaint timePaint = EinkTheme.getTextPaintMeta();
            float timeWidth = timePaint.measureText(timeStr);
            float timeX = getWidth() - paddingX - timeWidth;
            Paint.FontMetrics tfm = timePaint.getFontMetrics();
            float timeHeight = tfm.bottom - tfm.top;

            canvas.drawText(timeStr, timeX, yCursor - tfm.top, timePaint);
            yCursor += timeHeight + AndroidUtilities.dp(16); // Includes message spacing
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX();
            float y = event.getY();
            for (PhotoClickTarget target : photoClickTargets) {
                if (target.rect.contains((int) x, (int) y)) {
                    if (photoClickListener != null) {
                        photoClickListener.onPhotoClick(target.message);
                    }
                    return true;
                }
            }
        }
        return super.onTouchEvent(event);
    }

    /**
     * Compute message drawing height accurately for pagination partitioning
     */
    public static int measureMessage(MessageObject msg, boolean showSender, int width, int currentAccount) {
        int height = 0;

        // Header height
        if (showSender) {
            Paint.FontMetrics fm = EinkTheme.getTextPaintUsername().getFontMetrics();
            height += (fm.bottom - fm.top) + AndroidUtilities.dp(4);
        }

        // Body height
        if (msg.isPhoto()) {
            height += AndroidUtilities.dp(60) + AndroidUtilities.dp(8);
        } else {
            String bodyText = "";
            if (msg.isSticker()) {
                String emoji = msg.getStickerEmoji();
                bodyText = "[贴纸 " + (emoji != null ? emoji : "") + "]";
            } else {
                CharSequence txt = msg.messageText;
                if (txt == null && msg.messageTextShort != null) {
                    txt = msg.messageTextShort;
                }
                bodyText = txt != null ? txt.toString() : "";
            }

            TextPaint paint = EinkTheme.getTextPaintBody();
            int availableWidth = width - AndroidUtilities.dp(32);
            if (availableWidth <= 0) {
                availableWidth = AndroidUtilities.dp(300);
            }
            StaticLayout layout = new StaticLayout(bodyText, paint, availableWidth, Layout.Alignment.ALIGN_NORMAL, EinkTheme.LINE_SPACING_MULTIPLIER, 0.0f, false);
            height += layout.getHeight() + AndroidUtilities.dp(8);
        }

        // Timestamp height
        Paint.FontMetrics tfm = EinkTheme.getTextPaintMeta().getFontMetrics();
        height += (tfm.bottom - tfm.top) + AndroidUtilities.dp(16);

        return height;
    }
}
