# Változási Napló (Changelog) – AssiSten-s (korábban Fülelő Hangjegyzet)

## 1.0.4 (versionCode 6) – AssiSten-s

**Átnevezés**
- Az app neve **AssiSten-s** (indítóikon, nyitóképernyő, widget, e-mail-tárgy, APK-név).
  A belső azonosító (`hu.orajegyzet`) és az adatbázis változatlan: a frissítés megtartja az adatokat.

**P1 – Gemma-import javítása**
- A modell mindig rögzített néven kerül a `files/models` mappába (`gemma.task` / `gemma.bin`);
  az eredeti fájlnév csak a beállításokba kerül. Közös modellkeresés: `GemmaModel.file()`.
- Indításkor az 1.0.3 által eredeti néven mentett Gemma automatikusan átnevezésre kerül.
- Az import hibái láthatók (piros szöveg + Snackbar); a `.litertlm` (Gemma 4) érthető üzenettel elutasítva.
- Import után próbabetöltés (120 mp időkorlát); sikertelen betöltésnél a fájl törlődik.
- A modellnév mentése már nem írja felül a titkos mezőket (külön `setGemmaModelName`).

**P2 – Valódi folyamatlépések és forrásjelvény**
- A tréfás, körbeforgó feliratok helyett a valódi lépés látszik (pl. „Leirat: 3/12 darab (Whisper)",
  „Összefoglaló: Gemma (2/4 blokk)"). Új oszlop: `notes.progress` (Room-migráció 4 → 5).
- Minden jegyzeten jelvény: „📱 Gemma" / „☁ Gemini" / „Kivonat (nem AI)" / „Offline vázlat";
  figyelmeztetésnél sárga sáv „Összefoglaló újra" gombbal (`summaryEngine`, `summaryWarning`).

**P3 – Projektjelentések offline is**
- Vezetői összefoglaló, Státuszjelentés és QA-audit kódban összeállított tényblokkból (`ReportBuilder`).
- Útválasztás: adatvédelmi zár → Gemma; Gemini csak engedélyezett módban ÉS tényleges hálózattal,
  hibánál Gemma; modell nélkül „Offline vázlat" sablon. A prompt soha nem kerül a kivonatolóba.
- „Újrafogalmazás Geminivel" gomb, ha a jelentés offline készült és most van hálózat.

**P4 – Stílusos összefoglaló hosszú leiratnál**
- A 2048 tokenes keretbe nem férő szöveget blokkonként foglalja össze (map-reduce), a Gemma
  egyszer töltődik be, blokkonként 120 mp, összesen 10 perc időkorláttal; a meleg Whisper előtte elenged.
- A stílusgombok is ellenőrzik a hálózatot, és Gemini-hibánál Gemmára esnek vissza.

**P5 – Vosk eltávolítása**
- Kikerült a `vosk-android` és a JNA függőség, a Vosk-import és -ág; a régi Vosk-modell indításkor törlődik.

**P6 – Iskolai mód kapcsoló**
- Beállítások → „Iskolai mód (órarend, csengetés, widget)". Kikapcsolva az órarend rejtve,
  a csengetések törölve, a widget letiltva; az adatok megmaradnak. Frissítéskor bekapcsolt, ha van felvitt óra.

## [1.0.2] - 2026-03-24
### Hozzáadva & Fejlesztve
- **Szoftveres biztonság és titkosítás**: Android KeyStore AES/GCM/NoPadding titkosítás bevezetése a Gemini API-kulcsokhoz és az SMTP-jelszavakhoz (`SecretCipher`).
- **Adatbázis migráció és sémaexport**: Room sémaexport engedélyezése (`3.json`) és `.fallbackToDestructiveMigration()` eltávolítása az adatvesztés megelőzésére.
- **Hálózati biztonság**: API-kulcs eltávolítása az URL-ekből, küldése biztonságos `x-goog-api-key` fejlécben.
- **ANR és szálkezelés javítása**: A felvételindítás nem blokkolja a fő szálat; azonnali előtér-szolgáltatási értesítés induláskor (`RecordingService`).
- **16 KB-os lapméret-igazítás (Android 15+)**: C++ CMake linkbeállítások frissítése (`-Wl,-z,max-page-size=16384`) a natív Whisper library-khez.
- **Felületi szerkesztés**: Döntések, kockázatok és Gantt WBS feladatok szerkesztési lehetősége ✏️ ikonokkal. Összecsukható projekt kártyák a tisztább felületért.
- **Hibakezelés**: Korrigált HTTP 400/401/403/429 hibakezelés és méretellenőrzés a felesleges fájlfeltöltések elkerülésére.
