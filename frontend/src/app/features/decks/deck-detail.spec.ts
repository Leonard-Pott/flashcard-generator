import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { DeckDetailPage } from './deck-detail';
import { Card } from '../../core/models/card';
import { Deck } from '../../core/models/deck';
import { environment } from '../../../environments/environment';

const deckId = 'a1000000-0000-4000-8000-000000000001';
const deckUrl = `${environment.apiBaseUrl}/decks/${deckId}`;
const cardsUrl = `${deckUrl}/cards`;
const decksUrl = `${environment.apiBaseUrl}/decks`;
const foldersUrl = `${environment.apiBaseUrl}/folders`;

/** Anderer Stapel, den man hierher übernehmen kann. */
const otherDeck: Deck = {
  id: 'b2000000-0000-4000-8000-000000000002',
  name: 'Altlasten',
  description: null,
  language: 'de',
  examDate: null,
  folderId: null,
  cardCount: 5,
  dueToday: 0,
  nextDueAt: null,
  createdAt: '2026-07-01T10:00:00Z',
  updatedAt: '2026-07-01T10:00:00Z',
};

const deck: Deck = {
  id: deckId,
  name: 'ITIL Service Design',
  description: 'Kapitel 1-3',
  language: 'de',
  examDate: null,
  folderId: null,
  cardCount: 1,
  dueToday: 1,
  nextDueAt: null,
  createdAt: '2026-07-01T10:00:00Z',
  updatedAt: '2026-07-01T10:00:00Z',
};

function card(overrides: Partial<Card> = {}): Card {
  return {
    id: 'c1000000-0000-4000-8000-000000000001',
    deckId,
    front: 'Was ist ein Incident?',
    back: 'Eine ungeplante Unterbrechung.',
    tags: ['ITIL'],
    sourceDocumentId: null,
    sourceExcerpt: null,
    easeFactor: 2.5,
    intervalDays: 0,
    repetitions: 0,
    learningStep: 0,
    dueAt: '2026-07-27T10:00:00Z',
    createdAt: '2026-07-27T10:00:00Z',
    updatedAt: '2026-07-27T10:00:00Z',
    ...overrides,
  };
}

