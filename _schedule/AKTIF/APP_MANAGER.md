# App Manager

**Status:** Fase 1–2 selesai di kode; Fase 3 sebagian (tag + Shizuku), integrasi App Runner masih menunggu fitur tersebut.
**Tujuan:** Mengelola aplikasi terpasang dengan fitur dan pola interaksi yang cukup mirip `com.wssc.appanalyzer`, memakai API public dan infrastruktur FTxT yang sudah ada.
**Dasar:** `_schedule/KAJIAN/ANALISIS_APP_MANAGER_REFERENSI.md` (dekompilasi `_sample/App Manager/base.apk`), disesuaikan dengan kondisi aktual project (minSdk 26, Java, `app` + `core`, Material 1.12.0, ViewPager2 1.1.0).

---

## 1. Arah Penyesuaian

"Mirip" berarti **fitur inti, alur, dan informasi yang ditampilkan** mengikuti APK acuan — bukan meniru struktur internalnya. Acuan memakai satu Activity + 22 fragment; FTxT memakai 2 Activity + tab/kartu ringan, tanpa iklan dan tanpa otomasi aksesibilitas.

Yang dijadikan acuan:

- Sumber daftar `PackageManager.getInstalledPackages(0)`, diperkaya `LauncherApps` dan receiver package.
- Filter 6 kriteria, sort 7 kriteria, pencarian dengan highlight, chip New/Updated.
- Multi-select dengan action bar + konfirmasi untuk aksi massal.
- Halaman detail bertab: info, ukuran, izin, special access, komponen, sertifikat, grafis, pemakaian.
- Aksi aman lewat API public: buka, uninstall, bagikan APK, buka Settings.

Yang **tidak** ditiru (lihat kajian §7):

- Iklan AdMob/AppLovin, reward-video unlock, Firebase Analytics/Crashlytics/Messaging/Remote Config.
- Otomasi `AccessibilityService` untuk force stop / clear cache pihak ketiga.
- Hidden API reflection `getPackageSizeInfo()` dan `QUERY_ALL_PACKAGES`.
- Enable/disable aplikasi (acuan tidak memilikinya; "freeze" di acuan = force stop).
- Backup/restore APK+data dan "extract APK" (tidak ada bukti implementasi nyata di acuan).
- 22 fragment, 16 locale, tema aksen iklan, shortcut kosong.

---

## 2. Peta Fitur

### 2.1 Daftar aplikasi

- Query `getInstalledPackages(0)` di helper bersama `InstalledApps`; klasifikasi system/user dari flag `ApplicationInfo`, plus ABI 32/64 bit dan sumber installer (`getInstallSourceInfo()` + mapping Amazon/Samsung/Facebook).
- Perbarui daftar otomatis lewat `LauncherApps.Callback` dan receiver `PACKAGE_ADDED`, `PACKAGE_REMOVED`, `PACKAGE_REPLACED`; cache di-invalidate saat uninstall.
- Filter chip: **All / User / Running / System / 32Bit / 64Bit**. "Running" adalah proksi dari event `UsageStatsManager` (atau `pidof` bila Shizuku aktif) — diberi keterangan akurasi terbatas di UI.
- Sort: **Install time** (default), Name, Size, Times opened, Last used, Screen time, Data use. Empat pertama wajib; tiga terakhir menyusul bila `PACKAGE_USAGE_STATS` dan sumber data tersedia.
- Pencarian nama/package dengan highlight hasil (regex), chip **New/Updated** untuk instal atau update dalam 24 jam terakhir.
- Multi-select dengan action bar; konfirmasi menampilkan jumlah item sebelum uninstall massal.
- Preferensi filter/sort terakhir disimpan di `ftxt_prefs`, bukan database baru.

### 2.2 Detail aplikasi (bertab, di-query on-demand)

| Tab | Isi |
|-----|-----|
| Info | Label, package, versionName/versionCode, target/min SDK + nama Android, firstInstall/lastUpdate, installLocation, UID, processName, sourceDir/dataDir, installer, flag system/user, enabled |
| Ukuran | APK, data, cache, total dari `StorageStatsManager.queryStatsForPackage()`; fallback teks "ukuran tidak tersedia" bila query gagal |
| Izin | `requestedPermissions` + status granted/revoked, label/deskripsi, protection level — **read-only**, tanpa toggle |
| Special access | Daftar special access (sekitar 17 entri) dengan deep link ke halaman Settings terkait, satu per satu |
| Lanjutan | Komponen (activity/service/receiver/provider), sertifikat, ringkasan manifest, grafis; hanya di-query saat tab dibuka |
| Pemakaian | Times opened, last used, screen time; data use bila tersedia — dari `UsageStatsManager` |

