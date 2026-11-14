# Deteksi Mode Pengisian Bypass — via Shizuku

**Status:** Terimplementasi — parser dumpsys, detektor, indikator, dan toggle sudah dikerjakan; tinggal pengujian di perangkat bypass
**Tujuan:** Mendeteksi saat perangkat dalam mode pengisian bypass (baterai tidak menerima arus meski adaptor terhubung, umum di HP gaming) dan menampilkan indikatornya di modul Battery Bar / Battery Stats.

---

## 1. Gambaran

Tidak ada API Android publik untuk mendeteksi bypass charging. Deteksi dilakukan **heuristik** dengan menggabungkan 3 sumber sinyal:

1. **Broadcast** (`ACTION_BATTERY_CHANGED`) — gratis, tanpa izin: `plugged`, `status`, `level`.
2. **sysfs** (`/sys/class/power_supply/battery/...`) — `current_now`, `charge_type`, node vendor lain. Sebagian perangkat melindungi path ini (root/shell-only).
3. **`dumpsys battery` / `dumpsys power`** — sumber otoritatif, dijalankan sebagai UID shell lewat **Shizuku**. UID shell sudah memegang `READ_BATTERY_STATS`, jadi tidak perlu izin runtime tambahan.

### Heuristik

- `plugged != 0` (AC/USB) + `status == NOT_CHARGING` + `level < 100` → indikasi kuat. Normalnya `NOT_CHARGING` hanya saat baterai penuh.
- Level stagnan beberapa interval saat adaptor terhubung.
- `current_now` ≈ 0 (atau positif kecil) saat adaptor terpasang → baterai tidak menerima arus.
- Node sysfs vendor eksplisit, misal `charge_type` bernilai "Bypass" — bila ada, ini bukti terkuat.

### Konfirmasi & pengecualian (wajib, belum dirinci di versi lama)

- Jangan langsung menampilkan status dari satu sampel. Butuh kondisi konsisten minimal **30–60 detik / N sampel** sebelum status "Bypass" ditampilkan.
- Minimal **dua sumber** harus setuju (misal broadcast + arus ≈ 0), kecuali node vendor menyebut bypass secara eksplisit.
- Pengecualian yang harus menekan false positive: battery care / batas cas 80%, jeda charging karena suhu tinggi, dan status vendor yang melaporkan `NOT_CHARGING` saat arus kecil.
- Bypass tidak selalu diekspos framework. Tanpa node vendor, hasil tetap heuristik dan sebaiknya ditampilkan sebagai indikasi, bukan kepastian.

---

## 2. Kondisi Kode Saat Ini

Sudah tersedia (tidak perlu dibuat ulang):

- Dependency `dev.rikka.shizuku:api/provider:13.1.0` di `app/build.gradle`.
- `<provider>` Shizuku di `AndroidManifest.xml`.
- `ShizukuHelper` — cek binder, cek/izin, request permission.
- Flow izin, status, dan tombol buka Shizuku di `SettingsActivity`.
- `ShizukuShell.execArgs()` — eksekusi perintah shell via sesi, timeout, fallback one-shot.
- `BatteryReading` — sudah membaca `status`, `plugged`, `level`, `current_now` (extra + `BATTERY_PROPERTY_CURRENT_NOW`), serta sysfs `current_now`/`voltage_now` dengan filter entri bertipe Battery.

Sudah diimplementasikan:

- `BypassChargingDetector` — heuristik status/level/arus, konfirmasi 45 detik, pengecualian batas cas ≥ 80%.
- `DumpsysBatteryProbe` — parser `dumpsys battery` via Shizuku, throttle 10 detik, cache 20 detik.
- `BatteryReading.readSysfsText()` — pembacaan `charge_type`, `battery_charging_enabled`, `input_suspend`.
- Indikator di overlay Battery Info dan status ring tab Monitor.
- Toggle "Deteksi Bypass Charging" di panel Battery.

Catatan: `dumpsys power` tidak dipakai karena data baterai sudah cukup; bisa ditambahkan bila device tertentu butuh sinyal tambahan.

---

## 3. Fase Pengerjaan

### Fase A — Setup Dasar Shizuku — SELESAI
Dependency, provider, helper, dan flow izin sudah ada. Tidak ada sisa pekerjaan.