describe('DeckDetailPage', () => {
  let fixture: ComponentFixture<DeckDetailPage>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeckDetailPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DeckDetailPage);
    fixture.componentRef.setInput('id', deckId);
  });

  afterEach(() => httpMock.verify());

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function el<T extends HTMLElement>(selector: string): T {
    return (fixture.nativeElement as HTMLElement).querySelector<T>(selector)!;
  }

  function typeInto(selector: string, value: string): void {
    const field = el<HTMLInputElement | HTMLTextAreaElement>(selector);
    field.value = value;
    field.dispatchEvent(new Event('input'));
  }

  /** Ordner und Stapelliste braucht die Seite fuer die Auswahlfelder. */
  function flushLists(): void {
    httpMock.expectOne(foldersUrl).flush([]);
    httpMock.expectOne(decksUrl).flush([deck, otherDeck]);
  }

  async function loadWith(cards: Card[], loaded: Deck = deck): Promise<void> {
    await fixture.whenStable();
    httpMock.expectOne(deckUrl).flush(loaded);
    flushLists();
    await fixture.whenStable();
    httpMock.expectOne(cardsUrl).flush(cards);
    await fixture.whenStable();
  }

  it('should show deck header and its cards', async () => {
    await loadWith([card()]);

    expect(text()).toContain('ITIL Service Design');
    expect(text()).toContain('Was ist ein Incident?');
    expect(text()).toContain('Eine ungeplante Unterbrechung.');
    expect(text()).toContain('ITIL');
  });

  it('should show the empty state for a deck without cards', async () => {
    await loadWith([]);

    expect(text()).toContain('Dieser Stapel enthält noch keine Karten.');
  });

  it('should offer both export formats as direct links', async () => {
    await loadWith([card()]);

    const links = [...(fixture.nativeElement as HTMLElement).querySelectorAll('.exports a')];
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      `${environment.apiBaseUrl}/decks/${deckId}/export?format=csv`,
      `${environment.apiBaseUrl}/decks/${deckId}/export?format=anki`,
    ]);
  });

  it('should not offer an export for an empty deck', async () => {
    // Der Export haengt an cardCount des Stapels, nicht an der geladenen Kartenliste.
    await loadWith([], { ...deck, cardCount: 0 });

    expect((fixture.nativeElement as HTMLElement).querySelector('.exports')).toBeNull();
  });

  it('should add a card with parsed tags', async () => {
    await loadWith([]);

    typeInto('textarea[name="front"]', '  Was ist ein Problem?  ');
    typeInto('textarea[name="back"]', 'Die Ursache eines oder mehrerer Incidents.');
    typeInto('input[name="tags"]', 'ITIL, Service Operation ,');
    el<HTMLFormElement>('form.new-card').dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    const request = httpMock.expectOne({ method: 'POST', url: cardsUrl });
    expect(request.request.body).toEqual({
      front: 'Was ist ein Problem?',
      back: 'Die Ursache eines oder mehrerer Incidents.',
      tags: ['ITIL', 'Service Operation'],
    });
    request.flush(card({ id: 'neu', front: 'Was ist ein Problem?' }));
    await fixture.whenStable();

    expect(text()).toContain('Was ist ein Problem?');
  });

  it('should not send a request when a side is empty', async () => {
    await loadWith([]);

    typeInto('textarea[name="front"]', 'Nur die Vorderseite');
    el<HTMLFormElement>('form.new-card').dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    httpMock.expectNone({ method: 'POST', url: cardsUrl });
    expect(text()).toContain('Vorder- und Rückseite dürfen nicht leer sein.');
  });

  it('should edit a card inline and patch only the card', async () => {
    await loadWith([card()]);

    el<HTMLButtonElement>('td.actions button').click();
    await fixture.whenStable();

    typeInto('textarea[name="editBack"]', 'Korrigierte Antwort');
    const buttons = [...(fixture.nativeElement as HTMLElement).querySelectorAll('tr.editing button')];
    (buttons.find((b) => b.textContent?.includes('Speichern')) as HTMLButtonElement).click();
    await fixture.whenStable();

    const request = httpMock.expectOne({
      method: 'PATCH',
      url: `${environment.apiBaseUrl}/cards/${card().id}`,
    });
    expect(request.request.body).toEqual({
      front: 'Was ist ein Incident?',
      back: 'Korrigierte Antwort',
      tags: ['ITIL'],
    });
    request.flush(card({ back: 'Korrigierte Antwort' }));
    await fixture.whenStable();

    expect(text()).toContain('Korrigierte Antwort');
    expect((fixture.nativeElement as HTMLElement).querySelector('tr.editing')).toBeNull();
  });

  it('should leave the card untouched when editing is cancelled', async () => {
    await loadWith([card()]);

    el<HTMLButtonElement>('td.actions button').click();
    await fixture.whenStable();
    typeInto('textarea[name="editBack"]', 'Verworfen');

    const buttons = [...(fixture.nativeElement as HTMLElement).querySelectorAll('tr.editing button')];
    (buttons.find((b) => b.textContent?.includes('Abbrechen')) as HTMLButtonElement).click();
    await fixture.whenStable();

    httpMock.expectNone({ method: 'PATCH', url: `${environment.apiBaseUrl}/cards/${card().id}` });
    expect(text()).toContain('Eine ungeplante Unterbrechung.');
    expect(text()).not.toContain('Verworfen');
  });

  it('should delete a card', async () => {
    await loadWith([card()]);

    el<HTMLButtonElement>('td.actions button.danger').click();
    await fixture.whenStable();

    httpMock
      .expectOne({ method: 'DELETE', url: `${environment.apiBaseUrl}/cards/${card().id}` })
      .flush(null);
    await fixture.whenStable();

    expect(text()).toContain('Dieser Stapel enthält noch keine Karten.');
    // Die gelöschte Karte stand für heute an und faellt aus der Zählung.
    expect(text()).toContain('0 heute offen');
  });

  it('should show what is due today and when the next card comes', async () => {
    const morgen = new Date(Date.now() + 26 * 3_600_000).toISOString();
    await loadWith([card()], { ...deck, dueToday: 4, nextDueAt: morgen });

    expect(text()).toContain('4 heute offen');
    expect(text()).toContain('nächste Karte am ');
  });

  it('should save an exam date and show the resulting cap', async () => {
    await loadWith([card()]);

    // Ortszeit, nicht UTC - sonst kippt der Test rund um Mitternacht auf neun Tage.
    const inTenDays = new Date(Date.now() + 10 * 86_400_000).toLocaleDateString('sv-SE');
    typeInto('input[name="examDate"]', inTenDays);
    el<HTMLInputElement>('input[name="examDate"]').dispatchEvent(new Event('change'));
    await fixture.whenStable();

    const request = httpMock.expectOne({ method: 'PATCH', url: deckUrl });
    expect(request.request.body).toEqual({ examDate: inTenDays });
    request.flush({ ...deck, examDate: inTenDays });
    await fixture.whenStable();

    expect(text()).toContain('Noch 10 Tage');
    // Halbe Restzeit, wie in Sm2.capBeforeExam.
    expect(text()).toContain('auf 5 Tag(e) gedeckelt');
  });

  it('should send an empty string to clear the exam date', async () => {
    await loadWith([card()], { ...deck, examDate: '2026-08-14' });

    typeInto('input[name="examDate"]', '');
    el<HTMLInputElement>('input[name="examDate"]').dispatchEvent(new Event('change'));
    await fixture.whenStable();

    const request = httpMock.expectOne({ method: 'PATCH', url: deckUrl });
    expect(request.request.body).toEqual({ examDate: '' });
    request.flush(deck);
    await fixture.whenStable();

    expect(text()).toContain('Ohne Termin plant SM-2');
  });

  /** Der Fehler, der den Anlass gab: ein Teil-PATCH darf den Termin nicht mitnehmen. */
  it('should send only the changed field when moving into a folder', async () => {
    await loadWith([card()], { ...deck, examDate: '2026-09-01' });

    const select = el<HTMLSelectElement>('select[name="folder"]');
    select.value = '';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    const request = httpMock.expectOne({ method: 'PATCH', url: deckUrl });
    expect(Object.keys(request.request.body as object)).toEqual(['folderId']);
    request.flush({ ...deck, examDate: '2026-09-01' });
    await fixture.whenStable();

    expect(text()).toContain('Noch');
  });

  it('should rename the deck', async () => {
    await loadWith([card()]);

    typeInto('input[name="deckName"]', 'ITIL Foundation');
    el<HTMLInputElement>('input[name="deckName"]').dispatchEvent(new Event('change'));
    await fixture.whenStable();

    const request = httpMock.expectOne({ method: 'PATCH', url: deckUrl });
    expect(request.request.body).toEqual({ name: 'ITIL Foundation' });
    request.flush({ ...deck, name: 'ITIL Foundation' });
    await fixture.whenStable();

    expect(el<HTMLHeadingElement>('h2').textContent).toContain('ITIL Foundation');
  });

  it('should not send a request for an unchanged name', async () => {
    await loadWith([card()]);

    el<HTMLInputElement>('input[name="deckName"]').dispatchEvent(new Event('change'));
    await fixture.whenStable();

    httpMock.expectNone({ method: 'PATCH', url: deckUrl });
  });

  it('should merge another deck into this one', async () => {
    await loadWith([card()]);

    const select = el<HTMLSelectElement>('select[name="mergeSource"]');
    expect(select.textContent).toContain('Altlasten');
    select.value = otherDeck.id;
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    [...(fixture.nativeElement as HTMLElement).querySelectorAll('p.merge button')]
      .find((button) => button.textContent?.includes('Zusammenführen'))!
      .dispatchEvent(new Event('click'));
    await fixture.whenStable();

    const request = httpMock.expectOne({ method: 'POST', url: `${deckUrl}/merge` });
    expect(request.request.body).toEqual({ sourceDeckId: otherDeck.id });
    request.flush({ ...deck, cardCount: 6 });
    // Die Kartenliste wird danach frisch geladen.
    httpMock.expectOne(cardsUrl).flush([card(), card({ id: 'uebernommen', front: 'Aus Altlasten' })]);
    await fixture.whenStable();

    expect(text()).toContain('6 Karten');
    expect(text()).toContain('Aus Altlasten');
    // Der uebernommene Stapel steht nicht mehr zur Auswahl.
    expect((fixture.nativeElement as HTMLElement).querySelector('p.merge')).toBeNull();
  });

  it('should move the deck into a folder', async () => {
    await fixture.whenStable();
    httpMock.expectOne(deckUrl).flush(deck);
    httpMock.expectOne(foldersUrl).flush([
      { id: 'f1', name: 'Modul ITSM', createdAt: deck.createdAt, updatedAt: deck.updatedAt },
    ]);
    httpMock.expectOne(decksUrl).flush([deck]);
    await fixture.whenStable();
    httpMock.expectOne(cardsUrl).flush([card()]);
    await fixture.whenStable();

    const select = el<HTMLSelectElement>('select[name="folder"]');
    select.value = 'f1';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    const request = httpMock.expectOne({ method: 'PATCH', url: deckUrl });
    expect(request.request.body).toEqual({ folderId: 'f1' });
    request.flush({ ...deck, folderId: 'f1' });
    await fixture.whenStable();
  });

  it('should show a message when the deck does not exist', async () => {
    await fixture.whenStable();
    httpMock
      .expectOne(deckUrl)
      .flush({ detail: 'Stapel nicht gefunden: ' + deckId }, { status: 404, statusText: 'Not Found' });
    flushLists();
    await fixture.whenStable();

    expect(text()).toContain('Stapel nicht gefunden');
  });
});