Field berat tidak dimuat saat daftar dibuka. `AppInfo` tetap ringan: paket, label, ikon, flag, launcher, versi, waktu install/update.

### 2.3 Aksi

- **Aman, API public:** Open (`getLaunchIntentForPackage`), Uninstall per item + batch (`ACTION_DELETE`), Share APK (`ACTION_SEND`/`ACTION_SEND_MULTIPLE` + chooser), App Info (`ACTION_APPLICATION_DETAILS_SETTINGS`).
- **Lewat Settings:** Force stop, Clear cache, Clear data → buka halaman detail aplikasi dengan pesan jujur bahwa FTxT tidak menghapus data aplikasi lain secara langsung. Clear data wajib konfirmasi eksplisit.
- **Tier B (opsional, transparan):** `am force-stop <package>` melalui `ShizukuShell` yang sudah ada. Tetap berfungsi tanpa Shizuku; jangan jadikan syarat.
- Tidak meminta izin aksesibilitas hanya untuk otomasi aksi.

### 2.4 Kategori / tag (Fase 3)

- Kategori otomatis (Games, Social, News, Tools, dll) dari nama/package, dengan opsi ubah manual oleh user.
- Disimpan sebagai preferensi di `ftxt_prefs` (package → kategori), tanpa tabel database baru.
- Bisa difilter dari halaman daftar; bukan bookmark/favorite klasik (acuan tidak memilikinya).

---

## 3. Sumber Data & Perilaku

- `InstalledApps`: satu helper untuk App Manager **dan** App Runner. Jangan ada dua query terpisah.
- Ukuran: `StorageStatsManager` tersedia langsung karena minSdk FTxT 26; jangan pakai fallback refleksi dan jangan hitung manual folder `dataDir`/`cacheDir` aplikasi lain.
- Izin: baca `requestedPermissions` dan `requestedPermissionsFlags`; tampilkan status, jangan ubah izin aplikasi pihak ketiga.
- Special access: arahkan ke Settings per entri; jangan meminta permission FTxT tambahan untuk ini.
- Package visibility: perluas `<queries>` dengan intent `MAIN`+`LAUNCHER` dan `VIEW` sesuai kebutuhan. Uji di Android 11, 13, 15 sebelum memutuskan perluasan lebih lanjut. `QUERY_ALL_PACKAGES` dilarang.
- Daftar adalah data sistem yang bisa di-query ulang; tidak perlu database lokal.

---

## 4. Arsitektur & Struktur File

App Manager **bukan overlay**: tidak masuk `SmartPanelRegistry`, tidak punya `OverlayModule`, tidak butuh modul Gradle baru. Logika bersama di `core`, UI di `app`, mengikuti pola `SessionListActivity` / `DocumentationActivity`.

| File | Keterangan |
|------|------------|
| `core/src/main/java/exp/ftxt/core/appmanager/InstalledApps.java` | Query + cache + receiver invalidate; dipakai bersama App Runner |
| `core/src/main/java/exp/ftxt/core/appmanager/AppInfo.java` | Model ringan + kategori + metrik pemakaian |
| `core/src/main/java/exp/ftxt/core/appmanager/AppDetails.java` | Query on-demand: ukuran, izin, komponen, sertifikat, grafis, pemakaian |
| `core/src/main/java/exp/ftxt/core/appmanager/AppActions.java` | Intent open/uninstall/settings/special access; framework-only |
| `app/src/main/java/exp/ftxt/ui/AppManagerActivity.java` | Halaman daftar: filter, sort, cari, multi-select |
| `app/src/main/java/exp/ftxt/ui/AppDetailActivity.java` | Halaman detail 6 tab + aksi |
| `app/src/main/java/exp/ftxt/ui/AppListAdapter.java` | RecyclerView + multi-select + highlight |
| `app/src/main/java/exp/ftxt/ui/AppShareHelper.java` | Salin APK ke cache lalu bagikan via `FileProvider` |
| `app/src/main/java/exp/ftxt/ui/AppManagerPackageReceiver.java` | Invalidasi cache saat paket berubah |
| `app/src/main/res/layout/activity_app_manager.xml`, `activity_app_detail.xml`, `item_app.xml`, `item_detail_page.xml` | Layout daftar, detail, item, dan halaman tab |
| `app/src/main/AndroidManifest.xml` | Registrasi 2 activity + receiver, perluasan `<queries>` |
| `app/src/main/java/exp/ftxt/MainActivity.java` | Menu "App Manager" di `showSettingsPopup()` |
| `_schedule/RENCANA/APP_RUNNER.md` | Path helper bersama sudah disinkronkan |

