import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UploadPage } from './upload';
import { DocumentSummary } from '../../core/models/document';
import { environment } from '../../../environments/environment';

const documentsUrl = `${environment.apiBaseUrl}/documents`;

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
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(UploadPage);
  });

  afterEach(() => httpMock.verify());

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  /** jsdom kennt kein DataTransfer, deshalb wird das Drop-Event von Hand gebaut. */
  function dropFiles(...files: File[]): void {
    const event = new Event('drop', { bubbles: true });
    Object.defineProperty(event, 'dataTransfer', { value: { files } });
    (fixture.nativeElement as HTMLElement).querySelector('.dropzone')!.dispatchEvent(event);
  }

  it('should list documents from the backend', async () => {
    await fixture.whenStable();
    httpMock.expectOne(documentsUrl).flush([summary(), summary({ id: 'x', filename: 'folien.pptx' })]);
    await fixture.whenStable();

    expect(text()).toContain('skript.pdf');
    expect(text()).toContain('folien.pptx');
    expect(text()).toContain('12.345');
  });

  it('should show the empty state when nothing is uploaded yet', async () => {
    await fixture.whenStable();
    httpMock.expectOne(documentsUrl).flush([]);
    await fixture.whenStable();

    expect(text()).toContain('Noch keine Dokumente hochgeladen.');
  });

  it('should show the failure reason of a document', async () => {
    await fixture.whenStable();
    httpMock.expectOne(documentsUrl).flush([
      summary({ status: 'FAILED', charCount: 0, errorMessage: 'Es wurde kein Text gefunden.' }),
    ]);
    await fixture.whenStable();

    expect(text()).toContain('FAILED');
    expect(text()).toContain('Es wurde kein Text gefunden.');
  });

  it('should upload a dropped file as multipart and prepend it to the list', async () => {
    await fixture.whenStable();
    httpMock.expectOne(documentsUrl).flush([]);
    await fixture.whenStable();

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
    await fixture.whenStable();
    httpMock.expectOne(documentsUrl).flush([]);
    await fixture.whenStable();

    dropFiles(new File([''], 'leer.txt', { type: 'text/plain' }));
    await fixture.whenStable();

    httpMock
      .expectOne({ method: 'POST', url: documentsUrl })
      .flush({ detail: 'Die Datei ist leer.' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();

    expect(text()).toContain('Die Datei ist leer.');
  });

  it('should delete a document', async () => {
    await fixture.whenStable();
    httpMock.expectOne(documentsUrl).flush([summary()]);
    await fixture.whenStable();

    const button = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      'button.danger'
    )!;
    button.click();
    await fixture.whenStable();

    httpMock.expectOne({ method: 'DELETE', url: `${documentsUrl}/${summary().id}` }).flush(null);
    await fixture.whenStable();

    expect(text()).toContain('Noch keine Dokumente hochgeladen.');
  });
});
