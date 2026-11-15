# Frühfunke veröffentlichen (Google Play, automatisiert)

Jede Version wird über den Workflow `.github/workflows/build.yml` („Bauen & veröffentlichen“) gebaut.
Er prüft Signatur, Paketname und Version, lädt auf Wunsch das App-Bundle zu Google Play hoch und legt
APK + AAB als GitHub-Release `v<Version>` ab.

## Feste Werte – niemals ändern

| Was | Wert |
|---|---|
| Paketname | `de.fruehfunke.app` |
| Signaturschlüssel | `fruehfunke-release.jks`, Alias `fruehfunke`, CN=Fruehfunke |
| Zertifikat SHA-256 | `22:BC:F0:36:B9:62:D2:4D:F3:71:CD:33:63:06:F5:CF:06:AE:F8:A2:07:BB:B5:15:AC:5E:8B:90:9F:C0:3A:78` |
| Play App Signing | **nicht angemeldet** (Stand 15.11.2026). Der Workflow lädt dann die mit dem eigenen Schlüssel signierte **APK** hoch – Play-Version und GitHub-APK sind identisch signiert. Falls Google später ein App-Bundle verlangt: Play App Signing **mit eigenem Schlüssel** (PEPK, siehe unten) einrichten, dann lädt der Workflow automatisch das AAB hoch. |
| versionCode | Laufnummer dieses Workflows (`github.run_number`) – Datei `build.yml` deshalb nie umbenennen |
| versionName | `<fruehfunke.version aus gradle.properties>.<versionCode>`, z. B. `1.0.12` |

## Neue Version veröffentlichen

1. Änderungen sind auf `main` gepusht (jeder Push baut automatisch eine APK, ohne Upload).
2. Workflow mit Upload starten:
   - **Claude / API:**
     `gh api -X POST repos/olidoh/fruehfunke/actions/workflows/build.yml/dispatches -f ref=main -f 'inputs[upload]=true' -f 'inputs[track]=internal' -f 'inputs[notes]=Was ist neu'`
   - **Von Hand:** GitHub › Actions › *Bauen & veröffentlichen* › **Run workflow** › Track wählen.
3. Nach ca. 5 Minuten: Release `v1.0.<nr>` auf GitHub, Version im gewählten Track bei Google Play.
   Interne Tester bekommen das Update über den Play Store meist innerhalb weniger Minuten.

Tracks: `internal` = interner Test (bis 100 eingeladene Konten, keine Prüfung), `alpha`/`beta` = geschlossener/offener Test,
`production` = öffentlich. Einen Release aus dem internen Test kann man in der Play Console auch direkt „hochstufen“.

Größerer Versionssprung (z. B. 1.1): `fruehfunke.version` in `gradle.properties` ändern, committen, pushen.

## Einmalige Einrichtung

### 1. App in der Play Console anlegen
Play Console › **App erstellen**: Name „Frühfunke“, Standardsprache Deutsch, App, kostenlos.

### 2. Play App Signing (optional, nur falls Google ein App-Bundle verlangt)
Ohne Play App Signing nimmt Google für Frühfunke die APK an (so läuft es aktuell). Wird ein App-Bundle Pflicht:
Unter *Test und Release › App-Integrität › Play App Signing* bzw. beim nächsten Release (Testen › Interner Test › Neuen Release erstellen) bei „App-Signatur“
**„Signaturschlüssel ändern“ › „Schlüssel aus Java-Schlüsselspeicher exportieren und hochladen“** wählen.
Die Console zeigt einen fertigen Befehl mit `pepk.jar` und dem Verschlüsselungsschlüssel; diesen auf dem PC im Ordner
mit `fruehfunke-release.jks` ausführen (Alias `fruehfunke`, Passwort aus `LIESMICH-Signierschluessel.txt`) und die
erzeugte ZIP-Datei hochladen. Danach zusätzlich das Upload-Zertifikat hochladen bzw. bestätigen, dass derselbe
Schlüssel auch Upload-Schlüssel ist.

