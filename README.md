# Inkgram - 墨水屏专用的 Telegram 客户端 / Telegram Client Designed for E-ink Devices

[English Version Below](#english-version)

**Inkgram** 是专为国产安卓墨水屏（E-ink）阅读器（如文石 Boox 等）深度定制开发的 Telegram 客户端。

针对墨水屏刷新率低、易留残影、灰度色阶差等硬件局限性，Inkgram 重新设计并实现了一套完全独立的墨水屏阅读与操作界面，以实现极致纯净、无残留、如同看电子书般的流畅社交体验。

---

## 核心设计理念

为了在墨水屏上达到最佳的视觉与交互体验，Inkgram 严格遵循以下视觉法则：
* **纯黑白视觉**：全屏仅采用纯黑（#000）与纯白（#FFF）两色。坚决摒弃灰色、阴影、渐变以及半透明效果，从根本上解决墨水屏在渲染灰度时的留影难题。
* **高对比度边缘**：所有可交互元素与输入框均配有清晰的物理 1px 实线黑边或下划线，提供极致清晰的触觉界限。
* **小说排版级阅读**：取消了传统的对话气泡，转而采用类似小说的段落书籍排版。使用优雅的衬线字体，配以宽敞的 1.6 倍行距，让阅读长文字信息变得像看电子书一样惬意。
* **固定高度分页翻页**：摒弃墨水屏极其忌讳的上下滚动条，通过运行时动态测算视窗高度进行精密“分页”。用户可以通过底部的“上一页/下一页”按钮或**手机音量键**进行整屏翻页，大幅度减少屏幕刷新次数，保护视力。

---

## 我们已经做了什么 (已实现功能)

在目前的 **0.5 版本**中，我们已经实现了以下功能：

### 1. 独立界面与一键切换
* 在设置中提供了“经典模式”与“E-Ink 模式”切换开关。切换后客户端会自动重构重启，加载完全独立的 E-ink 极简界面。

### 2. 墨水屏定制聊天列表
* 纯白底色的极简对话列表。
* 摒弃彩色圆形头像，使用加粗拼音/英文字母首字母方框作为头像占位符。
* 频道等对话前面配有直观的 `📢` 文字标记。
* 高对比度的纯黑圆形未读消息指示器（●）。
* 预留非功能性搜索框位置以保持布局完整。

### 3. 小说书籍级聊天阅读页
* **极致翻页阅读**：支持屏幕底部按钮点击翻页，并完美支持**音量键控制上下翻页**。
* **智能阅读位置记忆**：当聊天有新消息或界面轮询刷新时，系统会精准锁定您当前页面的首条消息。即便发生重新分页，也会智能锁定并重置到对应页码，确保您的阅读视线绝对不会被打断或发生莫名的界面跳跃。
* **无缝往前加载更早消息**：当您翻阅到第一页时，翻页按钮会自动变换为“**加载更早消息**”。点击后会无缝增量拉取更早历史，并利用 **Junction Page Tracking** 技术精准将您的视线停留在新旧消息拼接的地方，实现阅读进度的完美连续。
* **非打扰式新消息横幅**：如果您正专注于看历史消息，新消息进来时不会粗暴地重绘您的页面。顶部会浮现高对比度横幅提示“有 N 条新消息”。您可随时点击提示条一键跳转合并，或者在手动翻页到最后一页时由系统悄无声息地自动合并。
* **精简图片与贴纸预览**：聊天中的图片渲染为精致的 `[图片]` 边框占位符，点击后可弹出大图弹窗，并配有极易触摸的“关闭”按钮。贴纸则直观渲染为其关联的 Emoji，如 `[贴纸 😭]`。

---

## 下载安装 (v0.5)

您可以直接下载本项目发布的预编译 APK 进行安装体验：
* 👉 **[下载 Inkgram_v0.5.apk](Inkgram_v0.5.apk)**

---

<a name="english-version"></a>

# Inkgram (English)

**Inkgram** is a tailored Android Telegram client fork optimized specifically for E-ink reader devices (such as Onyx Boox) with low refresh rates, monochrome screens, and ghosting issues.

By completely splitting the UI layers from the "Classic" Telegram client, Inkgram introduces a pristine, book-like social experience that minimizes screen refreshes and eliminates ghosting artifacts.

## Core Design Principles
* **Pure Black & White Theme**: Only pure black (#000) and pure white (#FFF) are used. No gray fills, shadows, gradients, or transparency—guaranteeing zero ghosting artifacts.
* **Crisp Tactile Elements**: All clickable items, buttons, and inputs are framed with physical 1px solid outlines or underlines for high-contrast visibility.
* **Novel-style Typesetting**: Replaces message bubbles with spacious paragraph typesetting. Utilizing serif typefaces and comfortable 1.6× line-spacing for effortless, long-form reading.
* **Fixed-height Pagination**: Ditches continuous scrolling in favor of static pages calculated dynamically at runtime. Flip pages instantly via bottom buttons or the **physical volume keys** to minimize screen flashing.

## What We Have Done (Features Implemented)

### 1. One-click Mode Switcher
* Simple UI switcher inside Settings to toggle between "Classic Mode" and "E-ink Mode" (restart to apply).

### 2. Tailored Conversation List
* Minimalist, white-background chat list.
* Initials-box monochrome avatars replacing heavy colored circular images.
* Prepend channel items with descriptive `📢` text labels.
* High-contrast unread indicators (●) and non-functional search placeholder.

### 3. Immersive Chat & Reading View
* **Elegant Page-flipping**: Smooth button-triggered or **volume key-triggered** E-ink pagination.
* **Reading Progress Memory**: Automatically tracks the top message of the current page. If history reloads or repaginates, it restores your exact viewport to prevent unexpected scrolling or visual disorientation.
* **Continuous Load Earlier History**: The bottom left button changes to "**Load Earlier Messages**" on the first page. It increments older history and uses **Junction Page Tracking** to keep your reading position completely steady at the boundary of old and new messages.
* **Non-intrusive New Message Banner**: When reading history, new messages are buffered in the background and a top banner displays "N new messages". Tap to merge and jump, or simply flip pages to the end to let them merge seamlessly.
* **Inline Media Placeholders**: Clean placeholders like `[图片]` (Image) and `[Sticker 😭]`. Clicking the image placeholder pops up the full image in a high-contrast dialog with a prominent "Close" button.

---

## Download (v0.5)

Download the pre-compiled APK directly from our repository:
* 👉 **[Download Inkgram_v0.5.apk](Inkgram_v0.5.apk)**
