# Analisis App Manager (`com.wssc.appanalyzer`) sebagai Referensi Modul FTxT

**Status:** KAJIAN — bahan riset untuk rencana modul App Manager, bukan rencana implementasi aktif.
**Tanggal:** 2026/11/19
**Metode:** Dekompilasi statis `base.apk` dengan apktool 2.11.1 (manifest, resource, smali) dan jadx 1.5.2 (source Java), lalu pencocokan dengan rencana dan struktur FTxT.
**Sumber:** `_sample/App Manager/base.apk` (17,1 MB); hasil dekompilasi di `_sample/App Manager/base_apktool/` dan `_sample/App Manager/base_jadx/`.
**Rencana terkait:** `_schedule/AKTIF/APP_MANAGER.md`, `_schedule/RENCANA/APP_RUNNER.md`.

> **Batasan sumber:** `base.apk` adalah split dasar (`requiredSplitTypes="base__abi,base__density"`), jadi split ABI/density tidak ikut dianalisis. Kode sangat ter-obfuscasi (banyak paket bernama `a`, `b`, `c`, …) dan jadx gagal pada 39 class. Kesimpulan di bawah bersumber dari manifest, navigation graph, string resource, smali, dan class Java yang berhasil dibaca — bukan dari nama class yang direkayasa. Angka nominal yang tersembunyi oleh obfuskasi sengaja tidak dipakai.

---

## Ringkasan Eksekutif

- Aplikasi acuan ini adalah **analyzer + optimizer aplikasi**, bukan toolbox root. Fokus utamanya: katalog aplikasi, inspeksi mendalam (manifest, komponen, izin, sertifikat, grafis), analisis pemakaian, dan aksi manajemen lewat API public + AccessibilityService.
- Sumber daftar aplikasi adalah `PackageManager.getInstalledPackages(0)`, diperkaya `LauncherApps` dan receiver `PACKAGE_ADDED/REMOVED/REPLACED`. Pemakaian (`UsageStatsManager`) dipakai sebagai **metrik**, bukan sebagai sumber daftar.
- Ukuran aplikasi memakai `StorageStatsManager.queryStatsForPackage()` dengan fallback refleksi hidden API. FTxT sebaiknya mengambil pola pertama saja dan membuang fallback reflektifnya.
- Force stop dan clear cache **tidak** memakai API sistem yang terlarang; keduanya diotomatisasi lewat `AccessibilityService` (klik tombol di halaman Settings). Clear data hanya membuka Settings. Tidak ada root, Shizuku, `DevicePolicyManager`, atau `Runtime.exec`.
- Tiga hal paling bernilai untuk FTxT: filter/sort multi-kriteria + highlight pencarian, multi-select dengan konfirmasi, dan halaman detail berbasis tab/kartu. Iklan, telemetri, reward-unlock, dan otomasi aksesibilitas harus ditinggalkan.
- Rencana `APP_MANAGER.md` sudah direvisi mengikuti kajian ini: `getPackageSizeInfo()` dibuang, force stop tidak mengandalkan permission biasa, dan clear cache manual untuk aplikasi lain tidak dipakai.

---

## 1. Identitas & Artefak

| Item | Nilai | Bukti |
|------|-------|-------|
| Package | `com.wssc.appanalyzer` | `_sample/App Manager/base_apktool/AndroidManifest.xml:2` |
| Label | " App Manager" / "App Analyzer" | `base_apktool/res/values/strings.xml:99,102` |
| Version | versionCode `1303`, versionName `1.3.0.3` | `base_apktool/apktool.yml:15-16` |
| SDK | minSdk 24, targetSdk 35, compileSdk 35 | `apktool.yml:9-10`, `AndroidManifest.xml:2` |
| Dex | 3 dex, ~14,8 MB | `base.apk` |
| Konfigurasi | `largeHeap=true`, `allowBackup=true`, `extractNativeLibs=false`, `supportsRtl=true` | `AndroidManifest.xml:57` |
| Lokalisasi | 16 locale (cs, de, el, en, es, fi, fr, in, it, ja, ko, pt, ru, th, tr, zh + varian) | `base_apktool/res/xml/splits0.xml` |

