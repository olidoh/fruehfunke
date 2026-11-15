# Frühfunke

Eigener Android-Wecker für Android 16, als Nachbau des eingestellten AlarmDroid 2.5.10,
mit neuem Material-You-Design. Ohne Werbung, ohne Tracking, ohne Entwickler-Server.

## Installieren
1. Unter **Releases** die neueste `fruehfunke-<nr>.apk` auf das Tablet laden.
2. Öffnen und Installation aus dieser Quelle erlauben.
3. Beim ersten Start Benachrichtigungen erlauben; die App zeigt an, falls noch etwas fehlt
   (Vollbild beim Wecken, Akku-Optimierung).

Neue Builds lassen sich direkt über die alte Version installieren, die Wecker bleiben erhalten.

## Bauen
Jeder Push auf `main` baut per GitHub Actions eine signierte APK und legt sie als Release ab.
Benötigte Secrets: `FRUEHFUNKE_KEYSTORE_B64` (Keystore, base64) und `FRUEHFUNKE_KEYSTORE_PASSWORD`.

## Stand
- [x] Schritt 2: Kern-Wecker – wiederholend (Wochentage, gerade/ungerade KW), einmalig mit Datum,
      Countdown, Überspringen, Duplizieren, Schlummern (Dauer, Höchstzahl), Klingeldauer,
      ansteigende Lautstärke, eigene Audiodateien, Vibration, Weckbildschirm auf dem Sperrbildschirm,
      Direct Boot (klingelt auch nach Neustart vor dem Entsperren)
- [x] Internetradio als Weckton: Sender mit Stream-URL (auch .m3u/.pls/.m3u8) speichern, probehören,
      pro Wecker auswählen; ohne Netz oder bei Abbruch klingelt automatisch der Ersatz-Weckton
- [x] Export/Import aller Wecker und Sender als JSON (z. B. OneDrive), Ersetzen oder Ergänzen,
      optional automatische Sicherung in die Exportdatei bei jeder Änderung
- [x] Schritt 3: Steuerung beim Klingeln – Umdrehen, Schütteln (einstellbar, mit Sensortest), Power-Knopf,
      Annähern, Lautstärketasten (Lautstärke/Sperren/Schlummern/Stopp), Wischen oder lange gedrückte Tasten
- [x] Schritt 4: Sprechende Uhr – Ansage mit Textbausteinen (Uhrzeit, Wochentag, Datum, Name, Wetter),
      Weckton wird währenddessen leiser, Verzögerung und Wiederholung einstellbar; Wetter von Open-Meteo
      (ohne API-Schlüssel) mit Ortssuche, Anzeige auf dem Weckbildschirm
