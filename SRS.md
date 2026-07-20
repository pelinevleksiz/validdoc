# Software Requirements Specification (SRS)

**Project Name:** validdoc

---

## 1. Functional Requirements

### 1.1 User Authentication & Role-Based Access
- Kimlik doğrulama **JWT tabanlı ve oturumsuz** olarak sağlanır; iki rol tanımlıdır: `admin`, `operator`. Token ömrü **10 dakikadır**, refresh mekanizması içermez.
- Hesaplar yalnızca bir admin tarafından oluşturulur; herkese açık kayıt desteklenmez. İlk kurulumda sistem otomatik bir admin hesabı oluşturur.
- Şifreler **BCrypt** ile saklanır; başarısız giriş denemeleri IP başına sınırlandırılır.

### 1.2 Document Upload & Management
- PDF (**çok sayfa destekli**), PNG ve JPEG formatları kabul edilir; dosyalar diske yazılmadan bellekte işlenir.
- Her yükleme zorunlu olarak bir **template**'e bağlanır; template-free doğrulama desteklenmez.

### 1.3 Template-Based Segmentation & Rule-Based Validation
- Admin, template üzerinde **sayfa ve koordinat bazlı segmentler** tanımlar; her segmente sistemin sunduğu sabit kataloglardan bir veya birden fazla kural atanır.
- Kural kataloğu iki gruba ayrılır: *yapısal* (harf/rakam/uzunluk/tarih/imza-kaşe) ve *doğrulanmış format* (T.C. Kimlik No, VKN — checksum'lı, Telefon, E-posta).
- Her segment **dolu-geçerli / dolu-geçersiz / boş** olarak değerlendirilir; sonuç segment bazında raporlanır, tek bir toplu skor kullanılmaz.
- **Template'ler kaydedildikten sonra değiştirilemez**; düzeltme yeni bir template oluşturularak yapılır.
- Admin, kaydetmeden önce örnek belge üzerinde segment koordinatlarını **önizleyebilir**.

### 1.4 Workflow & Approval Management
- Belge statüsü segment sonuçlarından **deterministik** olarak türetilir: hepsi geçerliyse `VALIDATED`, hepsi boşsa `REJECTED_EMPTY`, karışıksa `REJECTED_INVALID` atanır.
- `PENDING_REVIEW` yalnızca motor hatalarında (bozuk dosya, sayfa uyumsuzluğu) kullanılır; skor belirsizliğine dayalı bir ara durum bulunmaz.
- Operatör, otomatik sonuçtan bağımsız olarak her belgeyi manuel onaylayabilir veya reddedebilir.

### 1.5 Multi-Language Support (Turkish / English)
- API hata ve geri bildirim mesajları `Accept-Language` header'ına göre TR/EN sunulur.
- OCR tarama dili, arayüz dilinden bağımsız olarak upload anında ayrı bir parametreyle belirlenir.

---

## 2. Technical & Architectural Requirements

### 2.1 Backend Architecture
Uygulama Spring Boot 4.x ve Java 21 ile geliştirilir; **stateless** container olarak paketlenir ve yatay ölçeklenebilir.

### 2.2 OCR Engine Integration
OCR işlemleri için Tesseract (Tess4J) lokal olarak entegre edilir; segment koordinatları kırpılarak okunur.

### 2.3 Database & Persistence
- Veri katmanında PostgreSQL ve Spring Data JPA kullanılır; yüklenen dosyalar yalnızca bellekte işlenir, kalıcı olarak saklanmaz.
- İşlem tamamlandığında yalnızca sonuç (statü, segment raporu, zaman damgaları) kalıcı hale gelir; **saklama süresi** dolduğunda otomatik olarak silinir.

---

## 3. Non-Functional Requirements

### 3.1 Security & Compliance
- Belgeden çıkarılan kişisel veriler **maskelenerek AES-256-GCM ile şifreli** saklanır; konfigüre edilebilir bir süre sonunda silinir.
- Her kullanıcı işlemi değiştirilemez bir **audit log**'a kaydedilir.

### 3.2 Performance & Scalability
- Uygulama stateless container olarak çoğaltılabilir şekilde tasarlanır.
- Tek sayfalık standart bir belgenin uçtan uca işlenmesi **3 saniyenin altında** tamamlanır; yükleme isteği hemen döner, işleme arka planda asenkron yürütülür.
- Kimlik doğrulaması gerektirmeyen bir **health-check** endpoint'i sağlanır.