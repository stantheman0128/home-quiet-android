# 在家安靜 HomeQuiet（Android POC）

連上「家裡的 Wi-Fi」時，自動收起指定 App（預設 Grok Bot）的通知；出門就照常跳。

## 安裝
1. 手機瀏覽器打開 [最新版 Release](https://github.com/stantheman0128/home-quiet-android/releases/latest)，下載 `HomeQuiet-v0.1.0.apk`。
2. 打開檔案，依提示允許瀏覽器「安裝不明來源的應用程式」，按安裝（Play 保護若跳警告，選「仍要安裝」）。

## 設定
1. 開啟 App，按「開啟通知存取」→ 打開「在家安靜」。（Android 13+ 若顯示受限設定：到 設定 → 應用程式 → 在家安靜 → 右上角 ⋮ →「允許受限設定」，再回來開。）
2. 按「允許位置」→ 選「使用應用程式時允許」並保留「精確位置」。
3. 按「位置改成一律允許」→ 權限 → 位置 →「一律允許」（背景讀 Wi-Fi 名稱需要）。
4. 在家連著 Wi-Fi 時，按「把目前的 Wi-Fi 設為家」。
5. 確認「要靜音的 App」裡有 Grok Bot，沒有就按「選擇 App」勾選。
手機的「定位」總開關需開著。

## 已知限制
- 通知可能先閃一下橫幅再被收起（App 是在通知出現後才取消它）。
- 被收起的通知不會保留在通知欄，可在 App 內「最近收起的通知」看標題。
- 只認 Wi-Fi 名稱（SSID），同名的其他 Wi-Fi 也會被當成家。
- 自簽名 APK，僅供側載。

## 建置
JDK 17 + Android SDK 35，`./gradlew assembleRelease`（需自備 `release.jks`）。
