import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { UploadPage } from './upload';
import { DocumentSummary } from '../../core/models/document';
import { environment } from '../../../environments/environment';

const documentsUrl = `${environment.apiBaseUrl}/documents`;
const decksUrl = `${environment.apiBaseUrl}/decks`;

function summary(overrides: Partial<DocumentSummary> = {}): DocumentSummary {
  return {
    id: 'e6f1c0d2-0000-4000-8000-000000000001',
    filename: 'skript.pdf',
    mimeType: 'application/pdf',
    sizeBytes: 2048,
    uploadedAt: '2026-07-27T10:00:00Z',
    charCount: 12345,
    status: 'EXTRACTED',
    errorMessage: null,
    ...overrides,
  };
}

describe('UploadPage', () => {
  let fixture: ComponentFixture<UploadPage>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UploadPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // Die Zielroute muss existieren, sonst scheitert die Weiterleitung nach dem Start.
        provideRouter([{ path: 'jobs/:id', children: [] }]),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(UploadPage);
  });

  afterEach(() => httpMock.verify());

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  /** Die Seite laedt beim Start Dokumente und Stapel; beides muss beantwortet werden. */
  async function load(documents: DocumentSummary[], decks: unknown[] = []): Promise<void> {
    await fixture.whenStable();
    httpMock.expectOne(documentsUrl).flush(documents);
    httpMock.expectOne(decksUrl).flush(decks);
    await fixture.whenStable();
  }

  /** jsdom kennt kein DataTransfer, deshalb wird das Drop-Event von Hand gebaut. */
  function dropFiles(...files: File[]): void {
    const event = new Event('drop', { bubbles: true });
    Object.defineProperty(event, 'dataTransfer', { value: { files } });
    (fixture.nativeElement as HTMLElement).querySelector('.dropzone')!.dispatchEvent(event);
  }

  it('should list documents from the backend', async () => {
    await load([summary(), summary({ id: 'x', filename: 'folien.pptx' })]);

    expect(text()).toContain('skript.pdf');
    expect(text()).toContain('folien.pptx');
    expect(text()).toContain('12.345');
  });

  it('should show the empty state when nothing is uploaded yet', async () => {
    await load([]);

    expect(text()).toContain('Noch keine Dokumente hochgeladen.');
  });

  it('should show the failure reason of a document', async () => {
    await load([summary({ status: 'FAILED', charCount: 0, errorMessage: 'Es wurde kein Text gefunden.' })]);

    expect(text()).toContain('FAILED');
    expect(text()).toContain('Es wurde kein Text gefunden.');
  });

  it('should upload a dropped file as multipart and prepend it to the list', async () => {
    await load([]);

    const file = new File(['inhalt'], 'neu.txt', { type: 'text/plain' });
    dropFiles(file);
    await fixture.whenStable();

    const request = httpMock.expectOne({ method: 'POST', url: documentsUrl });
    expect(request.request.body).toBeInstanceOf(FormData);
    const sent = (request.request.body as FormData).get('file') as File;
    expect(sent.name).toBe('neu.txt');
    expect(sent.type).toBe('text/plain');

    request.flush(summary({ filename: 'neu.txt', status: 'EXTRACTED' }));
    await fixture.whenStable();

    expect(text()).toContain('neu.txt');
    expect(text()).toContain('fertig');
  });

  it('should surface the backend problem detail when an upload is rejected', async () => {
    await load([]);

    dropFiles(new File([''], 'leer.txt', { type: 'text/plain' }));
    await fixture.whenStable();

    httpMock
      .expectOne({ method: 'POST', url: documentsUrl })
      .flush({ detail: 'Die Datei ist leer.' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();

    expect(text()).toContain('Die Datei ist leer.');
  });

  it('should delete a document', async () => {
    await load([summary()]);

    const button = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      'button.danger'
    )!;
    button.click();
    await fixture.whenStable();

    httpMock.expectOne({ method: 'DELETE', url: `${documentsUrl}/${summary().id}` }).flush(null);
    await fixture.whenStable();

    expect(text()).toContain('Noch keine Dokumente hochgeladen.');
  });

  function el<T extends HTMLElement>(selector: string): T {
    return (fixture.nativeElement as HTMLElement).querySelector<T>(selector)!;
  }

  function typeInto(selector: string, value: string): void {
    const field = el<HTMLInputElement>(selector);
    field.value = value;
    field.dispatchEvent(new Event('input'));
  }

  async function selectFirstDocument(): Promise<void> {
    el<HTMLInputElement>('table.documents tbody input[type="checkbox"]').click();
    await fixture.whenStable();
  }

  it('should not allow selecting a document without extracted text', async () => {
    await load([summary({ status: 'FAILED', charCount: 0 })]);

    const checkbox = el<HTMLInputElement>('table.documents tbody input[type="checkbox"]');
    expect(checkbox.disabled).toBe(true);
  });

  it('should estimate chunks and duration for the selection', async () => {
    await load([summary({ charCount: 60000 })]);
    await selectFirstDocument();

    // 60.000 Zeichen bei 6.000 je Abschnitt, gedrosselt auf etwa 10 pro Minute.
    expect(text()).toContain('10 Abschnitte');
    expect(text()).toContain('1 Minuten');
  });

  it('should start a generation job for a new deck', async () => {
    await load([summary()]);
    await selectFirstDocument();

    typeInto('input[name="newDeckName"]', '  ITIL Service Design  ');
    el<HTMLFormElement>('.generate form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    const request = httpMock.expectOne({
      method: 'POST',
      url: `${environment.apiBaseUrl}/generation-jobs`,
    });
    expect(request.request.body).toEqual({
      documentIds: [summary().id],
      deckId: null,
      newDeckName: 'ITIL Service Design',
      language: 'de',
      cardsPerChunk: 5,
      style: 'QA',
    });
    request.flush({ id: 'job-1', status: 'PENDING' });
    await fixture.whenStable();
  });

  it('should not start without a name for the new deck', async () => {
    await load([summary()]);
    await selectFirstDocument();

    el<HTMLFormElement>('.generate form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    httpMock.expectNone({ method: 'POST', url: `${environment.apiBaseUrl}/generation-jobs` });
    expect(text()).toContain('Namen für den neuen Stapel');
  });

  it('should surface a backend rejection of the generation request', async () => {
    await load([summary()]);
    await selectFirstDocument();

    typeInto('input[name="newDeckName"]', 'Neuer Stapel');
    el<HTMLFormElement>('.generate form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    httpMock
      .expectOne({ method: 'POST', url: `${environment.apiBaseUrl}/generation-jobs` })
      .flush(
        { detail: 'Aus diesen Dokumenten wurde kein Text extrahiert: scan.pdf' },
        { status: 400, statusText: 'Bad Request' }
      );
    await fixture.whenStable();

    expect(text()).toContain('Aus diesen Dokumenten wurde kein Text extrahiert: scan.pdf');
  });
});
