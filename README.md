# Karteikarten-Generator

Lokale Single-User-Webanwendung: Dokumente (PDF, DOCX, PPTX, TXT, Markdown) hochladen,
daraus per LLM Karteikarten generieren, bearbeiten, lernen und exportieren.

Spezifikation und Meilensteine: [flashcard-generator-plan.md](flashcard-generator-plan.md)

## Stand

**M1 — Grundgerüst** ist umgesetzt: Docker Compose mit Postgres 16, Spring Boot 3.5 mit
Flyway-Baseline, Angular-Workspace, CORS für `localhost:4200`, `GET /api/health`.

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

Backend:

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
