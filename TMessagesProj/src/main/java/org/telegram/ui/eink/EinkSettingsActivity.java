package org.telegram.ui.eink;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.InkgramConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LogoutActivity;

public class EinkSettingsActivity extends BaseFragment {

    private LinearLayout container;
    private int currentAccount = UserConfig.selectedAccount;

    @Override
    public View createView(Context context) {
        // Setup B/W Action Bar
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("InkgramSettingsTitle", R.string.InkgramSettingsTitle));
        actionBar.setBackgroundColor(EinkTheme.COLOR_WHITE);
        actionBar.setTitleColor(EinkTheme.COLOR_BLACK);
        actionBar.getTitleTextView().setTypeface(EinkTheme.SERIF_BOLD);
        actionBar.setCastShadows(false);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(EinkTheme.COLOR_WHITE);
        fragmentView = scrollView;

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(EinkTheme.COLOR_WHITE);
        scrollView.addView(container, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        rebuildSettings();

        return fragmentView;
    }

    private void rebuildSettings() {
        if (container == null) {
            return;
        }
        container.removeAllViews();
        Context context = getContext();
        if (context == null) {
            return;
        }

        // 1. Account Info Section (Header + Row)
        addSectionHeader("当前账号");
        TLRPC.User currentUser = getUserConfig().getCurrentUser();
        String accountInfoStr = "";
        if (currentUser != null) {
            String phone = currentUser.phone != null ? "+" + currentUser.phone : "";
            String username = currentUser.username != null ? "@" + currentUser.username : "";
            accountInfoStr = phone + (username.isEmpty() ? "" : "  (" + username + ")");
        }
        addDetailRow("账号信息", accountInfoStr);

        // 2. Interface Mode Section
        addSectionHeader("界面样式");

        // Classic Mode Row
        boolean isClassic = InkgramConfig.isClassicMode();
        addSelectionRow("经典模式", "优化过的标准 Telegram 界面", isClassic, v -> {
            if (!InkgramConfig.isClassicMode()) {
                InkgramConfig.setUiMode(InkgramConfig.UI_MODE_CLASSIC);
                showRestartDialog();
            }
        });

        // E-Ink Mode Row
        boolean isEink = InkgramConfig.isEinkMode();
        addSelectionRow("墨水屏模式 (当前)", "全新设计的纯黑白小说排版界面", isEink, v -> {
            // Already in E-ink mode
        });

        // 3. Page Flip Options Section
        addSectionHeader("浏览设置");
        boolean isFlipEnabled = InkgramConfig.isPageFlippingEnabled();
        addSelectionRow("音量键翻页", "在聊天页面使用音量键进行翻页", isFlipEnabled, v -> {
            InkgramConfig.setPageFlippingEnabled(!InkgramConfig.isPageFlippingEnabled());
            rebuildSettings();
        });

        // 4. Logout Section
        addSectionHeader("账户操作");
        addClickRow("退出登录", v -> presentFragment(new LogoutActivity()));
    }

    private void addSectionHeader(String title) {
        TextView header = new TextView(getContext());
        header.setText(title);
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        header.setTypeface(EinkTheme.SERIF_BOLD);
        header.setTextColor(EinkTheme.COLOR_BLACK);
        header.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(6));
        container.addView(header);
    }

    private void addDetailRow(String title, String detail) {
        EinkRowLayout row = new EinkRowLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));

        TextView titleView = new TextView(getContext());
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleView.setTypeface(EinkTheme.SERIF_BOLD);
        titleView.setTextColor(EinkTheme.COLOR_BLACK);
        row.addView(titleView);

        TextView detailView = new TextView(getContext());
        detailView.setText(detail);
        detailView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        detailView.setTypeface(EinkTheme.SERIF_REGULAR);
        detailView.setTextColor(EinkTheme.COLOR_BLACK);
        detailView.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        row.addView(detailView);

        container.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));
    }

    private void addSelectionRow(String title, String subtitle, boolean isChecked, View.OnClickListener onClickListener) {
        EinkRowLayout row = new EinkRowLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        row.setOnClickListener(onClickListener);

        LinearLayout textLayout = new LinearLayout(getContext());
        textLayout.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(getContext());
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleView.setTypeface(EinkTheme.SERIF_BOLD);
        titleView.setTextColor(EinkTheme.COLOR_BLACK);
        textLayout.addView(titleView);

        TextView subView = new TextView(getContext());
        subView.setText(subtitle);
        subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        subView.setTypeface(EinkTheme.SERIF_REGULAR);
        subView.setTextColor(EinkTheme.COLOR_BLACK);
        subView.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        textLayout.addView(subView);

        row.addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        TextView checkIndicator = new TextView(getContext());
        checkIndicator.setText(isChecked ? "[■]" : "[□]");
        checkIndicator.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        checkIndicator.setTypeface(EinkTheme.SERIF_BOLD);
        checkIndicator.setTextColor(EinkTheme.COLOR_BLACK);
        checkIndicator.setPadding(AndroidUtilities.dp(12), 0, 0, 0);
        row.addView(checkIndicator);

        container.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));
    }

    private void addClickRow(String title, View.OnClickListener onClickListener) {
        EinkRowLayout row = new EinkRowLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        row.setOnClickListener(onClickListener);

        TextView titleView = new TextView(getContext());
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleView.setTypeface(EinkTheme.SERIF_BOLD);
        titleView.setTextColor(EinkTheme.COLOR_BLACK);
        row.addView(titleView);

        container.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));
    }

    private void showRestartDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("InkgramRestartRequired", R.string.InkgramRestartRequired));
        builder.setMessage(LocaleController.getString("InkgramRestartMessage", R.string.InkgramRestartMessage));
        builder.setPositiveButton(LocaleController.getString("InkgramRestartNow", R.string.InkgramRestartNow), (dialog, which) -> {
            Intent intent = getParentActivity().getPackageManager().getLaunchIntentForPackage(getParentActivity().getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                getParentActivity().startActivity(intent);
            }
            Runtime.getRuntime().exit(0);
        });
        builder.setNegativeButton(LocaleController.getString("InkgramRestartLater", R.string.InkgramRestartLater), (dialog, which) -> rebuildSettings());
        showDialog(builder.create());
    }

    // Custom Row layout to draw bottom line divider
    private static class EinkRowLayout extends LinearLayout {
        public EinkRowLayout(Context context) {
            super(context);
            setWillNotDraw(false);
            setBackgroundColor(EinkTheme.COLOR_WHITE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            // Draw 1px bottom border
            Paint paint = EinkTheme.getBorderPaint();
            canvas.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1, paint);
        }
    }
}
