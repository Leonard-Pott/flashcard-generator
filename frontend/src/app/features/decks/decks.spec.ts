import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { DecksPage } from './decks';
import { Deck } from '../../core/models/deck';
import { Folder } from '../../core/models/folder';
import { environment } from '../../../environments/environment';

const decksUrl = `${environment.apiBaseUrl}/decks`;
const foldersUrl = `${environment.apiBaseUrl}/folders`;

function deck(overrides: Partial<Deck> = {}): Deck {
  return {
    id: 'a1000000-0000-4000-8000-000000000001',
    name: 'ITIL Service Design',
    description: 'Kapitel 1-3',
    language: 'de',
    examDate: null,
    folderId: null,
    cardCount: 12,
    dueToday: 4,
    nextDueAt: null,
    createdAt: '2026-07-01T10:00:00Z',
    updatedAt: '2026-07-01T10:00:00Z',
    ...overrides,
  };
}

function folder(overrides: Partial<Folder> = {}): Folder {
  return {
    id: 'f1000000-0000-4000-8000-000000000001',
    name: 'Modul Datenbanken',
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

    localStorage.clear();
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

  function submit(selector: string): void {
    (fixture.nativeElement as HTMLElement).querySelector(selector)!.dispatchEvent(new Event('submit'));
  }

  async function loadWith(decks: Deck[], folders: Folder[] = []): Promise<void> {
    await fixture.whenStable();
    httpMock.expectOne(decksUrl).flush(decks);
    httpMock.expectOne(foldersUrl).flush(folders);
    await fixture.whenStable();
  }

  it('should list decks with card count', async () => {
    await loadWith([deck(), deck({ id: 'b', name: 'Anatomie', cardCount: 0 })]);

    expect(text()).toContain('ITIL Service Design');
    expect(text()).toContain('12');
    expect(text()).toContain('Anatomie');
  });

  it('should show what is due today and when the next card comes', async () => {
    // Mehr als 24 Stunden, damit es unabhaengig von der Uhrzeit ein anderer Tag ist.
    const morgenFrueh = new Date(Date.now() + 26 * 3_600_000).toISOString();
    await loadWith([deck({ dueToday: 7, nextDueAt: morgenFrueh })]);

    const cells = [
      ...(fixture.nativeElement as HTMLElement).querySelectorAll('table.decks tbody tr td'),
    ].map((cell) => cell.textContent?.trim());
    expect(cells[3]).toBe('7');
    expect(cells[4]).toContain('am ');
  });

  it('should show the empty state', async () => {
    await loadWith([]);

    expect(text()).toContain('Noch keine Stapel angelegt.');
  });

  it('should group decks by folder', async () => {
    await loadWith(
      [
        deck({ id: 'a', name: 'Normalformen', folderId: folder().id }),
        deck({ id: 'b', name: 'Loser Stapel' }),
      ],
      [folder()]
    );

    const groups = [...(fixture.nativeElement as HTMLElement).querySelectorAll('.folder')];
    expect(groups.length).toBe(2);
    expect(groups[0].textContent).toContain('Modul Datenbanken');
    expect(groups[0].textContent).toContain('Normalformen');
    expect(groups[1].textContent).toContain('Ohne Ordner');
    expect(groups[1].textContent).toContain('Loser Stapel');
  });

  it('should remember which folders are collapsed', async () => {
    await loadWith([deck({ folderId: folder().id })], [folder()]);

    const details = (fixture.nativeElement as HTMLElement).querySelector<HTMLDetailsElement>(
      'details.folder'
    )!;
    expect(details.open).toBe(true);

    details.open = false;
    details.dispatchEvent(new Event('toggle'));
    await fixture.whenStable();

    expect(JSON.parse(localStorage.getItem('flashcards.collapsedFolders')!)).toEqual([folder().id]);
  });

  it('should start with a folder collapsed that was collapsed before', async () => {
    localStorage.setItem('flashcards.collapsedFolders', JSON.stringify([folder().id]));
    // Der Zustand wird im Konstruktor gelesen, die Seite also noch einmal aufbauen.
    fixture = TestBed.createComponent(DecksPage);
    await fixture.whenStable();
    // Beide Instanzen haben geladen; abgefragt wird nur die zweite.
    httpMock.match(decksUrl).forEach((r) => r.flush([deck({ folderId: folder().id })]));
    httpMock.match(foldersUrl).forEach((r) => r.flush([folder()]));
    await fixture.whenStable();

    const groups = [
      ...(fixture.nativeElement as HTMLElement).querySelectorAll<HTMLDetailsElement>('details.folder'),
    ];
    expect(groups[0].open).toBe(false);
  });

  it('should move a deck into a folder', async () => {
    await loadWith([deck()], [folder()]);

    const select = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>(
      'select.folder-select'
    )!;
    select.value = folder().id;
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    const request = httpMock.expectOne({ method: 'PATCH', url: `${decksUrl}/${deck().id}` });
    expect(request.request.body).toEqual({ folderId: folder().id });
    request.flush(deck({ folderId: folder().id }));
    await fixture.whenStable();

    const groups = [...(fixture.nativeElement as HTMLElement).querySelectorAll('.folder')];
    expect(groups.length).toBe(1);
    expect(groups[0].textContent).toContain('Modul Datenbanken');
  });

  it('should create a folder', async () => {
    await loadWith([]);

    typeInto('input[name="folderName"]', '  Modul Datenbanken  ');
    submit('form.new-folder');
    await fixture.whenStable();

    const request = httpMock.expectOne({ method: 'POST', url: foldersUrl });
    expect(request.request.body).toEqual({ name: 'Modul Datenbanken' });
    request.flush(folder());
    await fixture.whenStable();

    expect(text()).toContain('Modul Datenbanken');
    expect(text()).toContain('Noch kein Stapel in diesem Ordner.');
  });

  it('should keep the decks of a deleted folder', async () => {
    await loadWith([deck({ folderId: folder().id })], [folder()]);

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('button.link.danger')!
      .click();
    await fixture.whenStable();

    httpMock.expectOne({ method: 'DELETE', url: `${foldersUrl}/${folder().id}` }).flush(null);
    await fixture.whenStable();

    expect(text()).not.toContain('Modul Datenbanken');
    expect(text()).toContain('ITIL Service Design');
  });

  it('should create a deck and keep the list sorted by name', async () => {
    await loadWith([deck({ id: 'z', name: 'Zoologie' })]);

    typeInto('input[name="name"]', 'Anatomie');
    submit('form.new-deck');
    await fixture.whenStable();

    const request = httpMock.expectOne({ method: 'POST', url: decksUrl });
    expect(request.request.body).toEqual({
      name: 'Anatomie',
      description: null,
      language: 'de',
      folderId: null,
    });
    request.flush(deck({ id: 'a', name: 'Anatomie', description: null, cardCount: 0 }));
    await fixture.whenStable();

    const names = [
      ...(fixture.nativeElement as HTMLElement).querySelectorAll('table.decks tbody tr td:first-child'),
    ].map((cell) => cell.textContent?.trim());
    expect(names[0]).toContain('Anatomie');
    expect(names[1]).toContain('Zoologie');
  });

  it('should not send a request for a blank name', async () => {
    await loadWith([]);

    typeInto('input[name="name"]', '   ');
    submit('form.new-deck');
    await fixture.whenStable();

    httpMock.expectNone({ method: 'POST', url: decksUrl });
    expect(text()).toContain('Der Name darf nicht leer sein.');
  });

  it('should surface a backend error when creating fails', async () => {
    await loadWith([]);

    typeInto('input[name="name"]', 'Neuer Stapel');
    submit('form.new-deck');
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
    await loadWith([deck()]);

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button.danger')!.click();
    await fixture.whenStable();

    httpMock.expectOne({ method: 'DELETE', url: `${decksUrl}/${deck().id}` }).flush(null);
    await fixture.whenStable();

    expect(text()).toContain('Noch keine Stapel angelegt.');
  });
});