Tidak ada `.so`, database, atau `shared_prefs` yang ikut ter-pack di split dasar. Referensi `System.loadLibrary("mmkv")` ada di `smali_classes2/com/tencent/mmkv/MMKV.smali:789`; kemungkinan library native berada di split ABI yang tidak tersedia di sini.

---

## 2. Arsitektur & Komponen

Pola utama: **single Activity + Jetpack Navigation + bottom navigation 4 tab**.

- `SplashActivity` → launcher, `MainActivity` → `singleTask` + 6 deep link `app://wssc.appanalyzer/{history,running,permission,app_usage,net_usage,app_detail}` (`AndroidManifest.xml:58-126`).
- Navigasi: `main_bottom_navigation_menu.xml` berisi **Overview | Apps | Tags | Settings/Tools**; `nav_main.xml` meng-include `nav_overview`, `nav_apps`, `nav_tags`, `nav_tools` dengan `startDestination=@id/nav_apps`. Tidak ada `DrawerLayout`; `activity_main.xml` hanya `FragmentContainerView` + `BottomNavigationView` di dalam `BlurView`.
- Activity aplikasi lain: `AppInstallerActivity` (penerima APK/XAPK/APKS/ZIP, `AndroidManifest.xml:127-198`), `AccessibilityServiceProcessActivity` (pip), `SimpleWebViewActivity`.
- Service: `NotificationService` (foreground, `dataSync`) dan `AppAccessibilityService` (exported, `BIND_ACCESSIBILITY_SERVICE`, meta `@xml/accessibility_service`, `AndroidManifest.xml:199-206`). Service lain milik Firebase/Ads/WorkManager/Room.
- Provider: `androidx.startup.InitializationProvider` dengan 11 initializer (`AppInitializer`, `ThemeInitializer`, `MMKVInitializer`, `AdInitializer`, `FirebaseInitializer`, …) di `AndroidManifest.xml:65-77`; `UtilsFileProvider` untuk URI install.
- Database lokal: `AppDatabase_Impl` membuka **`app_data`** dan tabel **`app_store`** (`base_jadx/sources/com/wssc/appanalyzer/room/database/AppDatabase_Impl.java:32`), DAO `ic.d` / `ic.e`. Nama semantik DAO tidak bisa dipastikan karena obfuskasi.
- Dependency menonjol: AndroidX (Room, WorkManager, Navigation, ViewPager2, Startup, DocumentFile), Kotlin coroutines, Glide, Gson, Lottie, MPAndroidChart, XPopup, UtilCode, PermissionX, apkparser, MMKV, Zip4j; plus AdMob/AppLovin dan Firebase.

---

## 3. Peta Fitur

