# Karteikarten-Generator

Lokale Single-User-Webanwendung: Dokumente (PDF, DOCX, PPTX, TXT, Markdown) hochladen,
daraus per LLM Karteikarten generieren, bearbeiten, lernen und exportieren.

Spezifikation und Meilensteine: [flashcard-generator-plan.md](flashcard-generator-plan.md)

## Stand

**M1 — Grundgerüst**: Docker Compose mit Postgres 16, Spring Boot 3.5 mit
Flyway-Baseline, Angular-Workspace, CORS für `localhost:4200`, `GET /api/health`.

**M2 — Dokumenten-Upload**: Multipart-Upload mit Textextraktion über Apache Tika
(PDF, DOCX, PPTX, TXT, Markdown), Ablage in Postgres, Liste mit Drag & Drop im Frontend.

```
POST   /api/documents      multipart/form-data, Feldname "file", max. 25 MB
GET    /api/documents
GET    /api/documents/{id}
DELETE /api/documents/{id}
```

Gespeichert wird nur der extrahierte Text, nicht die Originaldatei. Ein Dokument ohne
Textebene (typisch für gescannte PDFs) landet mit Status `FAILED` und einer Begründung
in der Liste, statt den Upload abzulehnen.

**M3 — Deck- und Card-CRUD**: Stapel und Karten anlegen, ändern und löschen; im Frontend
Stapelliste und Kartentabelle mit Inline-Bearbeitung.

```
GET    /api/decks                 mit Kartenzahl, heute offenen Karten und nächstem Termin
POST   /api/decks
GET    /api/decks/{id}
PATCH  /api/decks/{id}            nur gesendete Felder werden geändert
DELETE /api/decks/{id}            löscht auch die Karten des Stapels

GET    /api/decks/{id}/cards
POST   /api/decks/{id}/cards
PATCH  /api/cards/{id}
DELETE /api/cards/{id}
```

Neue Karten starten mit den SM-2-Werten aus dem Plan (`easeFactor` 2.5, `intervalDays` 0,
`repetitions` 0), stehen auf dem ersten Lernschritt (`learningStep` 0) und sind sofort
fällig. Ausgewertet werden diese Felder erst in M6.

**M4 — Gemini-Anbindung**: `LlmClient`-Interface mit `GeminiLlmClient` als einziger
Implementierung, inklusive Drosselung auf `requests-per-minute` und Wiederholung mit
exponentiellem Backoff (2s/4s/8s/16s) bei HTTP 429, 5xx und Netzwerkfehlern.

Der API-Key kommt ausschließlich aus der Umgebungsvariable `GEMINI_API_KEY`. Das Modell
steht in `application.yml` unter `llm.gemini.model`; eingetragen ist `gemini-3.6-flash`.

**Tageskontingent:** Der Free Tier erlaubt für `gemini-3.6-flash` nur **20 Anfragen pro
Tag** — eine je Textabschnitt. Ein Dokument mit 150.000 Zeichen ergibt rund 25 Abschnitte
und passt damit nicht in einen Tag. Der Generierungs-Dialog warnt vorher. Der hinterlegte
Wert steht unter `llm.gemini.daily-request-budget` und lässt sich mit `GEMINI_DAILY_BUDGET`
überschreiben, wenn du ein anderes Modell oder einen bezahlten Tarif verwendest.
Achtung: In der Modellliste der API aufzutauchen heißt nicht, dass ein Modell nutzbar ist —
`gemini-2.5-flash` steht dort, wird für neue Keys aber mit HTTP 404 abgelehnt.

Für einen einmaligen echten Aufruf gibt es einen Testendpunkt, der standardmäßig aus ist,
weil jeder Aufruf vom Tageskontingent abgeht:

```bash
LLM_PROBE_ENABLED=true mvn spring-boot:run
```

```bash
curl -X POST http://localhost:8080/api/llm/probe -H "Content-Type: application/json" -d "{\"userPrompt\":\"Erstelle zwei Lernkarten zum Begriff Incident.\"}"
```

**M5 — Generierung**: Chunking (30.000 Zeichen, 500 Overlap, Trennung an Absatzgrenzen),
Prompting je Sprache und Kartenstil, defensives JSON-Parsen, Deduplizierung und asynchrone
Verarbeitung mit Fortschritts-Polling.

