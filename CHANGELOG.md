# Változási Napló (Changelog) – AssiSten-s (korábban Fülelő Hangjegyzet)

## 1.1.2 (versionCode 9) – Enyhébb, de név- és számérzékeny ellenőrzés

- **Hibajavítás:** a Gemma-összefoglaló gyakran elveszett a „hallucináció-szűrő” miatt. A régi szűrő a szavak 60%-os egyezését várta a leirattal, ami átfogalmazó összefoglalónál (és Whisper-elírásoknál) túl szigorú.
- Új egyezésvizsgálat (`Coherence`):
  - 20% alatt: a válasz elvetve, kivonat készül (a figyelmeztetés megmutatja a pontos egyezést);
  - 20–35% között: a Gemma szövege **megmarad**, sárga figyelmeztetéssel és az egyezés százalékával;
  - külön jelzés, ha az összefoglalóban **a leiratban nem szereplő név vagy szám** van (a lista- és táblázatsorszámok nem számítanak).
- A figyelmeztető kártyán új **„Kivonat”** gomb: egy érintéssel látható a nem AI-s kivonat is.
- A projektjelentések Gemma-ága ugyanezt a logikát használja.

## 1.1.1 (versionCode 8) – Valóban eltérő összefoglaló-típusok, átlátható Gemma-beállítás

- **Hibajavítás – egyforma összefoglalók:** a „Vezetői / Jegyzőkönyv / Teendők / Részletes jegyzet” gombok eddig ugyanazt a JSON-alapú, 8–12 mondatos jegyzetet kérték (Geminin), illetve ugyanazt a „foglald össze N mondatban” utasítást (Gemmán). Mostantól minden típus saját szerkezetet ír elő (`SummaryStyles`):
  - Vezetői: ≤150 szó, Lényeg / Döntések / Kockázatok / Következő lépés;
  - Jegyzőkönyv: Tárgy, Résztvevők, Napirendi pontok, Elhangzottak, Határozatok, Felelősök és határidők;
  - Teendők: táblázat (Feladat | Felelős | Határidő);
  - Részletes jegyzet: témakörönkénti alcímek + Kulcsfogalmak.
- Gemini: új `generateFreeText` hívás kötött JSON-séma nélkül, így a kért szerkezet megmarad (a projektjelentések is ezt használják).
- Gemma: hosszú leiratnál a blokkokból előbb tények gyűjtése, majd a kért stílusban egyetlen végső összefoglaló; a szerkezet szavai (pl. „Határozatok”) nem számítanak hallucinációnak.
- **Beállítások → Feldolgozás:** új „Jelenleg: …” sor mutatja, ténylegesen melyik motor készíti az összefoglalót (és miért). A Gemma-kapcsoló felirata egyértelműbb.
- A Gemma ismétlés-szűrője megőrzi a sortöréseket, így a címsoros, felsorolásos és táblázatos válasz nem esik szét egyetlen bekezdéssé (projektjelentéseknél is).
- Stílusos Gemma-összefoglalónál nagyobb hely marad a válasznak (500 helyett 1200 token), hogy a jegyzőkönyv és a részletes jegyzet ne szakadjon el.
- A jegyzetmegjelenítő táblázatot is rajzol (a „Teendők” típus táblázata nem nyers `|` jelekkel látszik).
- Ha a „Gemma 4 használata” ki van kapcsolva, a telepített modell már nem kényszeríti offline kivonatra a feldolgozást – a Gemini fut.

## 1.1.0 (versionCode 7) – Gemma 4 a LiteRT-LM motoron

- **Motorcsere:** a MediaPipe (`tasks-genai`) helyett a LiteRT-LM (`litertlm-android:0.11.0`, ugyanaz, mint a Google AI Edge Gallery 1.0.20-ban). Így fut a **Gemma 4 E2B** (`gemma-4-E2B-it.litertlm`, 2,59 GB).
- Gyorsító: először GPU, hiba esetén automatikusan CPU; a ténylegesen használt gyorsító látszik az import és a modellteszt eredményében.
- Tokenkeret 2048 → 4000 (a Gallery Gemma 4-beállítása), így ritkábban kell blokkosítani.
- A modell rögzített neve: `files/models/gemma.litertlm`. Indításkor a régi néven (pl. `gemma.task`) mentett, de valójában `.litertlm` tartalmú fájl **automatikusan átneveződik** – a korábban betöltött Gemma 4 újraimportálás nélkül működik.
- Az import csak `.litertlm` fájlt fogad (fejléc: `LITERTLM`), ellenőrzi a szabad helyet és a teljes átmásolást; a régi `.task` fájlra érthető üzenetet ad.
- **Alap elemző választó** (Beállítások → Feldolgozás): Gemma 4 (helyben, alapértelmezett) / Gemini (felhő). Gemma-alapnál a Gemini csak akkor fut, ha nincs telepített Gemma-modell, vagy kérésre („Újrafogalmazás Geminivel”).
- `minSdk` 26 → 31 (Android 12+), a LiteRT-LM igénye.

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
