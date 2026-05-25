package org.telegram.ui.eink;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.LayoutHelper;

public class EinkChatInputView extends LinearLayout {

    public interface OnSendClickListener {
        void onSend(String text);
    }

    private EditText messageEditText;
    private TextView sendButton;
    private OnSendClickListener sendClickListener;

    public EinkChatInputView(Context context) {
        super(context);
        init(context);
    }

    public EinkChatInputView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundColor(EinkTheme.COLOR_WHITE);
        setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));

        // Draw 1px top border for input bar separation
        setWillNotDraw(false);

        // Edit Text (1px solid black border, no rounded corners, serif)
        messageEditText = new EditText(context);
        messageEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        messageEditText.setTypeface(EinkTheme.SERIF_REGULAR);
        messageEditText.setTextColor(EinkTheme.COLOR_BLACK);
        messageEditText.setHint("输入消息...");
        messageEditText.setHintTextColor(0xFF888888);
        messageEditText.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(8));
        messageEditText.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        messageEditText.setSingleLine(false);
        messageEditText.setMaxLines(4);

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setShape(GradientDrawable.RECTANGLE);
        inputBg.setStroke(1, EinkTheme.COLOR_BLACK);
        inputBg.setColor(EinkTheme.COLOR_WHITE);
        inputBg.setCornerRadius(0);
        messageEditText.setBackground(inputBg);

        addView(messageEditText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 0, 0, 10, 0));

        // Send Button (1px solid border, bold serif)
        sendButton = new TextView(context);
        sendButton.setText("发送");
        sendButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        sendButton.setTypeface(EinkTheme.SERIF_BOLD);
        sendButton.setTextColor(EinkTheme.COLOR_BLACK);
        sendButton.setGravity(Gravity.CENTER);
        sendButton.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.RECTANGLE);
        btnBg.setStroke(1, EinkTheme.COLOR_BLACK);
        btnBg.setColor(EinkTheme.COLOR_WHITE);
        btnBg.setCornerRadius(0);
        sendButton.setBackground(btnBg);

        sendButton.setOnClickListener(v -> {
            String text = messageEditText.getText().toString().trim();
            if (!text.isEmpty()) {
                if (sendClickListener != null) {
                    sendClickListener.onSend(text);
                }
                messageEditText.setText("");
            }
        });

        addView(sendButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
    }

    public void setOnSendClickListener(OnSendClickListener listener) {
        this.sendClickListener = listener;
    }

    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        super.onDraw(canvas);
        // Draw 1px top divider line
        canvas.drawLine(0, 0, getWidth(), 0, EinkTheme.getBorderPaint());
    }
}