| Area | Status | Bukti ringkas |
|------|--------|---------------|
| Daftar aplikasi | ADA | `getInstalledPackages(0)` di `base_jadx/sources/rc/g.java:83-84` |
| Klasifikasi system/user | ADA | flag `ApplicationInfo` di `rc/g.java:90-93` |
| Update daftar otomatis | ADA | `LauncherApps` + receiver `PACKAGE_ADDED/REMOVED/REPLACED` di `td/a0.java:918-947`, `td/z.java:19-38` |
| Filter | ADA | All / User / Running / System / 32Bit / 64Bit — `AppsFilterMenuDialog.java:122-125` |
| Sort | ADA | Install time (default), Name, Size, Times opened, Last used, Screen time, Data use — `AppsFilterMenuDialog.java:125` |
| Pencarian + highlight | ADA | `AppsFragment.java:103,1226-1229`; regex highlight `pc/q2.java:127-141` |
| Chip New/Updated | ADA | instal 24 jam terakhir — `pc/q2.java:55-82` |
| Multi-select | ADA | `AppsFragment.java:129-148`, `AppUninstallFragment.java:720-732` |
| Detail aplikasi | ADA | model `td/n.java`, diisi `td/a0.java:215-358` |
| Manifest / komponen / sertifikat / grafis | ADA | `AppManifestFragment`, `AppComponentsFragment`, `AppCertificateFragment`, `AppGraphicsFragment` |
| Ukuran app/data/cache | ADA | `StorageStatsManager` di `td/h.java:43-55`, fallback hidden API `:96-98` |
| Daftar izin + granted/revoked | ADA | `requestedPermissions` + flag di `td/a0.java:721-762` |
| Special access | ADA | 17 permission + deep link Settings — `cd/a.java:79,442-595` |
| Open app | ADA | `getLaunchIntentForPackage` — `AppDetailFragment.java:206-209` |
| Uninstall (termasuk batch) | ADA | `ACTION_DELETE` + `Uri.parse("package:…")` — `AppUninstallFragment.java:734-755` |
| Force stop | ADA (otomasi UI) | AccessibilityService klik tombol Settings — `xc/b.java:75,156-165`, `xc/a.java:224-240` |
| Clear cache | ADA (otomasi UI) | `vc/a.java:128-129,195-196,263-264` |
| Clear data | TIDAK ADA langsung | hanya `ACTION_APPLICATION_DETAILS_SETTINGS` — `cd/a.java:395-405` |
| Enable/disable | TIDAK ADA | `setApplicationEnabledSetting` = 0 hasil; "freeze" = force stop |
| Install APK/XAPK | ADA | `PackageInstaller.createSession/commit` — `j8/a.java:96,170,295,308` |
| Share APK | ADA | `ACTION_SEND`/`SEND_MULTIPLE`, chooser "Share apps" — `pc/v2.java:139-197` |
| Backup/restore aplikasi | TIDAK YAKIN | "backups" hanya pemilih folder SAF — `xb/t0.java:51-79`, `StorageSwitchFragment.java:63-113` |
| Extract APK | TIDAK YAKIN | dialog `extracting` hanya dead code — `pc/g.java:121-125`, `AppDetailFragment.java:73,92` |
| Tags/kategori | ADA | `TagsFragment`, `TagsDetailFragment`; kategori Games/Social/News/dll |
| Bookmark/favorite klasik | TIDAK ADA | tidak ada referensi di `com/wssc` |
| Widget / shortcut | TIDAK ADA | tidak ada `AppWidgetProvider`; `launcher_shirtcuts.xml` kosong |
| AppOps per-app | TIDAK ADA | hanya self-check `checkOpNoThrow` — `td/j.java:61-64` |
| Root / Shizuku / shell | TIDAK ADA | tidak ada `su`, Shizuku, `Runtime.exec`, `pm list`, `dumpsys` di `com/wssc` |
| Iklan + telemetri | ADA | AdMob/AppLovin, Firebase Analytics/Messaging — `AndroidManifest.xml:48-54,207-217` |
| Premium/billing | TIDAK ADA | hanya reward-video unlock (`strings.xml:383-385`) |

---

## 4. Detail Teknis yang Relevan untuk FTxT

### 4.1 Daftar aplikasi & cache

Referensi memanggil `getInstalledPackages(0)` lalu mengklasifikasikan dengan flag `ApplicationInfo` (`SYSTEM_APP_FLAG`, `DEVELOPER_APP_FLAG`, dll). Daftar di-refresh oleh `LauncherApps.Callback` dan receiver package; cache dibersihkan saat uninstall (`td/z.java:19-38`). Ini menghindarkan daftar basi setelah instal/uninstall — pola yang belum ada di FTxT.

**Catatan visibility:** referensi mendeklarasikan `QUERY_ALL_PACKAGES` (`AndroidManifest.xml:28`). FTxT saat ini hanya punya `<queries>` untuk `ACTION_MAIN` + `LAUNCHER` (`app/src/main/AndroidManifest.xml:16-21`). Tanpa perluasan, `getInstalledPackages()` hanya mengembalikan aplikasi yang terlihat sesuai filter launcher. Perluasan `<queries>` (intent ACTION_MAIN/LAUNCHER, VIEW, dan filter sesuai kebutuhan) lebih aman untuk Play Policy daripada meniru `QUERY_ALL_PACKAGES`.