Die Abschnittsgröße ist am Tageskontingent ausgerichtet, nicht am Modell: Jeder Abschnitt
ist eine Anfrage, und davon gibt es nur 20 pro Tag. Ein Vorlesungsskript mit 139.000
Zeichen braucht damit 5 Anfragen statt 25. Abschnitte ohne Lernstoff — Deckblätter,
Inhaltsverzeichnisse, Seitenzahlen — werden vorher aussortiert und kosten keine Anfrage.

```
POST /api/generation-jobs      → 202 Accepted, Verarbeitung läuft im Hintergrund
GET  /api/generation-jobs/{id} → Fortschritt für das Polling
GET  /api/generation-jobs
```

Die Abschnitte werden **nacheinander** verarbeitet — der Free Tier verträgt keine
parallelen Aufrufe. Scheitert ein Abschnitt endgültig, wird er übersprungen und im Job
vermerkt; der Lauf geht weiter. Nur wenn kein einziger Abschnitt durchkommt, gilt der Job
als `FAILED`.

Im Frontend werden Dokumente per Checkbox ausgewählt (nur solche mit extrahiertem Text),
konfiguriert und gestartet; danach zeigt `/jobs/:id` den Fortschritt im Zwei-Sekunden-Takt
samt geschätzter Restdauer.

**M6 — Lernmodus (SM-2)**: Wiederholungslogik nach Abschnitt 10 des Plans, Endpunkte für
fällige Karten und Bewertungen, Lern-View unter `/decks/:id/study`.

```
GET  /api/decks/{id}/study/next   fällige Karte, offene Anzahl, nächster Termin
POST /api/cards/{id}/review       Body: { "quality": 0..5 }
```

Die vier Knöpfe des Lernmodus bilden auf SM-2-Bewertungen ab: Nochmal = 0, Schwer = 3,
Gut = 4, Einfach = 5. Leertaste deckt auf, die Tasten 1 bis 4 bewerten.

**Lernschritte**: SM-2 allein kennt nur ganze Tage — jede neue Karte käme selbst nach
"Nochmal" frühestens am Folgetag wieder. Darüber liegen deshalb kurze Schritte innerhalb
derselben Sitzung, nach Anki-Vorbild:

- Eine **neue Karte** durchläuft `1 min → 10 min`, bevor sie in den Tagesrhythmus wechselt.
  "Nochmal" setzt auf den ersten Schritt zurück, "Schwer" wiederholt den aktuellen,
  "Gut" geht einen weiter, "Einfach" überspringt die Schritte (4 Tage statt 1).
- Eine **vergessene Karte** aus dem Tagesrhythmus fällt nicht auf Null zurück: Sie kommt
  in 10 Minuten wieder und behält 30 % ihres Intervalls (aus 30 Tagen werden 9, nicht 1).
- Tagesintervalle liegen auf dem **Tagesbeginn**, nicht auf der Uhrzeit der Bewertung —
  abends gelernt heißt am nächsten Morgen fällig.

Deshalb ist `dueAt` ein Zeitstempel und kein Datum: "in 10 Minuten" muss ausdrückbar sein.
`learningStep` ist der Index im Schrittplan und `-1`, sobald die Karte im Tagesrhythmus
läuft. Die Lern-View lädt sich selbst nach, wenn die nächste Karte in unter 15 Minuten
fällig wird.

**Prüfungstermin**: SM-2 optimiert auf dauerhaftes Behalten mit möglichst wenigen
Wiederholungen. Vor einem festen Termin ist das die falsche Zielgröße — eine Karte mit
15 Tagen Intervall sieht man vor einer Prüfung in zehn Tagen kein einziges Mal wieder.
Ist auf dem Stapel ein `examDate` gesetzt, deckelt der Scheduler die Intervalle deshalb
auf die **halbe Restzeit** (mindestens ein Tag), damit jede Karte vorher mindestens
einmal wiederkommt. Ohne Termin bleibt es beim normalen SM-2. Der Termin hängt am
einzelnen Stapel, nicht am Ordner: mehrere Stapel eines Moduls können auf verschiedene
Klausuren zielen.

**M7 — Export**: CSV für Excel und TSV für Anki.

```
GET /api/decks/{id}/export?format=csv    Semikolon-getrennt, UTF-8 mit BOM
GET /api/decks/{id}/export?format=anki   Tabulator-getrennt, ohne Kopfzeile
```

In der Stapelansicht stehen beide als Links neben „Lernen". Der Dateiname entsteht aus dem
Stapelnamen (`Prüfung: ITIL & Co. 2026` wird zu `Prufung-ITIL-Co-2026.csv`).