Catatan: helper/shell Shizuku berada di `app` (`exp.ftxt.features.gamefps.reader`), bukan `core`. Karena itu `AppActions` di `core` hanya membuat intent; eksekusi `am force-stop` tetap dipanggil dari `app`.

---

## 5. UI/UX

- Daftar: `Toolbar`, kolom pencarian, chip filter, dropdown sort, RecyclerView dengan ikon/nama/kategori/versi/waktu update, action bar saat multi-select; ukuran ditampilkan di tab detail.
- Detail: header ikon + nama + versi, lalu tab ringan. Tab lanjutan dan pemakaian dimuat saat dibuka agar halaman awal tetap cepat.
- Konfirmasi: uninstall, uninstall massal (tampilkan jumlah), clear data. Force stop/clear cache cukup pesan bahwa akan membuka Settings.
- Entry point: menu popup `MainActivity.showSettingsPopup()`; tombol kembali manual `finish()` seperti halaman non-overlay lain.

---

## 6. Izin & Manifest

- Sudah ada: `REQUEST_INSTALL_PACKAGES`, `PACKAGE_USAGE_STATS`, FileProvider, provider Shizuku.
- Tidak ditambahkan: `QUERY_ALL_PACKAGES`, `FORCE_STOP_PACKAGES`, permission aksesibilitas, permission storage.
- `<queries>` diperluas dengan `MAIN`/`LAUNCHER` dan `VIEW`; hindari deklarasi terlalu luas.
- Receiver package terdaftar di manifest dengan aksi `PACKAGE_ADDED/REMOVED/REPLACED`.

---

## 7. Fase & Estimasi

| Fase | Cakupan | Estimasi |
|------|---------|----------|
| 1 — Fondasi + daftar | `InstalledApps`, `AppInfo`, receiver, `<queries>`, filter/sort/search/highlight, multi-select | 9–13 jam |
| 2 — Detail + aksi aman | 4–6 tab detail, ukuran, izin, special access, open/uninstall/share/Settings | 6–8 jam |
| 3 — Integrasi + lanjutan | App Runner, opsi Shizuku `am force-stop`, tag/kategori, uji visibility & ROM | 6–10 jam |
| **Total** | | **≈21–31 jam (≈3–5 hari kerja efektif)** |

Tanpa Fase 3 (App Manager mandiri): ≈15–21 jam. Angka ini menggantikan estimasi lama 13–19 jam yang belum menghitung cache/invalidasi, uji visibility, dan integrasi.

Aktual per 2026/11/19: Fase 1–2 selesai; tag/kategori dan opsi Shizuku sudah ada, integrasi supervisor App Runner dan uji ROM belum dikerjakan.

---

## 8. Risiko & Batasan

- Package visibility tanpa `QUERY_ALL_PACKAGES` bisa membuat daftar tidak lengkap pada perangkat tertentu; perlu uji multi-versi Android.
- `StorageStatsManager` bisa gagal pada aplikasi sistem/ROM tertentu; selalu tampilkan fallback teks.
- Label "Running" adalah proksi, bukan status proses presisi tanpa Shizuku; UI harus jujur.
- Aksi uninstall/clear data harus konfirmasi; jangan pernah hapus direktori aplikasi lain langsung.
- Perilaku Settings berbeda tiap OEM; jalur Shizuku hanya tambahan, bukan jaminan.

---

## 9. Hubungan Dokumen

- `_schedule/KAJIAN/ANALISIS_APP_MANAGER_REFERENSI.md` — sumber fakta dan batasan; baca sebelum implementasi.
- `_schedule/RENCANA/APP_RUNNER.md` — pemakai bersama `InstalledApps`; path sudah disinkronkan ke `core/appmanager`.
- Perubahan dokumen ini tidak dicatat di CHANGELOG (aturan `AGENTS.md` §2.7).

---

## 10. Status

- [x] Revisi rencana disetujui (menggantikan rencana lama)
- [x] Keputusan: ruang lingkup Fase 1 — Shizuku opsional
- [x] Fase 1 — fondasi + daftar
- [x] Fase 2 — detail + aksi aman
- [x] Fase 3 sebagian — tag/kategori + opsi Shizuku
- [ ] Fase 3 — integrasi App Runner (fitur App Runner belum dibangun)
- [x] Sinkronisasi `APP_RUNNER.md`
- [ ] Testing Android 11 / 13 / 15
