package de.leonard.flashcards.export;

import java.util.List;

import de.leonard.flashcards.deck.Card;
import de.leonard.flashcards.deck.Deck;

/** Ein Ausgabeformat des Kartenexports. */
public interface DeckExportWriter {

    ExportFormat format();

    String contentType();

    String fileExtension();

    byte[] write(Deck deck, List<Card> cards);
}