### 4.2 Detail & ukuran

Field yang dikumpulkan: nama, paket, versi, `targetSdkVersion`/nama Android, `minSdkVersion`, `sourceDir`/`publicSourceDir`, `dataDir`, `processName`, `firstInstallTime`, `lastUpdateTime`, `installLocation`, UID, flag system/user, enabled, ABI, installer, kategori (`td/a0.java:215-358`). Sumber installer memakai `getInstallSourceInfo()` + mapping Amazon/Samsung/Facebook (`:428-469`).

Ukuran memakai `StorageStatsManager.queryStatsForPackage()` (app/data/cache) dengan fallback refleksi `getPackageSizeInfo()` (`td/h.java:43-55,96-98`). **Fallback ini jangan ditiru** — FTxT sebaiknya menampilkan "ukuran tidak tersedia" bila query gagal.

### 4.3 Izin

Referensi menampilkan `requestedPermissions` beserta status granted (`requestedPermissionsFlags & 2`), label/deskripsi, dan protection level (`td/a0.java:721-762`), plus 17 special access dengan deep link ke Settings (`cd/a.java:79,442-595`). Ini sejalan dengan rencana `APP_MANAGER.md`, tetapi FTxT perlu memutuskan apakah izin ditampilkan **read-only** atau dengan toggle. Meniru toggle berisiko karena perubahan izin aplikasi pihak ketiga tidak bisa dilakukan dari aplikasi biasa.

### 4.4 Aksi

- Uninstall, open, dan share APK memakai API public standar — aman untuk ditiru.
- Force stop dan clear cache diotomatisasi dengan `AccessibilityService` yang menekan tombol di halaman Settings. Ini bekerja tanpa root, tetapi **intrusif**: butuh izin aksesibilitas, rentan berubah tiap OEM/versi Android, dan berpotensi melanggar kebijakan Play bila dipakai untuk otomasi pihak ketiga. FTxT sebaiknya membuka `ACTION_APPLICATION_DETAILS_SETTINGS` sebagai jalur utama.
- Clear data hanya membuka Settings. Ini sesuai dengan batas platform dan bisa dipakai FTxT apa adanya.

### 4.5 Hubungan dengan infrastruktur FTxT yang sudah ada

FTxT sudah memiliki `PACKAGE_USAGE_STATS`, `REQUEST_INSTALL_PACKAGES`, `<queries>` launcher, `FileProvider`, infra Shizuku (`app/src/main/java/exp/ftxt/features/gamefps/reader/ShizukuHelper.java`, `ShizukuShell.java`), tombol izin Shizuku di `SettingsActivity.java:207-212,810-844`, dan provider Shizuku di manifest `:145-152`. Belum ada kode untuk `getInstalledPackages`, `StorageStatsManager`, atau receiver `PACKAGE_ADDED/REMOVED` — jadi fondasi daftar aplikasi memang baru.

Modul FTxT terdiri dari `app`, `core`, `shared/*`, dan `features/*` yang semuanya berorientasi overlay (`OverlayModule`). App Manager **bukan overlay**, sehingga mengikuti pola halaman non-overlay (`SessionListActivity`, `VoltageStabilityActivity`, `DocumentationActivity`) dan meletakkan logika bersama di `core`, bukan membuat `features/appmanager` dengan `OverlayModule`. Ini sejalan dengan `APP_RUNNER.md:74-91`.

---

## 5. Perbandingan dengan Rencana `APP_MANAGER.md`