### Fase B — Eksekusi & Parsing Shell (±1–2 jam)
1. Parser `dumpsys battery`: `status`, `level`, `plugged`, `health`, dan field charging vendor bila ada.
2. Parser `dumpsys power` untuk field yang relevan (state charging/plug), dengan toleransi variasi output.
3. Baca sysfs `charge_type` dan node vendor bypass; fallback ke `cat` lewat `ShizukuShell` bila path tidak readable langsung.
4. Bungkus pembacaan ke satu kelas sumber daya baterai, terpisah dari logika keputusan.

### Fase C — Logika Deteksi + Integrasi Modul (±2–3 jam)
1. Gabungkan sumber dengan prioritas: broadcast (selalu jalan) → sysfs → dumpsys (saat Shizuku aktif).
2. State machine konfirmasi: akumulasi sampel, durasi minimum, reset saat adaptor dicabut atau status berubah.
3. Fallback Shizuku nonaktif: deteksi tetap berjalan dengan broadcast + sysfs.
4. Indikator bypass di Battery Bar / Battery Stats: field config baru, tampilan label/ikon, interval update.
5. Ikutkan dalam preset (`OverlayPreset` + `PositionController`/panel) bila relevan.

### Fase D — Testing & Dokumentasi (±1 jam + waktu uji perangkat)
1. Uji manual di kondisi: normal charging, penuh, bypass, battery care, suhu tinggi, perangkat tanpa fitur bypass.
2. Verifikasi tidak ada false positive saat charging pause.
3. Update CHANGELOG, README, PANDUAN, STRUKTUR di root. Salinan `app/src/main/assets/` tidak disentuh manual — `syncDocs` menyalinnya otomatis saat build.

---

## 4. Estimasi Total

**±3–6 jam kerja aktif**, plus waktu pengujian pada device yang mendukung bypass. Fondasi Shizuku yang sebelumnya memakan 1–2 jam sudah tidak dihitung.

---

## 5. Komponen yang Diperkirakan

- `BypassDetector.java` (baru) — logika gabungan, ambang, dan state konfirmasi.
- `DumpsysBatteryParser.java` (baru, nama menyesuaikan pola project) — parser output dumpsys.
- `BatteryReading.java` (ubah) — tambah pembacaan `charge_type`/node vendor bila memungkinkan.
- Modul Battery Bar / Battery Stats (ubah) — indikator bypass + config.
- `ShizukuHelper` / `ShizukuShell` (pakai ulang) — tidak perlu perubahan besar.
- Dokumen root (ubah) — CHANGELOG, README, PANDUAN, STRUKTUR.

Dependency dan manifest tidak perlu diubah lagi.

---

## 6. Risiko & Pertimbangan

- **Perangkat uji** — wajib ada device pendukung bypass (ROG, Xiaomi gaming, Lenovo Legion, dll) untuk verifikasi; tanpanya hasil tidak tuntas.
- **Bypass sering tidak terekspos** — tanpa node vendor, framework bisa tetap melaporkan status charging normal, sehingga akurasi terbatas.
- **False positive** — battery care, batas cas, dan jeda thermal bisa terlihat seperti bypass. Mitigasi lewat ambang konfirmasi dan pengecualian di atas.
- **Variasi output `dumpsys`/sysfs** antar vendor dan versi Android — parser harus toleran dan gagal dengan aman.
- **Privasi/keamanan** — batasi perintah ke battery/power saja, jangan log output mentah berlebihan.

---

## 7. Alternatif Ringan (Tanpa Shizuku)

- Deteksi hanya dari broadcast (`plugged` + `status` + `level`) + sysfs yang readable.
- Bahan bacanya sudah ada di `BatteryReading`, jadi tinggal logika heuristik dan tampilan — estimasi ±1 jam.
- Akurasi cukup untuk indikasi awal, tapi tidak bisa membaca `dumpsys` dan sysfs yang diproteksi.
- Disarankan sebagai tahap pertama; Shizuku menyusul untuk akurasi penuh.

---

## 8. Status

- [x] Fase A — Setup dasar Shizuku
- [x] Fase B — Eksekusi & parsing shell
- [x] Fase C — Logika deteksi + integrasi modul
- [ ] Fase D — Testing & dokumentasi (kode selesai; uji di perangkat bypass belum)
