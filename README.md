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
GET    /api/decks                 mit Kartenzahl je Stapel
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
`repetitions` 0) und sind sofort fällig. Ausgewertet werden diese Felder erst in M6.

**M4 — Gemini-Anbindung**: `LlmClient`-Interface mit `GeminiLlmClient` als einziger
Implementierung, inklusive Drosselung auf `requests-per-minute` und Wiederholung mit
exponentiellem Backoff (2s/4s/8s/16s) bei HTTP 429, 5xx und Netzwerkfehlern.

Der API-Key kommt ausschließlich aus der Umgebungsvariable `GEMINI_API_KEY`. Das Modell
steht in `application.yml` unter `llm.gemini.model`; eingetragen ist `gemini-3.6-flash`.
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

**M5 — Generierung**: Chunking (6.000 Zeichen, 300 Overlap, Trennung an Absatzgrenzen),
Prompting je Sprache und Kartenstil, defensives JSON-Parsen, Deduplizierung und asynchrone
Verarbeitung mit Fortschritts-Polling.

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
Gut = 4, Einfach = 5. Leertaste deckt auf, die Tasten 1 bis 4 bewerten. Neue Karten sind
sofort fällig; eine verfehlte Karte kommt am Folgetag wieder.

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

## Voraussetzungen

- JDK 21 oder neuer (getestet mit JDK 24)
- Maven 3.9+
- Node.js 22+ und npm
- Docker Desktop

## Start

```bash
cp .env.example .env
```

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
