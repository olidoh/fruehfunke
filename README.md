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
- [ ] Schritt 3: Sensor-Steuerung – Umdrehen, Schütteln, Power-Knopf, Näherung, Lautstärketasten
- [ ] Schritt 4: Sprechende Uhr + Wetter (OpenWeatherMap)
