"""Lädt ein signiertes App-Bundle (AAB) über die Google Play Developer API hoch und gibt es auf einem Track frei.

Anmeldung: Dienstkonto-JSON in der Umgebungsvariable PLAY_SERVICE_ACCOUNT_JSON (GitHub-Secret).
Bricht ab, wenn der versionCode nicht höher ist als alles, was bereits bei Google Play liegt.
Solange die App in der Play Console noch ein Entwurf ist, wird der Release als Entwurf angelegt.
"""
import argparse
import json
import os
import sys

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from googleapiclient.http import MediaFileUpload

SCOPE = "https://www.googleapis.com/auth/androidpublisher"


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--package", required=True)
    p.add_argument("--aab", required=True)
    p.add_argument("--apk", default="", help="Ausweichweg, wenn die App nicht bei Play App Signing angemeldet ist")
    p.add_argument("--version-code", required=True, type=int)
    p.add_argument("--release-name", required=True)
    p.add_argument("--track", default="internal")
    p.add_argument("--notes", default="")
    a = p.parse_args()

    info = json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"])
    creds = service_account.Credentials.from_service_account_info(info, scopes=[SCOPE])
    api = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)
    edits = api.edits()

    edit_id = edits.insert(packageName=a.package, body={}).execute()["id"]
    try:
        vorhanden = [int(v) for t in edits.tracks().list(packageName=a.package, editId=edit_id).execute().get("tracks", [])
                     for r in t.get("releases", []) for v in r.get("versionCodes", [])]
        hoechste = max(vorhanden, default=0)
        print(f"Höchster versionCode bei Google Play: {hoechste}, neu: {a.version_code}")
        if a.version_code <= hoechste:
            sys.exit(f"FEHLER: versionCode {a.version_code} ist nicht höher als {hoechste}.")

        try:
            bundle = edits.bundles().upload(
                packageName=a.package, editId=edit_id,
                media_body=MediaFileUpload(a.aab, mimetype="application/octet-stream", resumable=True),
            ).execute()
            print(f"Bundle hochgeladen: versionCode {bundle['versionCode']}, SHA-256 {bundle.get('sha256', '?')}")
        except HttpError as e:
            if "Play Signing" not in str(e) or not a.apk:
                raise
            print("App ist nicht bei Play App Signing angemeldet – lade stattdessen die signierte APK hoch.")
            apk = edits.apks().upload(
                packageName=a.package, editId=edit_id,
                media_body=MediaFileUpload(a.apk, mimetype="application/vnd.android.package-archive", resumable=True),
            ).execute()
            print(f"APK hochgeladen: versionCode {apk['versionCode']}")

        def set_track(status):
            release = {"name": a.release_name, "versionCodes": [str(a.version_code)], "status": status}
            if a.notes:
                release["releaseNotes"] = [{"language": "de-DE", "text": a.notes[:500]}]
            edits.tracks().update(
                packageName=a.package, editId=edit_id, track=a.track,
                body={"track": a.track, "releases": [release]},
            ).execute()

        status = "completed"
        try:
            set_track(status)
        except HttpError as e:
            if "draft app" not in str(e).lower():
                raise
            status = "draft"
            print("App ist in der Play Console noch ein Entwurf – Release wird als Entwurf angelegt.")
            set_track(status)

        try:
            edits.commit(packageName=a.package, editId=edit_id).execute()
        except HttpError as e:
            if "changesNotSentForReview" not in str(e):
                raise
            edits.commit(packageName=a.package, editId=edit_id, changesNotSentForReview=True).execute()
        print(f"Track '{a.track}': {a.release_name} ({a.version_code}) – Status {status}")
    except BaseException:
        try:
            edits.delete(packageName=a.package, editId=edit_id).execute()
        except Exception:
            pass
        raise


def _annotate(msg):
    """Fehlermeldung als GitHub-Annotation ausgeben (über die API lesbar, auch ohne Log-Zugriff)."""
    clean = " ".join(str(msg).split())[:900]
    print(f"::error::Google Play: {clean}")


if __name__ == "__main__":
    try:
        main()
    except HttpError as e:
        detail = e.content.decode("utf-8", "replace") if getattr(e, "content", None) else str(e)
        _annotate(f"HTTP {e.resp.status} – {detail}")
        sys.exit(1)
    except SystemExit as e:
        if e.code not in (0, None) and not isinstance(e.code, int):
            _annotate(e.code)
        raise
    except Exception as e:
        _annotate(f"{type(e).__name__}: {e}")
        sys.exit(1)
