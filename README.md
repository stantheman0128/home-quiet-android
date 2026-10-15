# 在家安靜 HomeQuiet

到家時自動清除選定 App 的通知（預設 Grok Bot）。v0.3.0 起介面改為 Jetpack Compose 黑底極簡設計（狀態／應用／紀錄三分頁＋設定引導）。

- 套件名：`tw.stan.homequiet`，release 以 `release.jks` 簽章（與 v0.1/v0.2 同一把金鑰，可直接覆蓋安裝）
- 在家判斷：Google Play 服務地理圍欄；同時以前景服務透過 LocationManager（GPS／網路／被動）持續定位作為備援；可選「住家 Wi-Fi 也算在家」
- 通知清除：NotificationListenerService

Build: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
