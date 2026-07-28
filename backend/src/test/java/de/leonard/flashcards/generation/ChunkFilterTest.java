package de.leonard.flashcards.generation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChunkFilterTest {

    private final ChunkFilter filter = new ChunkFilter();

    @Test
    void acceptsRealSubjectMatter() {
        String text = """
                Ein Incident ist eine ungeplante Unterbrechung eines IT-Service oder eine
                Minderung der Qualitaet eines IT-Service. Auch der Ausfall einer
                Konfigurationseinheit, der sich noch nicht auf einen Service ausgewirkt hat,
                gilt als Incident. Das Ziel des Incident Managements ist die schnellstmoegliche
                Wiederherstellung des normalen Servicebetriebs. Ein Problem dagegen ist die
                Ursache eines oder mehrerer Incidents und wird dauerhaft beseitigt.
                """;

        assertThat(filter.judge(text).usable()).isTrue();
    }

    @Test
    void rejectsPageNumbersAndFigures() {
        String text = "12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 "
                + "36 37 38 39 40 41 42 43 44 45 46 47 48 49 50 51 52 53 54 55 56 57 58 59 60";

        ChunkFilter.Verdict verdict = filter.judge(text);

        assertThat(verdict.usable()).isFalse();
        assertThat(verdict.reason()).contains("Zahlen");
    }

    @Test
    void rejectsATableOfContents() {
        String text = """
                1. Einleitung .................... 3
                2. Grundlagen ................... 7
                3. Service Design .............. 12
                4. Service Transition .......... 21
                5. Service Operation ........... 34
                6. Zusammenfassung ............. 47
                """;

        assertThat(filter.judge(text).usable()).isFalse();
    }

    @Test
    void rejectsACoverSlide() {
        String text = "IT Service Management\nVorlesung 3\nProf. Dr. Muster\nSommersemester 2026";

        ChunkFilter.Verdict verdict = filter.judge(text);

        assertThat(verdict.usable()).isFalse();
        assertThat(verdict.reason()).contains("Woerter");
    }

    @Test
    void rejectsRepeatedHeaders() {
        // Kopf- und Fusszeilen, die die Extraktion je Seite mitgenommen hat.
        String text = "Kapitel 4 Service Design Folie Seite ".repeat(30);

        ChunkFilter.Verdict verdict = filter.judge(text);

        assertThat(verdict.usable()).isFalse();
        assertThat(verdict.reason()).contains("unterschiedliche");
    }

    @Test
    void rejectsEmptyOrBlankText() {
        assertThat(filter.judge(null).usable()).isFalse();
        assertThat(filter.judge("   ").usable()).isFalse();
    }

    @Test
    void acceptsBulletPointsFromSlides() {
        // Stichpunkte sind duenn, aber der Plan will sie ausdruecklich zu Karten verdichtet sehen.
        String text = """
                Ziele des Incident Managements
                - Wiederherstellung des normalen Servicebetriebs in vereinbarter Zeit
                - Minimierung negativer Auswirkungen auf den Geschaeftsbetrieb
                - Sicherstellung bestmoeglicher Servicequalitaet und Verfuegbarkeit
                - Dokumentation aller Stoerungen fuer die spaetere Problemanalyse
                - Eskalation nach festgelegten Regeln bei Ueberschreitung der Fristen
                """;

        assertThat(filter.judge(text).usable()).isTrue();
    }

    @Test
    void acceptsTextWithManyNumbersButRealSentences() {
        // Zahlen allein sind kein Ausschlussgrund, solange Sprache drumherum steht.
        String text = """
                Eine Verfuegbarkeit von 99,9 Prozent bedeutet bei einer Servicezeit von 24 Stunden
                an 7 Tagen pro Woche eine zulaessige Ausfallzeit von etwa 8,76 Stunden pro Jahr.
                Bei 99,99 Prozent sind es nur noch rund 52 Minuten im Jahr. Die Messung erfolgt
                ueblicherweise ueber den vereinbarten Zeitraum von 12 Monaten hinweg und wird im
                Service Level Agreement zwischen Anbieter und Kunde festgehalten.
                """;

        assertThat(filter.judge(text).usable()).isTrue();
    }
}
