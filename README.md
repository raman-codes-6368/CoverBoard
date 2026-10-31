<div align="center">

# ⌨️ CoverBoard

### A modern, private, open-source keyboard for Android

Beautiful by design. Powerful by default. **Zero internet permission.**

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Material You](https://img.shields.io/badge/Material%20You-3-FF3F68?style=for-the-badge)
![Privacy](https://img.shields.io/badge/no%20internet-100%25%20offline-success?style=for-the-badge)

</div>

---

## ✨ Why CoverBoard?

Most keyboards send what you type somewhere else. **CoverBoard never can.** The app does not declare the `INTERNET` permission, so Android itself blocks every network connection. Your words stay on your phone, and the in-app Privacy screen verifies this at runtime.

On top of that, it is fast, themeable, and built entirely with Jetpack Compose.

---

## 🚀 Features

### ⌨️ Typing
- **Swipe typing** with an optional gesture trail and adjustable width
- **Smart autocorrect** with adjustable strength
- **Word and next-word predictions** plus **emoji predictions**
- Auto-capitalisation, double-space period, smart punctuation and auto-spacing
- **Space-bar cursor control** and swipe-to-delete
- Hold-to-delete by words
- Text **shortcuts** (snippet expansion)
- Custom **dictionaries**: import your own word lists

### 🌍 Languages
- Many languages and regional variants, including **English (US, UK, India, Australia, Canada, Ireland, NZ, South Africa)**
- **Multilingual typing**: suggestions and autocorrect across every language you add
- **Long-press the space bar** to switch language instantly
- Layouts for QWERTY, AZERTY, QWERTZ, Cyrillic, Devanagari, Arabic, Hebrew, Greek and more

### 🎨 Design
- Cinematic dark **themes** that follow the system's light or dark mode
- Fully **custom themes**: background, keys, accent and text colours
- Adjustable key height, spacing, corner radius and borders
- **One-handed** and **split** keyboard modes
- Optional number row, arrow keys, emoji key and globe key

### 🎙️ Voice typing
- Built-in dictation, shown **inside the keyboard** (no pop-up screen)
- Continuous listening that types straight into the field

### 📋 Clipboard
- **Clipboard history** with suggestions in the toolbar
- Skips sensitive copies (like passwords) automatically
- Auto-expiry and a configurable history size

### 📳 Haptics and sound
- Multiple haptic profiles, with adjustable strength and duration
- Separate feedback for gestures, long-presses and cursor moves
- Optional key sounds with volume control

### 🛠️ Customisable toolbar
- Reorder or hide tools: clipboard, emoji, editing, theme, voice, incognito, one-handed, layout and settings

### 🔒 Privacy
- **No internet permission**
- **Incognito mode** that pauses learning and clipboard capture
- Optional **encryption** of learned data
- Optional **secure app screen**, which blocks screenshots in settings
- No accounts, no analytics, no ads

---

## 📲 Installation

### Build from source

```bash
git clone https://github.com/raman-codes-6368/CoverBoard.git
cd CoverBoard
./gradlew assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/`.

Or open the project in **Android Studio** and press **Run**.

### Enable the keyboard

1. Open the **CoverBoard** app.
2. Tap **Enable keyboard** and switch **CoverBoard** on.
3. Choose **CoverBoard** as your active keyboard.

---

## 🧱 Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material You 3 |
| Input | `InputMethodService` |
| Voice | Android `SpeechRecognizer` |
| Storage | Local, optionally encrypted |

---

## 📁 Project structure

```
app/src/main/java/com/example/coverboard/
├── CoverBoardApp.kt        # Shared services (Graph)
├── MainActivity.kt         # Settings app
├── ime/                    # Keyboard service, input controller, voice
├── engine/                 # Prediction and autocorrect engine
├── data/                   # Settings, user data, clipboard history
└── ui/                     # Keyboard UI, settings screens, theme
```

---

## 🤝 Contributing

Contributions are welcome.

1. Fork the repository
2. Create a branch: `git checkout -b feature/amazing-idea`
3. Commit your changes: `git commit -m "Add amazing idea"`
4. Push the branch: `git push origin feature/amazing-idea`
5. Open a Pull Request

Bug reports and feature requests belong in the **Issues** tab.

---

## 🗺️ Roadmap

- [ ] More languages and bundled dictionaries
- [ ] Theme import and export
- [ ] Backup and restore of settings
- [ ] More layouts

---

## 📄 License

Distributed under the **MIT License**. See `LICENSE` for details.

---

<div align="center">

### Made with ❤️ by [Raman Singh](https://github.com/raman-codes-6368)

If you like CoverBoard, give it a ⭐ on GitHub.

</div>
