Aku sudah memeriksa kedua file database yang tersedia, lalu mencocokkan hasilnya dengan kode sumber FTxT di branch `main`.

Ada beberapa temuan yang cukup serius. Yang paling penting: data memang menunjukkan anomali baterai yang berulang, tetapi ada juga kelemahan pada metode estimasi FTxT yang bisa membuat angka kesehatan baterai kurang dapat dipercaya.

Aku menggunakan `battery_history.db` sebagai database terbaru karena datanya berlanjut sampai 4 November 2026 pukul 10:28 WITA. File `battery_history (2).db` lebih lama dan berakhir pada 3 November.

### 1\. Kondisi database

### 110.692

Total sampel baterai

### 515

Sesi pengisian

### 495

Sesi pengosongan

### 7 hari

Rentang data terbaru

Integritas SQLite

OK

Interval median sampel 5,046 detik

Sampel dengan cycle count 0

Database tidak menunjukkan kerusakan SQLite. Sampling juga cukup konsisten, meskipun ada 100 jeda lebih dari 30 detik dan beberapa jeda sangat panjang.

Satu keterbatasan: FTxT tidak memperoleh informasi jumlah siklus baterai dari perangkatmu. Jadi, kita tidak bisa menggunakan cycle count untuk mengukur usia pemakaian baterai.

### 2\. Kejadian persentase jatuh mendadak memang nyata

Ini bukan sekadar kesan dari grafik. Ada beberapa kejadian yang terekam langsung.

| Waktu WITA   | Perubahan persentase                      | Tegangan        |
| ------------ | ----------------------------------------- | --------------- |
| 30 Okt 15:23 | 42% → 0%                                  | 3,219 → 3,200 V |
| 1 Nov 05:59  | 29% → 60% saat pengisian                  | 3,173 → 4,491 V |
| 2 Nov 01:21  | 66% tetap, tegangan pulih setelah dicolok | 3,507 → 4,462 V |
| 4 Nov 10:28  | 56% → 0%                                  | 3,230 → 3,260 V |

Kejadian 4 November sangat menarik:

* 10:26:42 — 57%, 3,184 V, arus −908 mA.

* 10:27:32 — 57%, 3,291 V, arus −627 mA.

* 10:28:25 — 57%, 3,107 V, arus −863 mA.

* 10:28:40 — 56%, 3,230 V, arus −617 mA.

* 10:28:46 — 0%, 3,260 V, arus −768 mA.

Dalam sekitar dua menit, persentase hanya turun satu angka, kemudian langsung menjadi nol. Tidak ada perubahan tegangan yang sebanding dengan penurunan 56 poin tersebut.

Ini memperkuat dugaan adanya masalah pada estimasi tingkat pengisian (state of charge) atau sistem pengukuran/pengelolaan baterai. Tegangan terakhir yang tercatat bukan jaminan tegangan persis pada saat perangkat mati, tetapi pola ini tetap abnormal.

### 3\. Tegangan baterai menunjukkan perubahan yang tidak biasa

Aku menemukan perubahan tegangan yang cukup besar bahkan ketika arus pengosongan masih berada di kisaran ratusan mA hingga sekitar 1 A.

Contoh pada 4 November:

* 10:28:25 — 3,107 V pada −863 mA.

* 10:28:30 — 3,107 V pada −794 mA.

* 10:28:35 — 3,292 V pada −608 mA.

Tegangan berubah sekitar 185 mV dalam lima detik. Perubahan beban memang bisa menyebabkan tegangan baterai berubah, tetapi variasi sebesar itu dalam kondisi arus yang relatif berdekatan patut diperiksa.

Ini cocok dengan alasan kita ingin membuat Voltage Stability Monitor. Kita perlu mengamati tegangan, arus, dan waktu secara bersamaan, bukan hanya membaca tegangan sesaat.

Namun, aku tidak akan langsung mengubah angka tersebut menjadi nilai resistansi internal baterai. Pengukuran ini belum terkontrol dan belum cukup untuk menghasilkan nilai resistansi yang dapat dipercaya.

### 4\. Estimasi kapasitas: ada dua hasil berbeda

Dari tabel `discharge_sessions`, terdapat 430 sesi yang ditandai valid.

Estimasi kapasitas sesi pengosongan yang tersimpan

### 1.611 mAh

Median dari 430 sesi valid

Kapasitas desain 5.000 mAh

Rasio terhadap desain 32,2%

Rentang kuartil 1.527–1.724 mAh

Tetapi ketika aku merekonstruksi metode estimasi utama FTxT dari sampel mentah tujuh hari, hasilnya berbeda:

| Jenis sesi  | Median estimasi |
| ----------- | --------------- |
| Pengisian   | 1.144 mAh       |
| Pengosongan | 2.223 mAh       |
| Gabungan    | 1.454 mAh       |

Angka tersebut merupakan reproduksi algoritma dari data mentah, bukan hasil pengukuran kapasitas laboratorium.

Perbedaan ini penting. FTxT memang menghitung median dari segmen pengisian dan pengosongan, tetapi hasil dari kedua arah ternyata cukup jauh berbeda.

