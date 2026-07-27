import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { StudyPage } from './study';
import { Card, NextCard } from '../../core/models/card';
import { Deck } from '../../core/models/deck';
import { environment } from '../../../environments/environment';

const deckId = 'a1000000-0000-4000-8000-000000000001';
const deckUrl = `${environment.apiBaseUrl}/decks/${deckId}`;
const nextUrl = `${deckUrl}/study/next`;

const deck: Deck = {
  id: deckId,
  name: 'ITIL Service Design',
  description: null,
  language: 'de',
  cardCount: 3,
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

function next(overrides: Partial<NextCard> = {}): NextCard {
  return { card: card(), dueCount: 3, nextDueDate: null, ...overrides };
}

describe('StudyPage', () => {
  let fixture: ComponentFixture<StudyPage>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StudyPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(StudyPage);
    fixture.componentRef.setInput('id', deckId);
  });

  afterEach(() => httpMock.verify());

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function buttonWith(label: string): HTMLButtonElement {
    return [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].find((b) =>
      b.textContent?.includes(label)
    ) as HTMLButtonElement;
  }

  async function load(response: NextCard = next()): Promise<void> {
    await fixture.whenStable();
    httpMock.expectOne(deckUrl).flush(deck);
    httpMock.expectOne(nextUrl).flush(response);
    await fixture.whenStable();
  }

  it('should show only the front until it is revealed', async () => {
    await load();

    expect(text()).toContain('Was ist ein Incident?');
    expect(text()).not.toContain('Eine ungeplante Unterbrechung.');
    expect(text()).toContain('Noch 3 fällig');
  });

  it('should reveal the back on demand', async () => {
    await load();

    buttonWith('Aufdecken').click();
    await fixture.whenStable();

    expect(text()).toContain('Eine ungeplante Unterbrechung.');
    expect(text()).toContain('ITIL');
    expect(buttonWith('Gut')).toBeTruthy();
  });

  it('should not allow rating before revealing', async () => {
    await load();

    // Vor dem Aufdecken gibt es die Bewertungsknöpfe gar nicht.
    expect(buttonWith('Gut')).toBeUndefined();
    httpMock.expectNone({ method: 'POST', url: `${environment.apiBaseUrl}/cards/${card().id}/review` });
  });

  it('should send the mapped quality and load the next card', async () => {
    await load();
    buttonWith('Aufdecken').click();
    await fixture.whenStable();

    buttonWith('Gut').click();
    await fixture.whenStable();

    const review = httpMock.expectOne({
      method: 'POST',
      url: `${environment.apiBaseUrl}/cards/${card().id}/review`,
    });
    expect(review.request.body).toEqual({ quality: 4 });
    review.flush(card({ repetitions: 1, intervalDays: 1 }));
    await fixture.whenStable();

    httpMock.expectOne(nextUrl).flush(next({ card: card({ id: 'c2', front: 'Zweite Frage' }), dueCount: 2 }));
    await fixture.whenStable();

    expect(text()).toContain('Zweite Frage');
    // Die neue Karte startet wieder verdeckt.
    expect(text()).not.toContain('Eine ungeplante Unterbrechung.');
    expect(text()).toContain('1 bearbeitet');
  });

  it('should map "Nochmal" to quality 0', async () => {
    await load();
    buttonWith('Aufdecken').click();
    await fixture.whenStable();

    buttonWith('Nochmal').click();
    await fixture.whenStable();

    const review = httpMock.expectOne({
      method: 'POST',
      url: `${environment.apiBaseUrl}/cards/${card().id}/review`,
    });
    expect(review.request.body).toEqual({ quality: 0 });
    review.flush(card());
    await fixture.whenStable();

    httpMock.expectOne(nextUrl).flush(next({ card: null, dueCount: 0, nextDueDate: '2026-07-28' }));
    await fixture.whenStable();
  });

  it('should reveal with the space bar and rate with the number keys', async () => {
    await load();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: ' ' }));
    await fixture.whenStable();
    expect(text()).toContain('Eine ungeplante Unterbrechung.');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: '4' }));
    await fixture.whenStable();

    const review = httpMock.expectOne({
      method: 'POST',
      url: `${environment.apiBaseUrl}/cards/${card().id}/review`,
    });
    expect(review.request.body).toEqual({ quality: 5 });
    review.flush(card());
    await fixture.whenStable();

    httpMock.expectOne(nextUrl).flush(next({ card: null, dueCount: 0, nextDueDate: null }));
    await fixture.whenStable();
  });

  it('should show when the next card is due once nothing is left', async () => {
    await load(next({ card: null, dueCount: 0, nextDueDate: '2026-08-02' }));

    expect(text()).toContain('Für heute geschafft.');
    expect(text()).toContain('2. August 2026');
  });

  it('should say when the deck has no cards at all', async () => {
    await load(next({ card: null, dueCount: 0, nextDueDate: null }));

    expect(text()).toContain('noch keine Karten');
  });

  it('should surface a backend error', async () => {
    await fixture.whenStable();
    httpMock.expectOne(deckUrl).flush(deck);
    httpMock
      .expectOne(nextUrl)
      .flush({ detail: 'Stapel nicht gefunden' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(text()).toContain('Stapel nicht gefunden');
  });
});
