# Agent Rules — Pendamping AGENTS.md

Dokumen ini adalah pendamping operasional `AGENTS.md`. `AGENTS.md` tetap menjadi aturan utama; dokumen ini mengatur perilaku kerja, gate kabar progres, urutan pelaporan, penerusan temuan subagent, dan self-check sebelum menyatakan selesai.

Baca `AGENTS.md` lebih dulu, lalu dokumen ini, lalu `agent-workflow.md` di awal sesi. Aturan alur kerja, CHANGELOG, dan versioning ada di `agent-workflow.md`. Aturan commit, tag, push, build, environment, dan tindakan destruktif tetap mengikuti `AGENTS.md`.

## 0. Kedudukan Dokumen

1. `AGENTS.md` adalah sumber aturan utama.
2. `agent-rules.md` adalah aturan perilaku kerja dan gate kabar progres.
3. `agent-workflow.md` adalah aturan alur kerja, CHANGELOG, dan versioning.
4. Jika terjadi perbedaan, pilih ketentuan yang lebih ketat.
5. Dokumen ini tidak menggantikan kewajiban perintah eksplisit, konfirmasi tindakan berisiko, atau larangan build/commit tanpa perintah.
6. Jika aturan berubah, `AGENTS.md`, `agent-rules.md`, dan `agent-workflow.md` harus diperbarui agar tidak saling bertentangan.

## 1. Perilaku Kerja

1. Lakukan perubahan minimal dan fokus; jangan menyentuh file di luar scope.
2. Jangan refactor atau audit project tanpa diminta; jangan membuat daftar "Next Steps" lalu mengeksekusinya sendiri.
3. Jangan revert tanpa konfirmasi.
4. Jangan jalankan, tawarkan, atau minta konfirmasi build kecuali user memberi perintah eksplisit.
5. Tanya hanya jika tidak bisa ditebak dan berdampak. Hal kecil yang bisa ditafsirkan mengikuti pola existing, lalu sebutkan asumsinya.
6. Diskusi yang belum selesai bukan perintah untuk bekerja.
7. Selesai sesuai perintah, berhenti. Jangan menutup jawaban dengan tawaran tindakan atau pertanyaan balik yang tidak diminta.
8. Tindakan destruktif/berisiko, commit, tag, dan push tetap mengikuti `AGENTS.md`; laporan progres bukan pengganti konfirmasi.
9. Lakukan self-check sebelum menyatakan selesai, lewat `git diff` atau pemeriksaan file sesuai permintaan.

## 2. Prinsip Pelaporan

1. Laporan progres adalah bagian dari pekerjaan, bukan efek samping pekerjaan.
2. Ukuran “perlu dilaporkan” adalah pengetahuan user, bukan pengetahuan agent. Informasi yang sudah diketahui agent belum tentu diketahui user.
3. Laporan bukan narasi tiap tool call. Laporkan hanya hal yang mengubah pemahaman, arah, atau risiko.
4. Laporan bukan izin. Aturan build, commit, push, tag, dan tindakan destruktif tetap berlaku terpisah.
5. Jika ragu antara melapor atau diam, pilih melapor sebelum mengedit.

## 3. Pemicu Wajib

Laporan wajib dikirim sebelum agent melanjutkan, pada kondisi berikut:

1. Sebelum tool edit pertama pada tugas baru.
2. Setelah analisis atau dekompilasi, sebelum mengubah kode.
3. Sebelum menyentuh lebih dari dua file.
4. Sebelum menyentuh lebih dari satu layer, misalnya XML layout, Java, dan dokumen.
5. Sebelum menimpa logika lama atau mengubah perilaku yang sudah ada.
6. Saat menemukan asumsi yang salah, API/kelas/konstanta yang tidak ada, atau pola modul yang berbeda.
7. Saat verifikasi berhasil, gagal, atau tidak bisa dijalankan.
8. Saat menerima temuan subagent yang memengaruhi rencana.
9. Saat pekerjaan terblokir atau membutuhkan keputusan berdampak.
10. Saat berpindah dari analisis ke aset, layout, wiring Java, dokumen, atau verifikasi.

Pemicu ini menggantikan istilah “tahap baru” yang terlalu longgar.