### 5\. Aku menemukan kelemahan pada algoritma estimasi FTxT

File yang diperiksa: `SessionSegmentBuilder.java` dan `BatteryCapacityEstimator.java`.

Ada tiga hal yang perlu diperhatikan.

A. Segmen ditentukan dari perubahan persentase, bukan batas status charger.

`SessionSegmentBuilder` menentukan arah berdasarkan perubahan persentase dalam jendela 10 menit. Metode rekonstruksi ini tidak menggunakan status `plugged` untuk membatasi segmen. GitHub

Akibatnya, ketika persentase mengalami lonjakan atau penurunan abnormal, segmentasi bisa menganggapnya sebagai perubahan kapasitas normal.

B. Integrasi arus menggunakan nilai absolut.

Rumusnya:

mAh = |I| \\times \\frac{\\Delta t}{3.600.000}

Ini berarti arus positif maupun negatif sama-sama dihitung sebagai energi yang melewati baterai. GitHub +1

Untuk segmen pengisian, seharusnya arus pengisian yang dihitung. Untuk segmen pengosongan, seharusnya arus pengosongan. Jika kedua arah tercampur dalam satu segmen, estimasi bisa bias.

C. Filter validasi belum cukup untuk menolak semua kejadian abnormal.

Estimator mensyaratkan perubahan persentase minimal 5%, durasi minimal satu menit, dan hasil estimasi antara 500–30.000 mAh. GitHub

Masalahnya, penurunan persentase abnormal yang berlangsung lebih dari satu menit tetap bisa lolos filter.

Contoh paling jelas adalah segmen pengosongan pada 4 November yang direkonstruksi dari 100% hingga 0%, padahal penurunan terakhir dari 56% ke 0% terjadi mendadak. Segmen ini menghasilkan estimasi sekitar 530 mAh.

Jadi, angka kapasitas FTxT bukan berarti salah seluruhnya, tetapi algoritmanya belum cukup tahan terhadap gejala baterai yang sedang kamu alami.

### 6\. Charge counter ternyata hampir tidak memberikan informasi independen

Ini salah satu temuan paling menarik.

Di database, nilai `charge_mah` hampir selalu sama dengan:

\\text{charge counter} \\approx \\text{persentase} \\times 50

Contohnya:

| Persentase | Charge counter |
| ---------- | -------------- |
| 100%       | 5.000 mAh      |
| 80%        | 4.000 mAh      |
| 60%        | 3.000 mAh      |
| 56%        | 2.800 mAh      |
| 40%        | 2.000 mAh      |

Korelasi keduanya hampir sempurna, sekitar 0,9999996.

Kode FTxT memang membaca `BATTERY_PROPERTY_CHARGE_COUNTER` secara langsung. Jadi, ini bukan kesalahan konversi yang terlihat pada kode aplikasi. Kemungkinan besar nilai yang diberikan sistem perangkat sudah sangat terikat pada persentase baterai, atau dibulatkan dengan cara tertentu. GitHub

Konsekuensinya: kita tidak bisa memperlakukan charge counter tersebut sebagai pengukuran independen untuk membuktikan kapasitas aktual baterai.

### 7\. Kesimpulan pemeriksaan

Perlu pemeriksaan perangkat

Dari keseluruhan data, penilaianku:

* Indikator persentase tidak dapat diandalkan. Ada beberapa penurunan dan kenaikan ekstrem yang tercatat.

* Tegangan menunjukkan perilaku tidak stabil. Ada variasi besar dalam interval yang sangat singkat.

* Kapasitas aktual kemungkinan sudah sangat menurun. Estimasi sesi pengosongan berkumpul di sekitar 1.611 mAh, jauh di bawah desain 5.000 mAh. Namun, ini bukan hasil pengukuran kapasitas fisik yang terkalibrasi.

* Algoritma estimasi FTxT perlu diperbaiki. Khususnya pemisahan arah arus dan validasi segmen abnormal.

* Tidak ada bukti cycle count yang bisa digunakan.

Menurutku, ada dua masalah yang perlu dipisahkan: kondisi fisik baterai dan akurasi fuel gauge. Keduanya bisa terjadi bersamaan.

Karena perangkatmu sudah mati ketika indikator masih menunjukkan 56%, sebaiknya jangan sengaja mengulang pengosongan sampai perangkat mati untuk mengumpulkan data. Aku menyarankan pemeriksaan teknisi dan penilaian penggantian baterai. Jika perangkat terasa sangat panas, casing berubah bentuk, atau baterai terlihat menggembung, hentikan penggunaan dan pengisian daya.

### Apa yang akan aku periksa berikutnya?

Aku ingin memisahkan dua pekerjaan:

* Analisis baterai: mencari hubungan antara tegangan, arus, persentase, dan waktu tepat sebelum setiap kejadian mati mendadak.

* Audit kode FTxT: memeriksa apakah estimator, `DischargeTracker`, dan grafik historis memperlakukan data anomali secara benar.

Menurutku, audit kode juga penting karena kita tidak ingin membangun fitur Voltage Stability Monitor di atas asumsi pengukuran yang keliru. Aku akan mempertahankan data asli dan tidak mengubah database atau kode proyekmu.
