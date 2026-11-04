# Implementasi Shizuku FPS Reader (PoC-First)

## Gambaran
Membaca FPS dari aplikasi/game yang sedang aktif di layar menggunakan Shizuku dan `dumpsys gfxinfo`. Target saat ini: **buktikan dulu fondasi bisa jalan (PoC), baru bangun overlay/UI**.

**Branch kerja:** `feature/shizuku-fps-overlay`  
**Status:** PoC

---

## Tujuan PoC
- Validasi: bisa dapatkan FPS dari game via `Shizuku.newProcess()` + `dumpsys gfxinfo <package>`
- Validasi: parsing hasil `dumpsys gfxinfo` cukup stabil di device yang dipakai untuk tes
- Validasi: performa polling masih bisa ditoleransi (tidak bikin berat)
- **Hasil PoC:** cukup log ke Logcat dengan nilai FPS yang masuk akal. Tidak wajib ada UI/panel.

---

## Fase 0: Analisis & Persiapan (Wajib)
- [ ] Cek pola modul overlay yang ada di proyek (Config/Module/PanelController/PositionController) buat acuan nanti
- [ ] Cek `FloatingService` cara registrasi modul
- [ ] Cek pola integrasi menu/drawer (opsional, buat perencanaan fase berikutnya)
- [ ] Kumpulkan sample output `dumpsys gfxinfo` dari minimal 1 game yang bakal dipakai buat tes PoC

---

## Fase 1: Persiapan & Integrasi Shizuku
- [ ] **1.1 Dependency** — Tambah ke `app/build.gradle`: `dev.rikka.shizuku:api:13.1.0` (atau versi stabil yang kompatibel). Tambah `dev.rikka.shizuku:provider:13.1.0` jika dibutuhkan. Cek kompatibilitas dulu.
- [ ] **1.2 Manifest** — Tambah `ShizukuProvider` dengan authority `packageName.shizuku` (ikuti pola Shizuku SDK). Jangan lupa permission yang relevan kalau ada.
- [ ] **1.3 Izin Shizuku** — Implementasi helper: cek `Shizuku.pingBinder()`, `Shizuku.checkSelfPermission()`, `Shizuku.requestPermission()`. Handle listener `Shizuku.OnBinderReceivedListener` / `OnBinderDeadListener`. Handle kasus `Shizuku not running` / `permission denied`.
- [ ] **1.4 Koneksi dasar** — Buat class util `ShizukuHelper` (sederhana) buat cek status & minta izin.

---

## Fase 2: Baca & Parse FPS via dumpsys (CORE PoC)
- [ ] **2.1 Eksekusi via Shizuku** — Buat `ShizukuShell`/`GameFpsReader` util. Gunakan `Shizuku.newProcess(new String[]{"sh", "-c", "dumpsys gfxinfo <package>"}, null, null)` atau cara yang aman. Tangkap stdout+stderr.
- [ ] **2.2 Ambil package target** — Untuk PoC, bisa hardcode package game yang dipakai tes dulu (lebih cepat). Otomatisasi via `UsageStatsManager` bisa ditambah nanti.
- [ ] **2.3 Parsing toleran** — Cari data dari `dumpsys gfxinfo` dengan pendekatan regex toleran:
  - Coba ambil dari `Profile data` / `Frame stats` jika ada
  - Fallback ke `Total frames rendered`, `Janky frames`, `Number Missed Vsync`
  - Hitung FPS dari interval waktu / jumlah frame (prioritaskan cara paling stabil dari sample)
- [ ] **2.4 Validasi angka** — Normalisasi nilai (integer/float), clamp wajar (mis. 1-240), tangani `NaN/0`. Jika gagal parsing -> kembalikan `null`/`-1` + log alasan.
- [ ] **2.5 Looping ringan** — Polling interval bisa dikonfigurasi (mulai 1000ms, bisa turun/naik). Hentikan kalau Shizuku mati/izin dicabut. Tambah timeout baca output.

**Kriteria lulus PoC:** dapat angka FPS konsisten & masuk akal saat game berjalan, minimal 3-5 menit tanpa crash/ANR.

---

## Fase 3: Overlay/UI (SETELAH PoC LULUS)
Hanya dikerjakan **jika Fase 1-2 terbukti berhasil**.

- [ ] **3.1 Config/Module** — `GameFpsConfig.java`, `GameFpsModule.java` (ikuti pola existing)
- [ ] **3.2 Controller** — `GameFpsPositionController.java` (bisa reuse), `GameFpsPanelController.java`
- [ ] **3.3 Layout** — `panel_game_fps.xml` sesuai kebutuhan panel
- [ ] **3.4 Integrasi** — Tambah item menu ke drawer/MainActivity + register di `FloatingService`
- [ ] **3.5 Auto-detect foreground** — Tambah deteksi via `UsageStatsManager` + izin `PACKAGE_USAGE_STATS` (jika masih dibutuhkan)

---

## Error Handling & Edge Case
- **Shizuku tidak aktif**: Log + bisa arahkan buka app Shizuku
- **Izin ditolak**: Nonaktifkan fitur, log jelas
- **dumpsys gagal/kosong/timeout**: Jangan crash, kembalikan null, coba lagi next interval
- **Ganti game/app**: Siapkan mekanisme update target package
- **Perbedaan OEM**: Parser harus toleran, minimal ada fallback
- **Beban performa**: Batasi polling, stop saat tidak relevan

---

## Testing
- **PoC test:** Jalankan game (PUBG/Mobile Legends/yang gampang), cek logcat FPS. Bandingin kasar sama yang terlihat/harapan.
- **Stabilitas:** Lari 5-10 menit, perhatikan lag/overheat
- **Izin flow:** Tes Shizuku mati -> nyala, izin dicabut

---

## Catatan Eksekusi
- **Prioritas mutlak:** Fase 0-2 dulu. **Jangan** buat XML/panel/Menu sebelum PoC lulus.
- **Scope minimal:** Buat class util di tempat yang wajar (bisa di package baru terpisah) biar gampang dihapus kalo gagal.
- **Aman di branch:** Kerja di `feature/shizuku-fps-overlay`, bisa balik ke `main` kapan aja.
- **Parsing first:** Kumpulin sample output dulu baru coding parser.

---

## Risiko
- Output `dumpsys gfxinfo` beda tiap OEM/Android version
- Butuh Shizuku aktif + izin user
- Pola parsing bisa berubah
- Polling bisa bikin beban, perlu disesuaikan
- Tidak semua game kompatibel (jarang, tapi mungkin)