## 4. Isi Laporan Minimum

Laporan tidak perlu panjang. Minimal memuat tiga hal secara berurutan:

1. Keadaan atau temuan saat ini.
2. Dampak atau risiko bila pekerjaan dilanjutkan.
3. Tindakan berikutnya, termasuk file atau cakupan yang akan disentuh.

Tulis sebagai kalimat manusiawi, bukan template kaku dan bukan narasi tiap tindakan. Jangan memakai label pembuka seperti “Sitrep”, “Status”, “Progress Update”, atau “Kabar:”.

Untuk perubahan multi-file, sebutkan daftar file dan alasan masing-masing.

Untuk tugas visual, tambahkan:

1. Elemen referensi yang akan ditiru.
2. Elemen referensi yang sengaja tidak ditiru dan alasannya.
3. Kriteria hasil yang bisa diperiksa, bukan sekadar “dibuat lebih mirip”.

## 5. Gate Sebelum Edit

Sebelum memanggil tool edit pertama, agent wajib sudah menyampaikan:

1. Pemahaman perintah.
2. Kondisi awal yang relevan.
3. Rencana perubahan.
4. File atau layer yang akan disentuh.
5. Risiko regresi yang diketahui.

Tanpa laporan itu, agent belum boleh mengedit.

Jika perubahan menyentuh lebih dari dua file atau lebih dari satu layer, agent wajib melaporkan rencana terlebih dahulu. Bila perubahan tergolong berisiko atau destruktif menurut `AGENTS.md`, laporan tidak menggantikan konfirmasi eksplisit.

## 6. Integrasi ke Alur Kerja

Alur edit biasa harus menjadi:

1. Baca konteks, `git status`, dan `git log` bila relevan.
2. Kirim laporan temuan dan rencana.
3. Edit kode atau aset.
4. Verifikasi.
5. Kirim laporan hasil verifikasi.
6. Rapikan CHANGELOG dan versionCode sesuai `agent-workflow.md`.
7. Lakukan self-check sebelum jawaban akhir.

Tanpa langkah 2, jangan masuk ke langkah 3.

Alur rinci untuk edit, pre-release, commit, push, tag, dan release ada di `agent-workflow.md`. Aturan perilaku dan gate lapor tetap di dokumen ini. Aturan yang hanya berada di pembuka mudah ditinggalkan saat eksekusi, jadi gate lapor harus muncul di urutan kerja.

## 7. Aturan untuk Tugas Visual

Sebelum mengubah layout yang meniru referensi:

1. Inventaris elemen referensi: struktur, kartu, action, label, dan perilaku klik.
2. Petakan elemen referensi ke komponen FTxT.
3. Sebutkan elemen yang tidak bisa ditiru karena data, izin, atau modul belum tersedia.
4. Tetapkan kriteria hasil: hierarki, jarak, aksi, keadaan kosong, dan perilaku klik.
5. Laporkan inventaris dan pemetaan sebelum mengedit.

Setelah mengedit:

1. Jangan menyatakan “sesuai referensi” hanya karena XML valid.
2. Bedakan validasi struktur dari validasi visual dan UX.
3. Sebutkan apa yang belum diverifikasi, misalnya tampilan pada perangkat atau build.
4. Jika build tidak dijalankan, katakan demikian.

## 8. Aturan Multi-File dan Perubahan Berisiko

Perubahan pada lebih dari dua file atau lintas layer dianggap perubahan besar untuk keperluan pelaporan.

Sebelum perubahan seperti itu, agent wajib menyebut:

1. File yang akan diubah.
2. Perilaku lama yang bisa berubah.
3. Bagian yang dipertahankan.
4. Cara verifikasi.
5. Risiko yang belum bisa diuji.

Untuk tindakan destruktif atau berisiko, konfirmasi eksplisit tetap wajib. Laporan progres bukan pengganti konfirmasi.

## 9. Aturan Subagent

1. Laporan subagent adalah bukti internal, bukan laporan ke user.
2. Temuan subagent yang memengaruhi rencana wajib diringkas ke user sebelum edit berikutnya.
3. Ringkasan cukup memuat temuan, dampak, dan keputusan. Tidak perlu menyalin seluruh laporan.
4. Prompt subagent wajib meminta path dan nomor baris bila riset menyentuh kode.
5. Subagent yang hanya boleh riset harus dilarang mengedit.
6. Keberhasilan subagent tidak membuktikan sesi utama sudah melapor.

