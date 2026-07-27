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
