# Változási Napló (Changelog) – Fülelő Hangjegyzet

## [1.0.2] - 2026-03-24
### Hozzáadva & Fejlesztve
- **Szoftveres biztonság és titkosítás**: Android KeyStore AES/GCM/NoPadding titkosítás bevezetése a Gemini API-kulcsokhoz és az SMTP-jelszavakhoz (`SecretCipher`).
- **Adatbázis migráció és sémaexport**: Room sémaexport engedélyezése (`3.json`) és `.fallbackToDestructiveMigration()` eltávolítása az adatvesztés megelőzésére.
- **Hálózati biztonság**: API-kulcs eltávolítása az URL-ekből, küldése biztonságos `x-goog-api-key` fejlécben.
- **ANR és szálkezelés javítása**: A felvételindítás nem blokkolja a fő szálat; azonnali előtér-szolgáltatási értesítés induláskor (`RecordingService`).
- **16 KB-os lapméret-igazítás (Android 15+)**: C++ CMake linkbeállítások frissítése (`-Wl,-z,max-page-size=16384`) a natív Whisper library-khez.
- **Felületi szerkesztés**: Döntések, kockázatok és Gantt WBS feladatok szerkesztési lehetősége ✏️ ikonokkal. Összecsukható projekt kártyák a tisztább felületért.
- **Hibakezelés**: Korrigált HTTP 400/401/403/429 hibakezelés és méretellenőrzés a felesleges fájlfeltöltések elkerülésére.