## 10. Self-Check Sebelum Jawaban Akhir

Sebelum menyatakan pekerjaan selesai, agent wajib bisa menjawab:

1. Apa yang ditemukan?
2. Apa yang diubah, termasuk file dan perilaku yang terkena?
3. Risiko atau regresi apa yang mungkin terjadi?
4. Verifikasi apa yang sudah dijalankan, dan apa yang belum?
5. Apakah user sudah mengetahui temuan, keputusan, file yang disentuh, dan risiko tersebut?

Jika ada jawaban “belum”, agent belum boleh menyatakan selesai.

## 11. Contoh Laporan yang Benar

Contoh untuk tugas visual:

> Referensi memakai empat ubin ringkasan dan action teks di setiap kartu. Di FTxT, data izin dan installer belum punya halaman sendiri, jadi aksi izin akan memakai dialog ringkas dan aksi instal memakai pemilih berkas. Saya akan mengubah layout dashboard dan wiring di `AppManagerActivity`, lalu memvalidasi XML. Risiko: filter daftar aplikasi bisa ikut berubah saat action kartu dipakai.

Contoh untuk temuan yang membatalkan asumsi:

> Kelas `AppListAdapter.FILTER_*` tidak ada, jadi rencana memakai konstanta itu akan gagal kompilasi. Saya akan memakai konstanta `F_ALL` sampai `F_64` yang sudah ada dan mengembalikan perilaku `AppShareHelper` serta reload `onResume`. Dampaknya hanya pada wiring filter, bukan pada layout.

## 12. Contoh Laporan yang Salah

1. “Saya lanjut memperbaiki UI” lalu langsung mengedit banyak file.
2. Mengerjakan hasil riset subagent tanpa meringkas temuannya ke user.
3. Menyebut “sudah selesai” setelah `XML_OK`, padahal tampilan belum diperiksa.
4. Menunda semua laporan sampai jawaban akhir.
5. Menilai informasi “tidak perlu dilaporkan” karena agent sudah mengetahuinya.

## 13. Larangan

1. Jangan memanggil tool edit pertama sebelum gate laporan.
2. Jangan menganggap laporan subagent sebagai laporan user.
3. Jangan memakai alasan “bukan informasi baru bagi saya” untuk tidak melapor.
4. Jangan menimbun temuan sampai akhir.
5. Jangan menyatakan hasil visual sesuai referensi tanpa bukti pemeriksaan.
6. Jangan memakai laporan sebagai pengganti konfirmasi tindakan berisiko.
7. Jangan mengabaikan pemicu hanya karena perubahan terlihat kecil.

## 14. Penegasan Penilaian

Laporan dinilai dari sudut pandang user:

> Laporkan bila user belum mengetahui informasi ini, atau bila informasi ini mengubah keputusan, ekspektasi, atau pemahaman user. Jika user sudah mengetahuinya dan tidak ada arah yang berubah, lanjutkan kerja.

Bagian “hindari laporan sepele” ditafsirkan sebagai berikut:

> Jangan laporkan pencarian, pembacaan file, atau tindakan rutin. Tetapi temuan, perubahan arah, risiko regresi, kegagalan verifikasi, dan perubahan multi-file wajib dilaporkan.

## 15. Batas dan Hubungan dengan Dokumen Lain

Dokumen ini tidak mengubah:

1. Kewajiban perintah eksplisit sebelum eksekusi.
2. Larangan commit, tag, push, atau build tanpa perintah.
3. Kewajiban konfirmasi untuk tindakan destruktif atau berisiko.
4. Aturan environment dan prosedur build di `AGENTS.md`.
5. Aturan CHANGELOG, versionCode, dan alur release di `agent-workflow.md`.

Dokumen ini sengaja tidak menduplikasi seluruh `AGENTS.md`. Untuk hal di luar perilaku kerja dan pelaporan progres, rujuk dokumen terkait. Bila aturan berubah, perbarui ketiga dokumen agar tidak ada dua kebenaran yang berbeda.
