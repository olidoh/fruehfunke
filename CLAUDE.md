# Hinweise für Claude

## Veröffentlichen (Google Play)
- Ablauf, feste Werte und einmalige Einrichtung: `docs/Veroeffentlichung.md`.
- Neue Version: Änderungen auf `main` pushen, dann Workflow `build.yml` per REST-API starten:
  `gh api -X POST repos/olidoh/fruehfunke/actions/workflows/build.yml/dispatches -f ref=main -f 'inputs[upload]=true' -f 'inputs[track]=internal' -f 'inputs[notes]=…'`
  (`gh workflow run` geht nicht, GraphQL ist gesperrt). Track nur auf `production`, wenn der Nutzer das ausdrücklich will.
- Lauf verfolgen über `gh api repos/olidoh/fruehfunke/actions/runs?per_page=1`, Fehler über
  `gh api repos/olidoh/fruehfunke/check-runs/<job-id>/annotations`. Ergebnis berichten: Version, Track, Release-Link.
- Niemals ändern: Paketname `de.fruehfunke.app`, `signingConfigs`, `EXPECTED_CERT_SHA256` im Workflow,
  Dateiname `.github/workflows/build.yml` (Laufnummer = versionCode).
- Niemals lesen, ausgeben oder committen: `*.jks`, Keystore-Passwort, Dienstkonto-JSON. Secrets setzt nur der Nutzer.
- Schlägt die Signaturprüfung fehl: abbrechen und melden, nichts „reparieren“.
- Neue Android-Berechtigungen können in der Play Console neue Erklärungen erfordern – den Nutzer darauf hinweisen.
- Code: Kotlin + Jetpack Compose, Texte in der App auf Deutsch.
