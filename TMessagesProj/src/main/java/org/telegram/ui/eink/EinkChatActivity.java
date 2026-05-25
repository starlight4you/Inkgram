package org.telegram.ui.eink;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import java.util.ArrayList;
import java.util.Collections;

public class EinkChatActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private long chatId;
    private EinkMessagePageView pageView;
    private TextView prevButton;
    private TextView nextButton;
    private TextView pageIndicator;
    private EinkChatInputView inputView;

    private ArrayList<MessageObject> messageHistory = new ArrayList<>();
    private ArrayList<EinkMessagePageView.Page> pages = new ArrayList<>();
    private int currentPageIndex = 0;

    private FrameLayout bannerContainer;
    private ArrayList<MessageObject> pendingNewMessages = new ArrayList<>();
    private int junctionMsgId = 0;

    public EinkChatActivity(Bundle args) {
        super(args);
        chatId = args.getLong("chat_id");
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        getNotificationCenter().addObserver(this, NotificationCenter.messagesDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.dialogsNeedReload);

        // Load 100 messages initially
        getMessagesController().loadMessages(chatId, 0, false, 100, 0, 0, true, 0, classGuid, 0, 0, 0, 0, 0, 0, false);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.dialogsNeedReload);
    }

    @Override
    public View createView(Context context) {
        // Setup B/W Action Bar
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setBackgroundColor(EinkTheme.COLOR_WHITE);
        actionBar.setTitleColor(EinkTheme.COLOR_BLACK);
        actionBar.getTitleTextView().setTypeface(EinkTheme.SERIF_BOLD);
        actionBar.setCastShadows(false);

        String titleText = "";
        if (chatId > 0) {
            TLRPC.User user = getMessagesController().getUser(chatId);
            if (user != null) {
                titleText = ContactsController.formatName(user.first_name, user.last_name);
            }
        } else if (chatId < 0) {
            TLRPC.Chat chat = getMessagesController().getChat(-chatId);
            if (chat != null) {
                titleText = chat.title;
                if (ChatObject.isChannel(chat) && !chat.megagroup) {
                    titleText = "📢 " + titleText;
                }
            }
        }
        actionBar.setTitle(titleText);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        // Chat Container
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(EinkTheme.COLOR_WHITE);
        fragmentView = container;

        // New Messages Banner View
        bannerContainer = new FrameLayout(context);
        bannerContainer.setVisibility(View.GONE);
        bannerContainer.setBackgroundColor(EinkTheme.COLOR_WHITE);
        bannerContainer.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4));

        TextView bannerTextView = new TextView(context);
        bannerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        bannerTextView.setTypeface(EinkTheme.SERIF_BOLD);
        bannerTextView.setTextColor(EinkTheme.COLOR_BLACK);
        bannerTextView.setGravity(Gravity.CENTER);
        bannerTextView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));

        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setShape(GradientDrawable.RECTANGLE);
        bannerBg.setStroke(1, EinkTheme.COLOR_BLACK);
        bannerBg.setColor(EinkTheme.COLOR_WHITE);
        bannerBg.setCornerRadius(0);
        bannerTextView.setBackground(bannerBg);

        bannerTextView.setOnClickListener(v -> {
            mergePendingMessages();
            // Focus on the new last page
            currentPageIndex = Math.max(0, pages.size() - 1);
            updatePage();
        });

        bannerContainer.addView(bannerTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        container.addView(bannerContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Custom Paginated Message Page View
        pageView = new EinkMessagePageView(context);
        pageView.setOnPhotoClickListener(this::showImagePopup);

        container.addView(pageView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1.0f));

        // Pagination Controller Bar (上一页 / Indicator / 下一页)
        LinearLayout paginationBar = new LinearLayout(context);
        paginationBar.setOrientation(LinearLayout.HORIZONTAL);
        paginationBar.setGravity(Gravity.CENTER_VERTICAL);
        paginationBar.setBackgroundColor(EinkTheme.COLOR_WHITE);
        paginationBar.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4));

        prevButton = new TextView(context);
        prevButton.setText(LocaleController.getString("InkgramPrevPage", R.string.InkgramPrevPage));
        prevButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        prevButton.setTypeface(EinkTheme.SERIF_BOLD);
        prevButton.setTextColor(EinkTheme.COLOR_BLACK);
        prevButton.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));

        GradientDrawable prevBg = new GradientDrawable();
        prevBg.setShape(GradientDrawable.RECTANGLE);
        prevBg.setStroke(1, EinkTheme.COLOR_BLACK);
        prevBg.setColor(EinkTheme.COLOR_WHITE);
        prevBg.setCornerRadius(0);
        prevButton.setBackground(prevBg);
        prevButton.setOnClickListener(v -> flipPageUp());

        paginationBar.addView(prevButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        pageIndicator = new TextView(context);
        pageIndicator.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        pageIndicator.setTypeface(EinkTheme.SERIF_REGULAR);
        pageIndicator.setTextColor(EinkTheme.COLOR_BLACK);
        pageIndicator.setGravity(Gravity.CENTER);

        paginationBar.addView(pageIndicator, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        nextButton = new TextView(context);
        nextButton.setText(LocaleController.getString("InkgramNextPage", R.string.InkgramNextPage));
        nextButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        nextButton.setTypeface(EinkTheme.SERIF_BOLD);
        nextButton.setTextColor(EinkTheme.COLOR_BLACK);
        nextButton.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));

        GradientDrawable nextBg = new GradientDrawable();
        nextBg.setShape(GradientDrawable.RECTANGLE);
        nextBg.setStroke(1, EinkTheme.COLOR_BLACK);
        nextBg.setColor(EinkTheme.COLOR_WHITE);
        nextBg.setCornerRadius(0);
        nextButton.setBackground(nextBg);
        nextButton.setOnClickListener(v -> flipPageDown());

        paginationBar.addView(nextButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        container.addView(paginationBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40));

        // Bottom Input View
        inputView = new EinkChatInputView(context);
        inputView.setOnSendClickListener(this::sendMessage);
        container.addView(inputView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Wait for pageView to layout so we know height for pagination
        pageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                pageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                paginateMessages();
            }
        });

        return fragmentView;
    }

    private void paginateMessages() {
        int width = pageView.getWidth();
        int height = pageView.getHeight();
        if (width <= 0 || height <= 0 || messageHistory.isEmpty()) {
            return;
        }

        // Store the top message on the current page to preserve reading position
        MessageObject topMsg = null;
        if (!pages.isEmpty() && currentPageIndex >= 0 && currentPageIndex < pages.size()) {
            EinkMessagePageView.Page curr = pages.get(currentPageIndex);
            if (!curr.messages.isEmpty()) {
                topMsg = curr.messages.get(0);
            }
        }

        pages.clear();
        EinkMessagePageView.Page currentPage = new EinkMessagePageView.Page();
        int currentHeight = 0;
        int maxHeight = height - AndroidUtilities.dp(24); // top/bottom cushion

        long lastSenderId = -1;
        for (int i = 0; i < messageHistory.size(); i++) {
            MessageObject msg = messageHistory.get(i);
            boolean showSender = (msg.getSenderId() != lastSenderId);
            int msgHeight = EinkMessagePageView.measureMessage(msg, showSender, width, currentAccount);

            if (currentHeight + msgHeight > maxHeight && !currentPage.messages.isEmpty()) {
                pages.add(currentPage);
                currentPage = new EinkMessagePageView.Page();
                currentHeight = 0;
                lastSenderId = -1;
                showSender = true;
                msgHeight = EinkMessagePageView.measureMessage(msg, showSender, width, currentAccount);
            }

            currentPage.messages.add(msg);
            currentPage.showSenders.add(showSender);
            currentHeight += msgHeight;
            lastSenderId = msg.getSenderId();
        }
        if (!currentPage.messages.isEmpty()) {
            pages.add(currentPage);
        }

        // Restore reading position or handle earlier load junction
        int newPageIndex = -1;
        if (junctionMsgId > 0) {
            for (int p = 0; p < pages.size(); p++) {
                EinkMessagePageView.Page page = pages.get(p);
                for (MessageObject msg : page.messages) {
                    if (msg.getId() == junctionMsgId) {
                        newPageIndex = p;
                        break;
                    }
                }
                if (newPageIndex != -1) {
                    break;
                }
            }
            junctionMsgId = 0; // Reset after restoration
        } else if (topMsg != null) {
            for (int p = 0; p < pages.size(); p++) {
                EinkMessagePageView.Page page = pages.get(p);
                for (MessageObject msg : page.messages) {
                    if (msg.getId() == topMsg.getId()) {
                        newPageIndex = p;
                        break;
                    }
                }
                if (newPageIndex != -1) {
                    break;
                }
            }
        }

        if (newPageIndex != -1) {
            currentPageIndex = newPageIndex;
        } else {
            // Default to the last page (newest messages)
            currentPageIndex = Math.max(0, pages.size() - 1);
        }
        updatePage();
    }

    private void updatePage() {
        if (pageView == null) {
            return;
        }

        if (!pages.isEmpty() && currentPageIndex >= 0 && currentPageIndex < pages.size()) {
            pageView.setPage(pages.get(currentPageIndex));
            String text = LocaleController.formatString("InkgramPageOf", R.string.InkgramPageOf, currentPageIndex + 1, pages.size());
            pageIndicator.setText(text);
        } else {
            pageView.setPage(null);
            pageIndicator.setText("第 0 页 / 共 0 页");
        }

        if (pages.isEmpty()) {
            prevButton.setVisibility(View.INVISIBLE);
        } else if (currentPageIndex > 0) {
            prevButton.setVisibility(View.VISIBLE);
            prevButton.setText(LocaleController.getString("InkgramPrevPage", R.string.InkgramPrevPage));
        } else {
            // First page displays "加载更早消息"
            prevButton.setVisibility(View.VISIBLE);
            prevButton.setText("加载更早消息");
        }

        nextButton.setVisibility(currentPageIndex < pages.size() - 1 ? View.VISIBLE : View.INVISIBLE);
    }

    public void flipPageUp() {
        if (currentPageIndex > 0) {
            currentPageIndex--;
            updatePage();
        } else {
            loadEarlierMessages();
        }
    }

    public void flipPageDown() {
        if (currentPageIndex < pages.size() - 1) {
            currentPageIndex++;
            updatePage();
            if (currentPageIndex == pages.size() - 1) {
                mergePendingMessages();
            }
        }
    }

    private void loadEarlierMessages() {
        if (!messageHistory.isEmpty()) {
            junctionMsgId = messageHistory.get(0).getId();
            getMessagesController().loadMessages(chatId, 0, false, 50, junctionMsgId, 0, false, 0, classGuid, 0, 0, 0, 0, 0, 0, false);
        }
    }

    private void mergePendingMessages() {
        if (!pendingNewMessages.isEmpty()) {
            for (MessageObject msg : pendingNewMessages) {
                boolean duplicate = false;
                for (MessageObject hMsg : messageHistory) {
                    if (hMsg.getId() == msg.getId()) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    messageHistory.add(msg);
                }
            }
            pendingNewMessages.clear();
            Collections.sort(messageHistory, (m1, m2) -> Integer.compare(m1.getId(), m2.getId()));
            paginateMessages();
            hideNewMessagesBanner();
        }
    }

    private void showNewMessagesBanner(int count) {
        if (bannerContainer != null) {
            TextView textView = (TextView) bannerContainer.getChildAt(0);
            if (textView != null) {
                textView.setText("有 " + count + " 条新消息");
            }
            bannerContainer.setVisibility(View.VISIBLE);
        }
    }

    private void hideNewMessagesBanner() {
        if (bannerContainer != null) {
            bannerContainer.setVisibility(View.GONE);
        }
    }

    private void sendMessage(String text) {
        SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(text, chatId);
        SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
        // Force refresh chat history
        getMessagesController().loadMessages(chatId, 0, false, 100, 0, 0, false, 0, classGuid, 0, 0, 0, 0, 0, 0, false);
    }

    private void showImagePopup(MessageObject message) {
        if (message == null || getParentActivity() == null) {
            return;
        }

        Dialog imageDialog = new Dialog(getParentActivity());
        imageDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setStroke(1, EinkTheme.COLOR_BLACK);
        bg.setColor(EinkTheme.COLOR_WHITE);
        bg.setCornerRadius(0);
        layout.setBackground(bg);

        // Actual loaded image view (using standard BackupImageView)
        BackupImageView imageView = new BackupImageView(getParentActivity());
        imageView.setAspectFit(true);

        TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, 800);
        if (size != null) {
            imageView.setImage(ImageLocation.getForObject(size, message.messageOwner.media.photo), "800_800", (android.graphics.drawable.Drawable) null, null);
        }

        layout.addView(imageView, LayoutHelper.createLinear(260, 260, Gravity.CENTER, 0, 0, 0, 16));

        // Bold "关闭" button underneath
        TextView closeBtn = new TextView(getParentActivity());
        closeBtn.setText(LocaleController.getString("InkgramClose", R.string.InkgramClose));
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        closeBtn.setTypeface(EinkTheme.SERIF_BOLD);
        closeBtn.setTextColor(EinkTheme.COLOR_BLACK);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(10), AndroidUtilities.dp(24), AndroidUtilities.dp(10));

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.RECTANGLE);
        btnBg.setStroke(1, EinkTheme.COLOR_BLACK);
        btnBg.setColor(EinkTheme.COLOR_WHITE);
        btnBg.setCornerRadius(0);
        closeBtn.setBackground(btnBg);
        closeBtn.setOnClickListener(v -> imageDialog.dismiss());

        layout.addView(closeBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        imageDialog.setContentView(layout);
        if (imageDialog.getWindow() != null) {
            imageDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        imageDialog.show();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagesDidLoad) {
            int guid = (Integer) args[10];
            if (guid == classGuid) {
                ArrayList<MessageObject> messArr = (ArrayList<MessageObject>) args[2];
                if (messArr != null) {
                    ArrayList<MessageObject> filtered = new ArrayList<>();
                    for (MessageObject msg : messArr) {
                        if (msg != null && !msg.deleted) {
                            filtered.add(msg);
                        }
                    }
                    Collections.reverse(filtered);

                    int maxId = (Integer) args[12];
                    boolean isEarlierLoad = (maxId > 0);

                    if (isEarlierLoad) {
                        // Prepend loaded earlier history
                        messageHistory.addAll(0, filtered);
                        paginateMessages();
                    } else {
                        // Checking for new messages vs existing reloads
                        int newestId = messageHistory.isEmpty() ? 0 : messageHistory.get(messageHistory.size() - 1).getId();
                        ArrayList<MessageObject> reallyNew = new ArrayList<>();
                        for (MessageObject msg : filtered) {
                            if (msg.getId() > newestId) {
                                reallyNew.add(msg);
                            }
                        }

                        boolean atLastPage = (pages.isEmpty() || currentPageIndex >= pages.size() - 1);
                        if (atLastPage || messageHistory.isEmpty()) {
                            // Immediately merge newer messages if at the end of list or empty
                            messageHistory.clear();
                            messageHistory.addAll(filtered);
                            paginateMessages();
                            hideNewMessagesBanner();
                        } else {
                            // Buffer new messages if the user is reading history
                            if (!reallyNew.isEmpty()) {
                                for (MessageObject msg : reallyNew) {
                                    boolean duplicate = false;
                                    for (MessageObject pMsg : pendingNewMessages) {
                                        if (pMsg.getId() == msg.getId()) {
                                            duplicate = true;
                                            break;
                                        }
                                    }
                                    if (!duplicate) {
                                        pendingNewMessages.add(msg);
                                    }
                                }
                                showNewMessagesBanner(pendingNewMessages.size());
                            }
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.updateInterfaces || id == NotificationCenter.dialogsNeedReload) {
            // Hot reload messages when updates arrive
            getMessagesController().loadMessages(chatId, 0, false, 100, 0, 0, false, 0, classGuid, 0, 0, 0, 0, 0, 0, false);
        }
    }
}