### 3. Erstes App-Bundle von Hand hochladen
Google verlangt, dass die allererste Version über die Play Console hochgeladen wird. Dazu das neueste
`fruehfunke-1.0.<nr>.aab` aus den GitHub-Releases nehmen und im internen Test hochladen. Tester-Liste anlegen
(eigene Google-Konten) und Release einführen. Ab dann übernimmt der Workflow.

### 4. Dienstkonto für den Upload
Dasselbe Dienstkonto wie bei easyTDD verwenden: Play Console › **Nutzer und Berechtigungen** › das Dienstkonto
(`…@…iam.gserviceaccount.com`) für die App Frühfunke mit „Releases verwalten“ (Test- und Produktions-Tracks) freigeben.
Dann im Repo `olidoh/fruehfunke` unter Settings › Secrets › Actions das Secret
`PLAY_SERVICE_ACCOUNT_JSON` mit dem kompletten Inhalt der Dienstkonto-JSON anlegen.

### 5. App-Inhalte (Pflichtangaben)
Unter *Richtlinien › App-Inhalte*:
- **Datenschutzerklärung:** Text in `docs/datenschutz.md`, z. B. auf der eigenen Webseite veröffentlichen und URL eintragen.
- **Datensicherheit:** keine Konten, keine Werbung, kein Tracking. Für das Wetter werden die Koordinaten des selbst
  gewählten Orts an Bright Sky (DWD-Daten) und für die Ortssuche der Suchbegriff an Open-Meteo gesendet – als
  „Ungefährer Standort“, Zweck „App-Funktionen“, nicht gespeichert, verschlüsselt übertragen.
- **Exakte Wecker (`USE_EXACT_ALARM`):** Kernfunktion „Wecker“.
- **Vollbild-Benachrichtigungen (`USE_FULL_SCREEN_INTENT`):** Wecker.
- **Vordergrunddienst `mediaPlayback`:** „Spielt Weckton bzw. Internetradio ab, solange der Wecker klingelt“,
  dazu ein kurzes Video (Bildschirmaufnahme: Wecker klingelt, Ton läuft, Stopp).
- Inhaltseinstufung, Zielgruppe (ab 13), Werbung: nein.

Für den internen Test reichen Schritte 1–4; Schritt 5 ist spätestens vor `production` nötig.

## Store-Eintrag
Texte: `play/store-text.md` · Symbol 512×512: `play/icon-512.png` · Feature-Grafik 1024×500: `play/feature-graphic.png`
· Screenshots: auf dem Tablet aufnehmen (mind. 2).

## Typische Fehler

| Meldung | Ursache / Lösung |
|---|---|
| `FALSCHE SIGNATUR` | Secret `FRUEHFUNKE_KEYSTORE_B64` enthält einen anderen Keystore. Sollwert **nicht** ändern. |
| `versionCode … nicht höher` | Bei Google Play liegt schon eine höhere Version → Workflow erneut starten (neue Laufnummer). |
| `For uploading an AppBundle you must be enrolled in Play Signing` | Nur Hinweis: der Workflow weicht automatisch auf die APK aus. Schlägt auch das fehl → Schritt 2. |
| `Package not found` / 404 | App noch nicht angelegt oder erstes Bundle noch nicht von Hand hochgeladen (Schritte 1–3). |
| HTTP 403 / `caller does not have permission` | Dienstkonto in der Play Console nicht für Frühfunke freigegeben (Schritt 4), teils bis 24 h Wartezeit. |
| `PLAY_SERVICE_ACCOUNT_JSON fehlt` | Secret im Repo `olidoh/fruehfunke` anlegen (Schritt 4). |
| Release nur als Entwurf | App in der Play Console noch Entwurf (Pflichtangaben fehlen) → dort fertig einrichten und Release einführen. |
