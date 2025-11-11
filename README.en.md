<div align="center">

<img src="logo.png" width="300px" alt="KIZ VPN Logo"/>

# KIZ VPN

**Modern Android VPN client with VLESS and WireGuard support**

[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://android.com)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple.svg)](https://kotlinlang.org)
[![Version](https://img.shields.io/badge/Version-2.2.1-brightgreen.svg)](https://github.com/eXLu51ve-gjj/kiz-vpn-client/releases/latest)

📖 **Languages:** [🇷🇺 Русский](README.md) | [🇬🇧 English](README.en.md)

</div>

---

## ✨ Features

### Core Functionality
- 🔒 **VLESS Protocol** - Full support with Reality, WebSocket, TLS/XTLS
- 🛡️ **WireGuard Protocol** - Modern, fast, and secure VPN
- ⚡ **Quick Settings Tile** - Toggle VPN from notification shade
- 📊 **Real-time Statistics** - Monitor upload/download speeds
- 🌓 **Dark Theme** - Beautiful modern UI with Jetpack Compose

### Advanced Features
- 📅 **Subscription Management** - Track remaining days/hours
- 📷 **QR Code Scanner** - Import configs easily
- 🔄 **Auto-connect** - Start VPN on app launch
- 📜 **Connection History** - Track all VPN sessions
- 🔗 **Deep Link Support** - Import configs from Telegram bot
- 📱 **Multi-config Support** - Save and switch between multiple configs

---

## 🛠️ Technologies

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose + Material3
- **Architecture:** MVVM
- **VPN Core:** Based on [XiVPN](https://github.com/Exclude0122/XiVPN) (GPL-3.0)
- **Protocols:** VLESS (Xray-core), WireGuard

---

## 📥 Download

### Latest Release

[![Download APK](https://img.shields.io/badge/Download-APK-green.svg)](https://github.com/eXLu51ve-gjj/kiz-vpn-client/releases/latest)

**Current Version:** v2.2.1  
**Size:** ~162 MB  
**Min Android:** 8.0 (API 26)

[📦 Download Latest APK](https://github.com/eXLu51ve-gjj/kiz-vpn-client/releases/latest/download/KIZ_VPN.public.2.2.1.apk)

### Installation

1. Download the APK file
2. Enable "Install from Unknown Sources" in Settings
3. Open the APK file and install
4. Grant VPN permission when prompted

### ⚠️ Important Notes

**This is a demo build:**
- ✅ **VPN works with any VLESS/WireGuard config**
- ✅ All core VPN features are functional
- ❌ Subscription management requires API server configuration
- ❌ Key activation requires API server configuration

**To use:**
1. Paste your VLESS or WireGuard config
2. Connect to VPN - it will work!
3. Subscription info won't show (requires your own server)

**To enable full features:**
- Configure your own 3x-ui panel
- Update server settings in code (see Getting Started)

**Note:** This is an unsigned debug build. You may see a warning from Google Play Protect - this is normal for apps installed outside the Play Store.

---

## 📱 Screenshots

### Main Interface

<div align="center">

<table>
  <tr>
    <td><img src="screenshots/Screenshot_20251111_195621_KIZ VPN.jpg" width="250px"/><br/><b>Home Screen - Disconnected</b><br/>Beautiful animated interface</td>
    <td><img src="screenshots/Screenshot_20251111_200214_KIZ VPN.jpg" width="250px"/><br/><b>Home Screen - Connected</b><br/>Active VPN with animation</td>
    <td><img src="screenshots/Screenshot_20251111_195753_KIZ VPN.jpg" width="250px"/><br/><b>Network Statistics</b><br/>Real-time traffic monitoring</td>
  </tr>
</table>

</div>

### Features

<div align="center">

<table>
  <tr>
    <td><img src="screenshots/Screenshot_20251111_195718_KIZ VPN.jpg" width="250px"/><br/><b>Settings</b><br/>Subscription info: 364 days remaining</td>
    <td><img src="screenshots/Screenshot_20251111_201131_KIZ VPN.jpg" width="250px"/><br/><b>Connection History</b><br/>Track all VPN sessions</td>
  </tr>
</table>

</div>

### Quick Settings Tile

<div align="center">

<table>
  <tr>
    <td><img src="screenshots/Screenshot_20251111_195816_One UI Home.jpg" width="250px"/><br/><b>Quick Settings - Active</b><br/>VPN enabled from notification shade</td>
    <td><img src="screenshots/Screenshot_20251111_195823_One UI Home.jpg" width="250px"/><br/><b>Quick Settings - Available</b><br/>One-tap VPN control</td>
    <td><img src="screenshots/Screenshot_20251111_195804_One UI Home.jpg" width="250px"/><br/><b>VPN Notification</b><br/>Shows subscription: 12 months remaining</td>
  </tr>
</table>

</div>

### 🎬 Video Demo

<div align="center">

[📹 Watch Video Demo](media/Screen_Recording_20251111_195956_KIZ%20VPN.mp4)

*Quick demonstration of VPN connection process*

> **Note:** Click the link above to download and watch the video demo (4 MB)

</div>

---

## 🚀 Getting Started

### Requirements
- Android Studio Arctic Fox or later
- JDK 11 or higher
- Android SDK (minimum API 26)

### Setup

1. **Clone the repository**
```bash
git clone https://github.com/your-username/kiz-vpn-client.git
cd kiz-vpn-client
```

2. **Configure your server settings**

Edit `MainActivity.kt`:
```kotlin
private val apiClient = VpnApiClient(
    baseUrl = "http://YOUR_SERVER_IP:YOUR_API_PORT",
    subscriptionPort = YOUR_SUBSCRIPTION_PORT
)
```

Edit `VpnApiClient.kt`:
```kotlin
class VpnApiClient(
    private val baseUrl: String = "http://YOUR_SERVER_IP:YOUR_API_PORT",
    private val subscriptionPort: Int = YOUR_SUBSCRIPTION_PORT
)
```

Edit `AndroidManifest.xml` (if using deep links):
```xml
<data
    android:scheme="https"
    android:host="your-domain.com"
    android:pathPrefix="/connect" />
```

3. **Build the project**
```bash
./gradlew assembleDebug
```

Or in Android Studio:
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

---

## 📖 Documentation

- [Features List](FEATURES_LIST.md) - Complete list of features
- [Credits](CREDITS.md) - Acknowledgments and licensing information

---

## 🎯 Key Features Explained

### Quick Settings Tile
Toggle VPN directly from your notification shade with a single tap. The tile shows:
- Connection status (active/inactive)
- Subscription information (remaining days)

### Subscription Management
- Automatic subscription checking
- Display remaining time (days/hours)
- Integration with 3x-ui panel
- API-based subscription verification

### Multi-Protocol Support
- **VLESS:** Full Xray-core implementation with Reality, WebSocket, TLS
- **WireGuard:** Native implementation with high performance

---

## 🔧 Configuration

### Importing Configs

**Method 1: Paste from clipboard**
- Copy your config
- Open app → Settings → Paste Config

**Method 2: QR Code**
- Open app → Scan QR Code
- Point camera at QR code

**Method 3: Deep Link**
- Click on kizvpn:// or https:// link
- App opens automatically with config

**Method 4: Telegram Bot**
- Get config from your bot
- Click the link
- App imports automatically

---

## 🏗️ Architecture

```
app/
├── vpn/                  # VPN Service and Tile
│   ├── KizVpnService     # Main VPN service
│   └── KizVpnTileService # Quick Settings Tile
├── ui/                   # UI Components (Jetpack Compose)
│   ├── screens/          # App screens
│   ├── components/       # Reusable components
│   └── viewmodel/        # ViewModels
├── api/                  # API client
├── config/               # Config parser
└── xrayconfig/           # Xray configuration classes
```

---

## 📜 License

This project is licensed under the **GPL-3.0 License** due to the use of XiVPN core code.

See [LICENSE](LICENSE) for more information.

---

## 🙏 Credits

### VPN Core
This project uses VPN core from [XiVPN](https://github.com/Exclude0122/XiVPN):
- **Author:** Exclude0122
- **License:** GPL-3.0

#### What was taken from XiVPN:
- `libxivpn.so` - Native library for VPN routing
- VPN Service architecture
- VLESS and WireGuard protocol support
- IPC communication layer

#### Our additions:
- Modern UI/UX with Jetpack Compose
- Quick Settings Tile
- Subscription management system
- 3x-ui panel integration
- Telegram bot support
- QR code scanner
- Connection history
- Multi-config management

See [CREDITS.md](CREDITS.md) for detailed information.

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

### How to contribute:
1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📞 Contact

- 📧 **Email:** [nml5222600@mail.ru](mailto:nml5222600@mail.ru)
- 💬 **Telegram:** [@eXLu51ve](https://t.me/eXLu51ve)
- 🐙 **GitHub:** [Issues](https://github.com/your-username/kiz-vpn-client/issues)

---

## ⚠️ Disclaimer

This VPN client is provided for educational and legitimate purposes only. Users are responsible for complying with all applicable laws and regulations in their jurisdiction.

---

## 📝 Changelog

### Version 1.0.0 (Current)
- ✅ Initial release
- ✅ VLESS and WireGuard support
- ✅ Quick Settings Tile
- ✅ Subscription management
- ✅ Modern UI with Jetpack Compose
- ✅ QR code scanner
- ✅ Connection history
- ✅ Multi-config support

---

## 🔮 Roadmap

- [ ] OpenVPN protocol support
- [ ] Custom DNS settings
- [ ] Split tunneling
- [ ] Kill switch feature
- [ ] Light theme
- [ ] Widget support
- [ ] Tasker integration

---

<div align="center">

**Made with ❤️ for the privacy-conscious**

⭐ **Star this repo if you find it useful!** ⭐

</div>

