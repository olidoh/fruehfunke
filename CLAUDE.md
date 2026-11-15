# Hinweise für Claude

## Veröffentlichen (Managed Google Play, private App wie easyTDD)
- Frühfunke ist eine **private App** für die Organisation `LC049ol8fr` (oliver-dohrenbusch.de), verteilt über Intune.
- Ablauf, feste Werte und einmalige Einrichtung: `docs/Veroeffentlichung.md`.
- Neue Version: Änderungen auf `main` pushen, dann Workflow `build.yml` per REST-API starten:
  `gh api -X POST repos/olidoh/fruehfunke/actions/workflows/build.yml/dispatches -f ref=main -f 'inputs[upload]=true' -f 'inputs[track]=production' -f 'inputs[notes]=…'`
  (`gh workflow run` geht nicht, GraphQL ist gesperrt). Standard ist `production` (privat, nur Organisation).
- Lauf verfolgen über `gh api repos/olidoh/fruehfunke/actions/runs?per_page=1`, Fehler über
  `gh api repos/olidoh/fruehfunke/check-runs/<job-id>/annotations`. Ergebnis berichten: Version, Track, Release-Link.
- Niemals ändern: Paketname `de.fruehfunke.app`, `signingConfigs`, `EXPECTED_CERT_SHA256` im Workflow,
  Dateiname `.github/workflows/build.yml` (Laufnummer = versionCode).
- Niemals lesen, ausgeben oder committen: `*.jks`, Keystore-Passwort, Dienstkonto-JSON. Secrets setzt nur der Nutzer.
- Schlägt die Signaturprüfung fehl: abbrechen und melden, nichts „reparieren“.
- Bringt eine Version neue Android-Berechtigungen mit, den Nutzer erinnern, die App im Intune Admin Center (Managed Google Play) neu zu genehmigen.
- Die App nie wieder öffentlich machen wollen: private Apps lassen sich nicht in öffentliche umwandeln.
- Code: Kotlin + Jetpack Compose, Texte in der App auf Deutsch.
