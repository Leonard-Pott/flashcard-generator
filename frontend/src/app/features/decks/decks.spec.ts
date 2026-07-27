import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { DecksPage } from './decks';
import { Deck } from '../../core/models/deck';
import { environment } from '../../../environments/environment';

const decksUrl = `${environment.apiBaseUrl}/decks`;

function deck(overrides: Partial<Deck> = {}): Deck {
  return {
    id: 'a1000000-0000-4000-8000-000000000001',
    name: 'ITIL Service Design',
    description: 'Kapitel 1-3',
    language: 'de',
    cardCount: 12,
    createdAt: '2026-07-01T10:00:00Z',
    updatedAt: '2026-07-01T10:00:00Z',
    ...overrides,
  };
}

describe('DecksPage', () => {
  let fixture: ComponentFixture<DecksPage>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DecksPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DecksPage);
  });

  afterEach(() => httpMock.verify());

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function typeInto(selector: string, value: string): void {
    const field = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>(selector)!;
    field.value = value;
    field.dispatchEvent(new Event('input'));
  }

  it('should list decks with card count', async () => {
    await fixture.whenStable();
    httpMock.expectOne(decksUrl).flush([deck(), deck({ id: 'b', name: 'Anatomie', cardCount: 0 })]);
    await fixture.whenStable();

    expect(text()).toContain('ITIL Service Design');
    expect(text()).toContain('12');
    expect(text()).toContain('Anatomie');
  });

  it('should show the empty state', async () => {
    await fixture.whenStable();
    httpMock.expectOne(decksUrl).flush([]);
    await fixture.whenStable();

    expect(text()).toContain('Noch keine Stapel angelegt.');
  });

  it('should create a deck and keep the list sorted by name', async () => {
    await fixture.whenStable();
    httpMock.expectOne(decksUrl).flush([deck({ id: 'z', name: 'Zoologie' })]);
    await fixture.whenStable();

    typeInto('input[name="name"]', 'Anatomie');
    (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    const request = httpMock.expectOne({ method: 'POST', url: decksUrl });
    expect(request.request.body).toEqual({ name: 'Anatomie', description: null, language: 'de' });
    request.flush(deck({ id: 'a', name: 'Anatomie', description: null, cardCount: 0 }));
    await fixture.whenStable();

    const names = [
      ...(fixture.nativeElement as HTMLElement).querySelectorAll('table.decks tbody tr td:first-child'),
    ].map((cell) => cell.textContent?.trim());
    expect(names[0]).toContain('Anatomie');
    expect(names[1]).toContain('Zoologie');
  });

  it('should not send a request for a blank name', async () => {
    await fixture.whenStable();
    httpMock.expectOne(decksUrl).flush([]);
    await fixture.whenStable();

    typeInto('input[name="name"]', '   ');
    (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    httpMock.expectNone({ method: 'POST', url: decksUrl });
    expect(text()).toContain('Der Name darf nicht leer sein.');
  });

  it('should surface a backend error when creating fails', async () => {
    await fixture.whenStable();
    httpMock.expectOne(decksUrl).flush([]);
    await fixture.whenStable();

    typeInto('input[name="name"]', 'Neuer Stapel');
    (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    httpMock
      .expectOne({ method: 'POST', url: decksUrl })
      .flush({ detail: 'Der Name darf hoechstens 200 Zeichen lang sein.' }, {
        status: 400,
        statusText: 'Bad Request',
      });
    await fixture.whenStable();

    expect(text()).toContain('Der Name darf hoechstens 200 Zeichen lang sein.');
  });

  it('should delete a deck', async () => {
    await fixture.whenStable();
    httpMock.expectOne(decksUrl).flush([deck()]);
    await fixture.whenStable();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button.danger')!.click();
    await fixture.whenStable();

    httpMock.expectOne({ method: 'DELETE', url: `${decksUrl}/${deck().id}` }).flush(null);
    await fixture.whenStable();

    expect(text()).toContain('Noch keine Stapel angelegt.');
  });
});