Unterschiede der Formate: CSV bekommt eine Kopfzeile und ein BOM, damit Excel die Umlaute
richtig liest; Felder mit Semikolon, Anführungszeichen oder Zeilenumbruch werden nach
RFC 4180 maskiert. Anki bekommt keins von beidem — es würde die Kopfzeile als erste Karte
importieren. Da TSV keine Maskierung kennt, werden dort Zeilenumbrüche zu `<br>` und
Tabulatoren zu Leerzeichen; Leerzeichen in Tags werden zu Bindestrichen, weil Anki Tags am
Leerzeichen trennt.

**Organisation**: Ordner, Zusammenführen und Fälligkeiten je Stapel — Zugaben nach M7,
sobald die Stapelliste zweistellig wird.

```
GET    /api/folders
POST   /api/folders               Body: { "name": "Modul Datenbanken" }
PATCH  /api/folders/{id}          umbenennen
DELETE /api/folders/{id}          die enthaltenen Stapel bleiben bestehen

POST   /api/decks/{id}/merge      Body: { "sourceDeckId": "..." }
```

- **Fälligkeiten**: Liste und Detailseite zeigen, wie viele Karten heute noch offen sind
  (Überfälliges eingeschlossen) und wann die nächste fällig wird. Beides kommt aus
  derselben Abfrage wie die Kartenzahl — eine Zählung je Stapel wäre ein N+1-Problem.
- **Umbenennen** läuft über das bestehende `PATCH /api/decks/{id}`.
- **Zusammenführen** hängt alle Karten des Quellstapels samt Lernstand an den Zielstapel
  und löscht die leere Hülle. Doppelte Karten werden dabei nicht erkannt.
- **Ordner** gruppieren Stapel, etwa je Uni-Modul, und sind flach: ein Stapel liegt in
  höchstens einem Ordner, Ordner enthalten keine Ordner. Ein gelöschter Ordner nimmt seine
  Stapel nicht mit (`ON DELETE SET NULL`). In der Übersicht lassen sie sich auf- und
  zuklappen; zugeklappte Ordner merkt sich der Browser.

**Konvention der PATCH-Formate**: Ein Feld, das fehlt oder `null` ist, bleibt unverändert;
ein **leerer String** leert es. Das gilt für `description`, `examDate` und `folderId`
gleichermaßen. Der naheliegende Weg über `Optional` funktioniert hier nicht: Jackson macht
aus einem fehlenden `Optional`-Feld ein `Optional.empty()`, womit "nicht mitgeschickt"
nicht mehr von "ausdrücklich `null`" zu unterscheiden ist — jedes Teil-PATCH hätte den
Prüfungstermin gelöscht.

## Voraussetzungen

- JDK 21 oder neuer (getestet mit JDK 24)
- Maven 3.9+
- Node.js 22+ und npm
- Docker Desktop

## Start

```bash
cp .env.example .env
```

Unter Windows nehmen dir zwei Skripte in der Projektwurzel die drei Schritte ab:
`start.bat` fährt Postgres, Backend und Frontend hoch und öffnet den Browser,
`stop.bat` legt vorher einen Dump nach `backups/YYYY-MM-DD.sql` und fährt die Container
herunter. Die Sicherungen bleiben lokal — sie enthalten den kompletten Lernstand.

Von Hand geht es so:

1. **Postgres starten** (Projektwurzel):

   ```bash
   docker compose up -d
   ```

2. **Backend starten** (Ordner `backend`) — läuft auf http://127.0.0.1:8080:

   ```bash
   mvn spring-boot:run
   ```

3. **Frontend starten** (Ordner `frontend`) — läuft auf http://localhost:4200:

   ```bash
   npm start
   ```

Die Startseite zeigt den Status von Backend und Datenbank. Direkt prüfen lässt sich das mit:

```bash
curl http://localhost:8080/api/health
```

## Tests

Backend (die Persistenz-Tests starten Postgres über Testcontainers, Docker muss laufen):

```bash
mvn test
```

Frontend:

```bash
npm test
```

## Konfiguration

| Variable | Bedeutung | Default |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Zugangsdaten der lokalen Datenbank | `flashcards` |
| `GEMINI_API_KEY` | Google-AI-Studio-Key, wird erst ab M4 benötigt | — |

Der API-Key gehört ausschließlich in `.env` (per `.gitignore` ausgeschlossen), niemals ins Repository.
