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

const deck: Deck = {
  id: deckId,
  name: 'ITIL Service Design',
  description: 'Kapitel 1-3',
  language: 'de',
  cardCount: 1,
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
    dueDate: '2026-07-27',
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

  async function loadWith(cards: Card[]): Promise<void> {
    await fixture.whenStable();
    httpMock.expectOne(deckUrl).flush(deck);
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
  });

  it('should show a message when the deck does not exist', async () => {
    await fixture.whenStable();
    httpMock
      .expectOne(deckUrl)
      .flush({ detail: 'Stapel nicht gefunden: ' + deckId }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(text()).toContain('Stapel nicht gefunden');
  });
});
