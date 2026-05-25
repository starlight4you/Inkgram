package org.telegram.ui.eink;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import java.util.ArrayList;

public class EinkDialogsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private RecyclerListView listView;
    private EinkDialogsAdapter adapter;
    private FrameLayout searchPlaceholder;

    private static final int menu_settings = 1;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        getNotificationCenter().addObserver(this, NotificationCenter.dialogsNeedReload);
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        
        // Ensure dialogs are loaded from the database or server on E-ink launch
        org.telegram.ui.DialogsActivity.loadDialogs(getAccountInstance());
        getMessagesController().loadPinnedDialogs(0, 0, null);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.dialogsNeedReload);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
    }

    @Override
    public View createView(Context context) {
        // Setup pure B/W action bar
        actionBar.setBackButtonImage(0); // No back button for root screen
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Inkgram");
        actionBar.setBackgroundColor(EinkTheme.COLOR_WHITE);
        actionBar.setTitleColor(EinkTheme.COLOR_BLACK);
        actionBar.getTitleTextView().setTypeface(EinkTheme.SERIF_BOLD);
        actionBar.setCastShadows(false); // No shadows

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == menu_settings) {
                    presentFragment(new EinkSettingsActivity());
                }
            }
        });

        // Add Gear settings icon
        ActionBarMenu menu = actionBar.createMenu();
        View settingsBtn = menu.addItem(menu_settings, R.drawable.msg_settings);
        if (settingsBtn instanceof TextView) {
            ((TextView) settingsBtn).setTextColor(EinkTheme.COLOR_BLACK);
        } else if (settingsBtn != null) {
            // Apply pure black filter to the icon
            settingsBtn.setBackground(null);
        }

        // Parent container layout
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setBackgroundColor(EinkTheme.COLOR_WHITE);
        fragmentView = linearLayout;

        // Search placeholder (1px black border, non-functional)
        searchPlaceholder = new FrameLayout(context);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setShape(GradientDrawable.RECTANGLE);
        searchBg.setStroke(1, EinkTheme.COLOR_BLACK);
        searchBg.setColor(EinkTheme.COLOR_WHITE);
        searchBg.setCornerRadius(0);
        searchPlaceholder.setBackground(searchBg);

        TextView searchTextView = new TextView(context);
        searchTextView.setText(LocaleController.getString("InkgramSearchPlaceholder", R.string.InkgramSearchPlaceholder));
        searchTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        searchTextView.setTypeface(EinkTheme.SERIF_REGULAR);
        searchTextView.setTextColor(EinkTheme.COLOR_BLACK);
        searchTextView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        searchTextView.setPadding(AndroidUtilities.dp(10), 0, 10, 0);
        searchPlaceholder.addView(searchTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        linearLayout.addView(searchPlaceholder, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 12, 12, 12, 12));

        // Conversations RecyclerView
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setBackgroundColor(EinkTheme.COLOR_WHITE);
        listView.setVerticalScrollBarEnabled(false);
        listView.setGlowColor(EinkTheme.COLOR_WHITE);

        adapter = new EinkDialogsAdapter(context);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((view, position) -> {
            TLRPC.Dialog dialog = adapter.getItem(position);
            if (dialog != null) {
                Bundle args = new Bundle();
                args.putLong("chat_id", dialog.id);
                presentFragment(new EinkChatActivity(args));
            }
        });

        linearLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        loadDialogs();

        return fragmentView;
    }

    private void loadDialogs() {
        if (adapter != null) {
            ArrayList<TLRPC.Dialog> dialogList = getMessagesController().getDialogs(0);
            if (dialogList == null || dialogList.isEmpty()) {
                getMessagesController().loadDialogs(0, 0, 100, true);
                dialogList = getMessagesController().getDialogs(0);
            }
            adapter.setDialogs(dialogList);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.dialogsNeedReload || id == NotificationCenter.updateInterfaces) {
            loadDialogs();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadDialogs();
    }
}
