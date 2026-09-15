# Karteikarten-Generator

Lokale Webanwendung, die aus Vorlesungsunterlagen automatisch Karteikarten erzeugt und sie
nach einem Spaced-Repetition-Verfahren abfragt: Dokument hochladen (PDF, DOCX, PPTX, TXT,
Markdown) → Text extrahieren → per LLM Frage-Antwort-Paare generieren → bearbeiten →
lernen → nach Anki oder Excel exportieren.

Entstanden als Werkzeug für das eigene Studium. Das prägt den Entwurf: Single-User, alles
auf dem eigenen Rechner, und der kostenlose Gemini-Tarif mit **20 Anfragen pro Tag** ist
die knappe Ressource, an der sich einiges im Code ausrichtet.

| Bereich | Technologie |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Data JPA, Flyway |
| Datenbank | PostgreSQL 16 im Container |
| Frontend | Angular 21, Standalone Components, Signals |
| LLM | Google Gemini über Springs `RestClient` — kein SDK |
| Extraktion | Apache Tika |
| Tests | JUnit 5, Mockito, Testcontainers, WireMock (Backend), Vitest mit jsdom (Frontend) |

Rund 8.500 Zeilen Java und 3.000 Zeilen TypeScript, abgesichert durch 255 Backend-Tests in
29 Testklassen und 69 Frontend-Tests.

## Was daran technisch interessant ist