| Rencana lama | Fakta dari APK acuan | Rekomendasi untuk FTxT |
|--------------|----------------------|-------------------------|
| `getInstalledPackages()` + `<queries>` | Sama, tapi acuan menambah `QUERY_ALL_PACKAGES` | Pakai `<queries>` perluasan; hindari `QUERY_ALL_PACKAGES` |
| Filter User/System/Semua | Lebih kaya: Running, 32Bit, 64Bit | Mulai dari User/System/Semua; Running menyambung ke `APP_RUNNER.md` |
| Sort nama, ukuran, install, terakhir dibuka | Plus times opened, screen time, data use | Ambil 4 sort dasar dulu; tambah screen time/data use bila `PACKAGE_USAGE_STATS` tersedia |
| `getPackageSizeInfo()` | Ada sebagai fallback refleksi | **Buang**; pakai `StorageStatsManager`, fallback teks "tidak tersedia" |
| Force stop via permission system | Tidak ada; otomasi AccessibilityService | Buka Settings; opsi Shizuku `am force-stop` sebagai Tier B (perlu verifikasi) |
| Clear cache manual `getCacheDir()` | Otomasi Settings lewat aksesibilitas | Buka Settings; jangan hapus folder aplikasi lain langsung |
| Clear data | Juga hanya membuka Settings | Sesuai rencana — tetap dengan konfirmasi |
| Backup APK + data (root only) | Tidak ada bukti backup/restore; "backups" hanya folder | Tunda/nonaktifkan; jangan jadikan fitur utama |
| Share APK opsional | Terimplementasi dengan chooser | Naikkan prioritas ke Fase 2 (murah, API public) |
| 2 Activity + adapter + utils | Single Activity + 22 fragment + 32 widget | FTxT cukup 2 Activity + adapter + helper; jangan meniru 22 fragment |

---

## 6. Rekomendasi Desain untuk FTxT

1. **Fondasi bersama dengan App Runner.** Satu helper daftar aplikasi di `core` dipakai bersama oleh App Manager dan App Runner (dalam `APP_RUNNER.md` juga disebut kelas `InstalledApps`). Jangan ada dua query terpisah untuk kebutuhan yang sama.
2. **Model satu sumber.** `AppInfo` berisi paket, label, ikon, flag system/user, launcher, versi, ukuran (opsional), firstInstall/lastUpdate, dan status terpasang. Field berat (sertifikat, komponen, manifest) di-query on-demand saat halaman detail dibuka.
3. **Halaman non-overlay.** `AppManagerActivity` (daftar) + `AppDetailActivity` (detail), mengikuti pola `SessionListActivity`. Tidak masuk `SmartPanelRegistry`, tidak punya `OverlayModule`.
4. **Daftar: filter chip + search + sort.** Tambahkan `PACKAGE_ADDED/REMOVED/REPLACED` receiver untuk invalidate cache. Multi-select dengan action bar dan konfirmasi jumlah item.
5. **Detail bertahap.** Fase awal: info, ukuran, izin (read-only), tombol aksi. Lanjutan: komponen, sertifikat, manifest, history — hanya jika ada kebutuhan nyata.
6. **Aksi aman dulu.** Open, uninstall, share, dan buka Settings. Force stop/clear cache/clear data lewat Settings dengan pesan jelas. Shizuku hanya sebagai opsi lanjutan dan transparan.
7. **Izin bertahap.** FTxT sudah punya `PACKAGE_USAGE_STATS` dan `REQUEST_INSTALL_PACKAGES`. Untuk special access, arahkan ke Settings satu per satu; jangan minta aksesibilitas hanya untuk otomasi aksi.
8. **Tidak menambah DB baru untuk daftar.** Daftar adalah data sistem yang bisa di-query ulang; simpan hanya preferensi tampilan (filter/sort terakhir) di `ftxt_prefs`.

### File kandidat (mengikuti konvensi FTxT)

