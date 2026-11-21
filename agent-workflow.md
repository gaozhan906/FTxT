# Agent Workflow — Pendamping AGENTS.md

Dokumen ini mengatur alur kerja, CHANGELOG, dan versioning. Baca `AGENTS.md` lebih dulu, lalu `agent-rules.md`, lalu dokumen ini. Untuk perilaku kerja, gate kabar progres, dan self-check, ikuti `agent-rules.md`.

## 0. Kedudukan Dokumen

1. `AGENTS.md` adalah sumber aturan utama.
2. `agent-rules.md` adalah aturan perilaku dan gate kabar progres.
3. `agent-workflow.md` adalah aturan alur kerja, CHANGELOG, dan versioning.
4. Jika terjadi perbedaan, pilih ketentuan yang lebih ketat.
5. Jika aturan berubah, perbarui dokumen terkait agar tidak ada dua kebenaran yang berbeda.

## 1. Alur Edit Biasa

1. Baca `AGENTS.md`, `agent-rules.md`, dan dokumen ini.
2. Cek `git status` dan `git log` bila relevan.
3. Baca konteks yang akan diubah.
4. Kirim laporan temuan dan rencana sesuai gate di `agent-rules.md`.
5. Edit kode atau aset.
6. Verifikasi.
7. Kirim laporan hasil verifikasi.
8. Rapikan CHANGELOG dan versionCode bila diwajibkan.
9. Lakukan self-check sebelum jawaban akhir.
10. Jangan commit, tag, atau push tanpa perintah eksplisit.

## 2. Pre-release

1. Pastikan perubahan inti sudah selesai dan tidak ada pekerjaan setengah jalan yang tertinggal.
2. Jalankan pemeriksaan yang diminta atau yang relevan; jangan menjalankan build tanpa perintah eksplisit.
3. Perbarui CHANGELOG dengan entry `ONGOING` yang lengkap.
4. Pastikan `versionName` dan `versionCode` di `app/build.gradle` sesuai judul entry.
5. Sinkronkan dokumen topik di `_schedule/AKTIF/<TOPIK>.md` bila ada.
6. Laporkan hasil pemeriksaan, termasuk yang belum diverifikasi.
7. Jangan commit, tag, atau push sebelum user memerintahkan.

## 3. Versioning

1. Gunakan semver `major.minor.patch`.
2. `major` naik untuk perubahan besar atau tidak kompatibel.
3. `minor` naik untuk fitur baru yang kompatibel.
4. `patch` naik untuk perbaikan bug, penyesuaian kecil, atau perubahan internal yang tidak mengubah perilaku utama.
5. Versi di CHANGELOG, `versionName`, dan dokumen topik harus sama.
6. `versionCode` naik setiap perubahan yang menghasilkan build baru; jangan pernah menurunkannya.

## 4. CHANGELOG

### 4.1 Entry dan Judul

1. Versi terbaru berada di paling atas.
2. Entry baru ditulis di atas entry lama.
3. Judul memakai format:

   ```text
   ## <versi> — <YYYY/MM/DD HH:MM WITA> <versionCode> [***ONGOING***|***RELEASED***]
   ```

4. Saat pekerjaan dimulai, gunakan `***ONGOING***`.
5. Setelah release, ubah menjadi `***RELEASED***`.
6. Gunakan waktu WITA.

### 4.2 Kategori

Gunakan section berikut bila ada isinya:

1. `### Added` untuk fitur atau berkas baru.
2. `### Changed` untuk perubahan perilaku, tampilan, atau struktur.
3. `### Fixed` untuk perbaikan bug.
4. `### Removed` untuk penghapusan.

Hilangkan section yang kosong.

### 4.3 Alur Penulisan

1. Tulis entry `ONGOING` saat pekerjaan dimulai atau saat perubahan pertama disetujui.
2. Perbarui entry selama pekerjaan berlangsung.
3. Jangan menimbun seluruh catatan sampai akhir.
4. Saat release, ubah status menjadi `RELEASED` dan isi tanggal/waktu final.
5. Jangan membiarkan dua entry `ONGOING`; gabungkan menjadi satu entry yang utuh.

### 4.4 VersionCode

1. `versionCode` di `app/build.gradle` harus sama dengan angka di judul entry.
2. `versionName` di `app/build.gradle` harus sama dengan versi di judul entry.
3. Naikkan `versionCode` setiap perubahan yang memengaruhi build.
4. Jangan memakai ulang `versionCode` lama.
5. Jangan menurunkan `versionCode`.

### 4.5 Isi Entry

1. Tulis perubahan yang benar-benar dilakukan, bukan rencana yang tidak jadi.
2. Jelaskan alasan dan dampak perubahan bila tidak jelas dari judul.
3. Sebutkan path atau nomor baris untuk perubahan penting.
4. Catat perintah verifikasi yang dijalankan.
5. Catat temuan riset atau referensi bila memengaruhi keputusan.
6. Jangan mencatat aktivitas tool rutin atau langkah yang tidak mengubah hasil.

### 4.6 Penggabungan Entry

1. Gabungkan entry `ONGOING` ke entry baru saat pre-release bila diperlukan.
2. Pertahankan seluruh perubahan nyata; jangan menghilangkan catatan yang masih relevan.
3. Jangan mengubah entry `RELEASED` tanpa perintah eksplisit.

### 4.7 Verifikasi

1. Periksa `git diff` untuk memastikan hanya perubahan yang dimaksud yang tercatat.
2. Pastikan `versionName` dan `versionCode` cocok dengan judul entry.
3. Pastikan tidak ada entry lain yang berubah tanpa sengaja.
4. Pastikan dokumen topik di `_schedule/AKTIF/<TOPIK>.md` sudah sinkron.

### 4.8 Dilarang Dicatat

1. Aktivitas tool rutin, pencarian, atau pembacaan file.
2. Rencana yang belum atau tidak dikerjakan.
3. Detail rahasia, token, kredensial, atau data pribadi.
4. Berkas yang tidak berubah.
5. Klaim “sesuai referensi” tanpa pemeriksaan.

### 4.9 Sumber Dokumen

1. Sumber catatan topik: `_schedule/AKTIF/<TOPIK>.md`.
2. Jaga isi CHANGELOG dan dokumen topik tetap sinkron.
3. Jika keduanya berbeda, perbaiki sebelum menyatakan pekerjaan selesai.

## 5. Commit, Push, dan Tag

1. Lakukan hanya setelah user memerintahkan.
2. Sebelum commit, periksa `git status`, `git diff`, dan `git log`.
3. Stage hanya file yang dimaksud; jangan commit rahasia.
4. Tulis pesan commit yang ringkas dan sesuai gaya repo.
5. Jangan amend, force-push, atau membuat commit kosong tanpa perintah eksplisit.
6. Jangan melewati hook kecuali user memerintahkan.
7. Tag release hanya setelah entry CHANGELOG berstatus `RELEASED`.
8. Laporkan hasil commit, push, atau tag beserta hash yang relevan.

## 6. Setelah Release

1. Pastikan tag menunjuk commit release yang benar.
2. Pastikan CHANGELOG sudah `***RELEASED***`.
3. Jangan mengubah entry yang sudah release tanpa perintah eksplisit.
4. Perbarui dokumen topik bila masih ada pekerjaan lanjutan.
