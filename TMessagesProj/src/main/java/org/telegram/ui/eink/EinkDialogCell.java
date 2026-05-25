package org.telegram.ui.eink;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.LayoutHelper;
import java.util.ArrayList;

public class EinkDialogCell extends FrameLayout {

    private final int currentAccount = UserConfig.selectedAccount;

    private TextView initialsTextView;
    private TextView nameTextView;
    private TextView previewTextView;
    private TextView unreadTextView;

    private long currentDialogId;

    public EinkDialogCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(EinkTheme.COLOR_WHITE);

        // Initials square box (40x40dp, 1px black outline, no rounded corners)
        initialsTextView = new TextView(context);
        initialsTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        initialsTextView.setTypeface(EinkTheme.SERIF_BOLD);
        initialsTextView.setTextColor(EinkTheme.COLOR_BLACK);
        initialsTextView.setGravity(Gravity.CENTER);

        GradientDrawable initialsBg = new GradientDrawable();
        initialsBg.setShape(GradientDrawable.RECTANGLE);
        initialsBg.setStroke(1, EinkTheme.COLOR_BLACK);
        initialsBg.setColor(EinkTheme.COLOR_WHITE);
        initialsBg.setCornerRadius(0);
        initialsTextView.setBackground(initialsBg);

        addView(initialsTextView, LayoutHelper.createFrame(40, 40, Gravity.LEFT | Gravity.CENTER_VERTICAL, 12, 0, 0, 0));

        // Name and Preview container
        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setGravity(Gravity.CENTER_VERTICAL);

        nameTextView = new TextView(context);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        nameTextView.setTypeface(EinkTheme.SERIF_BOLD);
        nameTextView.setTextColor(EinkTheme.COLOR_BLACK);
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        textContainer.addView(nameTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

        previewTextView = new TextView(context);
        previewTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        previewTextView.setTypeface(EinkTheme.SERIF_REGULAR);
        previewTextView.setTextColor(EinkTheme.COLOR_BLACK);
        previewTextView.setSingleLine(true);
        previewTextView.setEllipsize(TextUtils.TruncateAt.END);
        textContainer.addView(previewTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        addView(textContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 64, 0, 48, 0));

        // Unread badge (●)
        unreadTextView = new TextView(context);
        unreadTextView.setText("●");
        unreadTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        unreadTextView.setTextColor(EinkTheme.COLOR_BLACK);
        unreadTextView.setGravity(Gravity.CENTER);
        unreadTextView.setVisibility(View.GONE);
        addView(unreadTextView, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // High 64dp for comfortable touch
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64), MeasureSpec.EXACTLY));
    }

    public void setDialog(TLRPC.Dialog dialog) {
        if (dialog == null) {
            return;
        }
        currentDialogId = dialog.id;

        String title = "";
        String initials = "";

        if (currentDialogId > 0) {
            // User private chat
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(currentDialogId);
            if (user != null) {
                title = ContactsController.formatName(user.first_name, user.last_name);
                if (TextUtils.isEmpty(title)) {
                    title = user.username != null ? user.username : "";
                }
                initials = getInitials(title);
            }
        } else {
            // Chat / Channel / Group
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-currentDialogId);
            if (chat != null) {
                title = chat.title;
                if (ChatObject.isChannel(chat) && !chat.megagroup) {
                    title = "📢 " + title; // Visual distinction for channels
                }
                initials = getInitials(chat.title);
            }
        }

        nameTextView.setText(title);
        initialsTextView.setText(initials);

        // Fetch last message preview
        String previewText = "";
        ArrayList<MessageObject> groupMessages = MessagesController.getInstance(currentAccount).dialogMessage.get(dialog.id);
        if (groupMessages != null && !groupMessages.isEmpty()) {
            MessageObject message = groupMessages.get(0);
            if (message != null) {
                if (message.isPhoto()) {
                    previewText = "[图片]";
                } else if (message.isSticker()) {
                    String emoji = message.getStickerEmoji();
                    previewText = "[贴纸 " + (emoji != null ? emoji : "") + "]";
                } else {
                    CharSequence txt = message.messageText;
                    if (txt == null && message.messageTextShort != null) {
                        txt = message.messageTextShort;
                    }
                    previewText = txt != null ? txt.toString() : "";
                }
            }
        }

        // Clean newlines
        previewText = previewText.replace('\n', ' ').trim();
        if (previewText.length() > 30) {
            previewText = previewText.substring(0, 30) + "...";
        }
        previewTextView.setText(previewText);

        // Unread dot
        if (dialog.unread_count > 0) {
            unreadTextView.setVisibility(View.VISIBLE);
        } else {
            unreadTextView.setVisibility(View.GONE);
        }
    }

    private String getInitials(String name) {
        if (TextUtils.isEmpty(name)) {
            return "?";
        }
        String cleanName = name.replace("📢", "").trim();
        if (cleanName.isEmpty()) {
            return "?";
        }
        return cleanName.substring(0, 1).toUpperCase();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Draw 1px bottom border/divider
        Paint paint = EinkTheme.getBorderPaint();
        canvas.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1, paint);
    }

    public long getDialogId() {
        return currentDialogId;
    }
}