| File | Keterangan |
|------|------------|
| `core/src/main/java/exp/ftxt/core/appmanager/InstalledApps.java` (baru) | Query + cache daftar + receiver invalidate; dipakai bareng App Runner |
| `core/src/main/java/exp/ftxt/core/appmanager/AppInfo.java` (baru) | Model ringan |
| `core/src/main/java/exp/ftxt/core/appmanager/AppDetails.java` (baru) | Query on-demand: ukuran, izin, komponen |
| `core/src/main/java/exp/ftxt/core/appmanager/AppActions.java` (baru) | Open/uninstall/share/buka Settings |
| `app/src/main/java/exp/ftxt/ui/AppManagerActivity.java` (baru) | Halaman daftar |
| `app/src/main/java/exp/ftxt/ui/AppDetailActivity.java` (baru) | Halaman detail |
| `app/src/main/java/exp/ftxt/ui/AppListAdapter.java` (baru) | Adapter RecyclerView + multi-select |
| `app/src/main/res/layout/activity_app_manager.xml`, `activity_app_detail.xml`, `item_app.xml` (baru) | Layout |
| `app/src/main/AndroidManifest.xml` (ubah) | Registrasi activity, receiver package, perluasan `<queries>` |
| `_schedule/AKTIF/APP_MANAGER.md` (sudah direvisi) | Rencana diselaraskan dengan kajian ini |

---

## 7. Yang Tidak Ditiru

- Iklan AdMob/AppLovin, reward-video unlock, consent GDPR/AD_ID, install referrer, Firebase Analytics/Crashlytics/Messaging/Remote Config.
- Otomasi `AccessibilityService` untuk force stop / clear cache pihak ketiga.
- Hidden API reflection `getPackageSizeInfo()` dan `QUERY_ALL_PACKAGES`.
- Installer XAPK/APKS/ZIP — FTxT hanya butuh install APK standar bila sama sekali diperlukan.
- 22 fragment + 32 widget, 16 locale, tema aksen berbasis iklan, shortcut kosong, dan label "backups" yang sebenarnya pemilih folder.
- Dead code "extract APK" dan klaim backup/restore tanpa implementasi yang terbukti.

---

## 8. Risiko & Batasan

- **Package visibility:** tanpa `QUERY_ALL_PACKAGES`, daftar bisa tidak lengkap pada perangkat dengan banyak aplikasi non-launcher. Uji di Android 11, 13, dan 15 sebelum memutuskan perluasan `<queries>`.
- **Ukuran:** `StorageStatsManager` bisa gagal pada aplikasi sistem atau ROM tertentu; selalu sediakan fallback teks.
- **Aksi berbahaya:** uninstall dan clear data harus memakai konfirmasi eksplisit. Jangan pernah menghapus direktori aplikasi lain langsung.
- **Otomasi UI:** rentan berubah pada update Settings OEM; inilah alasan FTxT memilih jalur Settings/Shizuku, bukan AccessibilityService.
- **Obfuskasi acuan:** nilai interval, ambang, dan beberapa nama DAO tidak dapat dipastikan; dokumen ini sengaja tidak memakainya sebagai acuan.
- **Split APK:** analisis tidak mencakup native library di split ABI; bila fitur acuan bergantung pada `.so`, kemungkinan besar tidak relevan untuk FTxT.

---

## 9. Estimasi Kasar (bukan komitmen)

| Tahap | Estimasi |
|-------|----------|
| Fondasi `InstalledApps` + `AppInfo` + receiver + `<queries>` | 4–6 jam |
| Daftar, filter, sort, search, multi-select | 5–7 jam |
| Detail + ukuran + izin (read-only) + aksi aman | 6–8 jam |
| Integrasi App Runner + opsi Shizuku + pengujian ROM | 6–10 jam |
| **Total** | **≈21–31 jam** |

Rencana lama memperkirakan 13–19 jam; selisihnya terutama karena tambahan cache/invalidasi, uji visibility, dan integrasi dengan App Runner. Bisa dipecah: (1) fondasi + daftar, (2) detail + aksi aman, (3) integrasi + Shizuku.

---

## 10. Status Kajian

- [x] Dekompilasi & verifikasi artefak
- [x] Inventarisasi manifest, komponen, dan navigasi
- [x] Peta fitur + bukti kode
- [x] Perbandingan dengan `AKTIF/APP_MANAGER.md` dan `APP_RUNNER.md`
- [x] Keputusan user: rencana `APP_MANAGER.md` direvisi mengikuti kajian ini
- [x] Keputusan user: ruang lingkup Fase 1 — Shizuku opsional