- **[Spaced Repetition mit Lernschritten](#lernschritte)** — SM-2 kennt nur ganze Tage, eine
  neue Karte käme selbst nach "Nochmal" frühestens morgen wieder. Darüber liegt deshalb eine
  Minutenebene nach Anki-Vorbild, weshalb `dueAt` ein Zeitstempel ist und kein Datum.
- **[Der Prüfungstermin verändert die Zielgröße](#prüfungstermin)** — SM-2 optimiert auf
  dauerhaftes Behalten mit wenigen Wiederholungen. Vor einer Klausur in zehn Tagen ist das
  falsch: Eine Karte mit 15 Tagen Intervall sieht man vorher kein einziges Mal. Der
  Scheduler deckelt die Intervalle dann auf die halbe Restzeit.
- **[Chunking am Kontingent ausgerichtet, nicht am Modell](#generierung)** — nicht die
  Kontextlänge ist knapp, sondern die 20 Anfragen pro Tag. Also große Abschnitte statt
  kleiner, und Deckblätter und Inhaltsverzeichnisse vorher aussieben, damit keine Anfrage
  für Text ohne Lernstoff draufgeht.
- **[Umgang mit einer unzuverlässigen Fremd-API](#anbindung-an-gemini)** — Drosselung,
  exponentielles Backoff, und ein Lauf, der einzelne gescheiterte Abschnitte überspringt,
  statt alles zu verwerfen.
- **[Ein Jackson-Fallstrick, der Daten gelöscht hat](#konvention-der-patch-formate)** — der
  naheliegende Weg über `Optional` machte "nicht mitgeschickt" ununterscheidbar von
  "ausdrücklich `null`", womit jedes Teil-PATCH den Prüfungstermin löschte.

## Architektur

Geschnitten wird nach Fachlichkeit, nicht nach Schichten — ein Paket enthält Controller,
Service und Repository seines Themas:

```
backend/src/main/java/de/leonard/flashcards/
  document/     Upload und Textextraktion über Tika
  generation/   Chunking, Prompting, asynchrone Job-Verarbeitung
  llm/          Gemini-Client mit Drosselung und Retry
  deck/         Stapel, Karten, Ordner
  study/        SM-2, Lernschritte, Fälligkeiten
  export/       CSV und Anki-TSV
  config/       CORS und einheitliche Fehlerantworten
```

Das Schema gehört Flyway (`ddl-auto: validate`), Hibernate darf nichts anlegen oder ändern.
Der Gemini-Zugriff liegt hinter einem `LlmClient`-Interface, was die Generierungstests ohne
echte API-Aufrufe möglich macht.

## Schnellstart

**Voraussetzungen**: JDK 21 oder neuer (getestet mit JDK 24), Maven 3.9+, Node.js 22+ mit
npm, Docker Desktop.

```bash
cp .env.example .env
```

In die `.env` gehört der `GEMINI_API_KEY` aus dem [Google AI Studio](https://aistudio.google.com/apikey).
Ohne ihn läuft alles außer der Generierung.

**Unter Windows** nehmen zwei Skripte in der Projektwurzel die Handgriffe ab:

- `start.bat` fährt Docker, Postgres, Backend und Frontend hoch und öffnet den Browser,
  sobald beide Server antworten. Backend und Frontend laufen ohne Fenster weiter, ihre
  Ausgabe landet in `logs\`.
- `stop.bat` sichert den Lernstand nach `backups\JJJJ-MM-TT.sql`, beendet die Server über
  ihre Ports und fährt die Container herunter.

Beim ersten Mal vorher einmal `npm install` im Ordner `frontend`.

**Von Hand** sind es drei Schritte:

1. **Postgres** (Projektwurzel): `docker compose up -d`
2. **Backend** (Ordner `backend`): `mvn spring-boot:run` — http://127.0.0.1:8080
3. **Frontend** (Ordner `frontend`): `npm start` — http://localhost:4200

Die Startseite zeigt den Status von Backend und Datenbank. Direkt prüfen lässt sich das mit:

```bash
curl http://localhost:8080/api/health
```

## Bedienung

Der Weg durch die Anwendung ist `/upload` → `/decks` → `/decks/:id` → `/decks/:id/study`:
Dokument hochladen, daraus einen Stapel generieren lassen, Karten nachbearbeiten, lernen,
bei Bedarf exportieren.

> **Tageskontingent beachten.** Der Free Tier erlaubt für `gemini-3.6-flash` nur **20
> Anfragen pro Tag** — eine je Textabschnitt. Ein Dokument mit 150.000 Zeichen ergibt rund
> 25 Abschnitte und passt damit nicht in einen Tag. Der Generierungs-Dialog warnt vorher.

## Dokumente

```
POST   /api/documents      multipart/form-data, Feldname "file", max. 25 MB
GET    /api/documents
GET    /api/documents/{id}
DELETE /api/documents/{id}
```

Die Textextraktion läuft über Apache Tika. Gespeichert wird nur der extrahierte Text, nicht
die Originaldatei. Ein Dokument ohne Textebene (typisch für gescannte PDFs) landet mit
Status `FAILED` und einer Begründung in der Liste, statt den Upload abzulehnen.

## Stapel und Karten

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

Neue Karten starten mit den SM-2-Startwerten (`easeFactor` 2.5, `intervalDays` 0,
`repetitions` 0), stehen auf dem ersten Lernschritt (`learningStep` 0) und sind sofort
fällig.

Im Frontend zeigt die Stapelliste zu jedem Stapel, wie viele Karten heute noch offen sind
(Überfälliges eingeschlossen) und wann die nächste fällig wird. Beides kommt aus derselben
Abfrage wie die Kartenzahl — eine Zählung je Stapel wäre ein N+1-Problem.

### Ordner und Zusammenführen

```
GET    /api/folders
POST   /api/folders               Body: { "name": "Modul Datenbanken" }
PATCH  /api/folders/{id}          umbenennen
DELETE /api/folders/{id}          die enthaltenen Stapel bleiben bestehen

POST   /api/decks/{id}/merge      Body: { "sourceDeckId": "..." }
```

- **Ordner** gruppieren Stapel, etwa je Uni-Modul, und sind flach: ein Stapel liegt in
  höchstens einem Ordner, Ordner enthalten keine Ordner. Ein gelöschter Ordner nimmt seine
  Stapel nicht mit (`ON DELETE SET NULL`). In der Übersicht lassen sie sich auf- und
  zuklappen; zugeklappte Ordner merkt sich der Browser.
- **Umbenennen** läuft über das bestehende `PATCH /api/decks/{id}`.
- **Zusammenführen** hängt alle Karten des Quellstapels samt Lernstand an den Zielstapel
  und löscht die leere Hülle. Doppelte Karten werden dabei nicht erkannt.

### Konvention der PATCH-Formate

Ein Feld, das fehlt oder `null` ist, bleibt unverändert; ein **leerer String** leert es.
Das gilt für `description`, `examDate` und `folderId` gleichermaßen. Der naheliegende Weg
über `Optional` funktioniert hier nicht: Jackson macht aus einem fehlenden `Optional`-Feld
ein `Optional.empty()`, womit "nicht mitgeschickt" nicht mehr von "ausdrücklich `null`" zu
unterscheiden ist — jedes Teil-PATCH hätte den Prüfungstermin gelöscht.

## Generierung

```
POST /api/generation-jobs      → 202 Accepted, Verarbeitung läuft im Hintergrund
GET  /api/generation-jobs/{id} → Fortschritt für das Polling
GET  /api/generation-jobs
```

Im Frontend werden Dokumente per Checkbox ausgewählt (nur solche mit extrahiertem Text),
konfiguriert und gestartet; danach zeigt `/jobs/:id` den Fortschritt im Zwei-Sekunden-Takt
samt geschätzter Restdauer.

Der Ablauf: Chunking (30.000 Zeichen, 500 Overlap, Trennung an Absatzgrenzen), Prompting je
Sprache und Kartenstil, defensives JSON-Parsen, Deduplizierung.

Die Abschnittsgröße ist am Tageskontingent ausgerichtet, nicht am Modell: Jeder Abschnitt
ist eine Anfrage, und davon gibt es nur 20 pro Tag. Ein Vorlesungsskript mit 139.000
Zeichen braucht damit 5 Anfragen statt 25. Abschnitte ohne Lernstoff — Deckblätter,
Inhaltsverzeichnisse, Seitenzahlen — werden vorher aussortiert und kosten keine Anfrage.

Die Abschnitte werden **nacheinander** verarbeitet — der Free Tier verträgt keine
parallelen Aufrufe. Scheitert ein Abschnitt endgültig, wird er übersprungen und im Job
vermerkt; der Lauf geht weiter. Nur wenn kein einziger Abschnitt durchkommt, gilt der Job
als `FAILED`.

### Anbindung an Gemini

`LlmClient`-Interface mit `GeminiLlmClient` als einziger Implementierung, inklusive
Drosselung auf `requests-per-minute` und Wiederholung mit exponentiellem Backoff
(2s/4s/8s/16s) bei HTTP 429, 5xx und Netzwerkfehlern.

Der API-Key kommt ausschließlich aus der Umgebungsvariable `GEMINI_API_KEY`. Das Modell
steht in `application.yml` unter `llm.gemini.model`; eingetragen ist `gemini-3.6-flash`.
Der hinterlegte Tageswert steht unter `llm.gemini.daily-request-budget` und lässt sich mit
`GEMINI_DAILY_BUDGET` überschreiben, wenn du ein anderes Modell oder einen bezahlten Tarif
verwendest; er dient nur der Vorwarnung im Frontend — durchgesetzt wird das Kontingent von
Google. Achtung: In der Modellliste der API aufzutauchen heißt nicht, dass ein Modell
nutzbar ist — `gemini-2.5-flash` steht dort, wird für neue Keys aber mit HTTP 404 abgelehnt.

Für einen einmaligen echten Aufruf gibt es einen Testendpunkt, der standardmäßig aus ist,
weil jeder Aufruf vom Tageskontingent abgeht:

```bash
LLM_PROBE_ENABLED=true mvn spring-boot:run
```

```bash
curl -X POST http://localhost:8080/api/llm/probe -H "Content-Type: application/json" -d "{\"userPrompt\":\"Erstelle zwei Lernkarten zum Begriff Incident.\"}"
```

## Lernen

```
GET  /api/decks/{id}/study/next   fällige Karte, offene Anzahl, nächster Termin
POST /api/cards/{id}/review       Body: { "quality": 0..5 }
```

Die vier Knöpfe der Lern-View unter `/decks/:id/study` bilden auf SM-2-Bewertungen ab:
Nochmal = 0, Schwer = 3, Gut = 4, Einfach = 5. Leertaste deckt auf, die Tasten 1 bis 4
bewerten.

### Lernschritte

SM-2 allein kennt nur ganze Tage — jede neue Karte käme selbst nach "Nochmal" frühestens am
Folgetag wieder. Darüber liegen deshalb kurze Schritte innerhalb derselben Sitzung, nach
Anki-Vorbild:

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

### Prüfungstermin

SM-2 optimiert auf dauerhaftes Behalten mit möglichst wenigen Wiederholungen. Vor einem
festen Termin ist das die falsche Zielgröße — eine Karte mit 15 Tagen Intervall sieht man
vor einer Prüfung in zehn Tagen kein einziges Mal wieder. Ist auf dem Stapel ein `examDate`
gesetzt, deckelt der Scheduler die Intervalle deshalb auf die **halbe Restzeit**
(mindestens ein Tag), damit jede Karte vorher mindestens einmal wiederkommt. Ohne Termin
bleibt es beim normalen SM-2. Der Termin hängt am einzelnen Stapel, nicht am Ordner:
mehrere Stapel eines Moduls können auf verschiedene Klausuren zielen.

### Freies Üben

`/decks/:id/study?frei=1` geht alle Karten des Stapels gemischt durch, unabhängig von der
Fälligkeit. Es wird kein Review geschickt, die Wiederholungszeiten bleiben also unverändert
— die Runde läuft rein im Browser über `GET /api/decks/{id}/cards`. "Nochmal" (Taste 1)
legt die Karte ans Ende der Runde, "Weiter" (Taste 2) hakt sie ab. In der Stapelansicht
steht der Link neben „Lernen".

## Export

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

## Tests

```bash
cd backend && mvn test      # 255 Tests, Docker muss laufen
cd frontend && npm test     # 69 Tests
```

Die Persistenz- und Migrationstests starten ein echtes Postgres über Testcontainers statt
gegen eine In-Memory-Datenbank zu laufen — sonst prüfen sie H2-Verhalten und nicht das, was
später produktiv läuft. Der Gemini-Client wird gegen WireMock getestet, inklusive der
Retry- und Drosselungspfade; die Zeit dafür liegt hinter einer `Sleeper`-Abstraktion, damit
die Tests keine 30 Sekunden warten.

## Sicherung und Wiederherstellung

`stop.bat` legt bei jedem Herunterfahren einen `pg_dump` nach `backups\JJJJ-MM-TT.sql` —
eine Datei pro Tag, mehrere Stopps am selben Tag überschreiben sich. Der Ordner ist von
`.gitignore` ausgeschlossen: Die Dumps enthalten den kompletten Lernstand.

Zurückspielen (der Dump ist ein einfacher SQL-Dump ohne `--clean`, die Datenbank muss also
leer sein):

```
docker compose down -v
docker compose up -d --wait
docker exec -i flashcards-postgres psql -U flashcards -d flashcards < backups\2026-09-15.sql
```

`docker compose down -v` löscht das Volume mitsamt dem bisherigen Stand — vorher sicher
sein, dass der Dump der richtige ist. Die letzte Zeile gehört in `cmd`, nicht in
PowerShell: PowerShell kennt `<` nicht und macht beim Durchpipen UTF-8 mit BOM daraus, was
`psql` nicht mag.

## Konfiguration

| Variable | Bedeutung | Default |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Zugangsdaten der lokalen Datenbank | `flashcards` |
| `GEMINI_API_KEY` | Google-AI-Studio-Key, wird für die Generierung benötigt | — |
| `GEMINI_DAILY_BUDGET` | Anfragen pro Tag, überschreibt `llm.gemini.daily-request-budget` | `20` |
| `LLM_PROBE_ENABLED` | schaltet den Testendpunkt `/api/llm/probe` frei | `false` |

Der API-Key gehört ausschließlich in `.env` (per `.gitignore` ausgeschlossen), niemals ins
Repository.

## Grenzen

Bewusst nicht enthalten, weil die Anwendung für genau einen Nutzer auf genau einem Rechner
gedacht ist: keine Authentifizierung, keine Mandantentrennung, kein Deployment-Pfad. Backend
und Datenbank sind an `127.0.0.1` gebunden und damit nicht aus dem Netz erreichbar — das ist
die Sicherheitsannahme, auf der der Rest aufbaut. Wollte man die Anwendung ins Netz stellen,
wäre Authentifizierung der erste Schritt, nicht der letzte.

Fachlich offen: Beim Zusammenführen zweier Stapel werden doppelte Karten nicht erkannt, und
die Generierung prüft die inhaltliche Richtigkeit der Karten nicht — sie kommen von einem
Sprachmodell und wollen durchgesehen werden.
