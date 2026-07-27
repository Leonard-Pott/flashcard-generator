import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { JobProgressPage } from './job-progress';
import { GenerationJob } from '../../core/models/generation';
import { environment } from '../../../environments/environment';

const jobId = 'j1000000-0000-4000-8000-000000000001';
const jobUrl = `${environment.apiBaseUrl}/generation-jobs/${jobId}`;

function job(overrides: Partial<GenerationJob> = {}): GenerationJob {
  return {
    id: jobId,
    status: 'RUNNING',
    targetDeckId: 'd1000000-0000-4000-8000-000000000001',
    documentIds: ['doc-1'],
    language: 'de',
    style: 'QA',
    cardsPerChunk: 5,
    totalChunks: 10,
    processedChunks: 2,
    skippedChunks: 0,
    generatedCardCount: 8,
    llmModel: 'gemini-3.6-flash',
    errorMessage: null,
    createdAt: '2026-07-27T10:00:00Z',
    finishedAt: null,
    ...overrides,
  };
}

describe('JobProgressPage', () => {
  let fixture: ComponentFixture<JobProgressPage>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    // Nur setInterval fälschen: würden auch setTimeout und Microtasks gefälscht,
    // käme Angulars whenStable() nie zurück und jeder Test liefe in den Timeout.
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });

    await TestBed.configureTestingModule({
      imports: [JobProgressPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(JobProgressPage);
    fixture.componentRef.setInput('id', jobId);
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  /** Wartet den Polling-Takt ab, ohne echte Zeit zu verbrauchen. */
  async function tick(ms: number): Promise<void> {
    await vi.advanceTimersByTimeAsync(ms);
    await fixture.whenStable();
  }

  it('should poll immediately and show progress', async () => {
    await fixture.whenStable();

    httpMock.expectOne(jobUrl).flush(job());
    await fixture.whenStable();

    expect(text()).toContain('Läuft');
    expect(text()).toContain('2 von 10');
    expect(text()).toContain('20 %');
    expect(text()).toContain('gemini-3.6-flash');
  });

  it('should keep polling every two seconds while the job runs', async () => {
    await fixture.whenStable();
    httpMock.expectOne(jobUrl).flush(job({ processedChunks: 2 }));
    await fixture.whenStable();

    await tick(2000);
    httpMock.expectOne(jobUrl).flush(job({ processedChunks: 7, generatedCardCount: 26 }));
    await fixture.whenStable();

    expect(text()).toContain('7 von 10');
    expect(text()).toContain('26');
  });

  it('should stop polling once the job is finished', async () => {
    await fixture.whenStable();
    httpMock.expectOne(jobUrl).flush(job({ processedChunks: 2 }));
    await fixture.whenStable();

    await tick(2000);
    httpMock.expectOne(jobUrl).flush(
      job({ status: 'COMPLETED', processedChunks: 10, generatedCardCount: 41, finishedAt: '2026-07-27T10:05:00Z' })
    );
    await fixture.whenStable();

    expect(text()).toContain('Fertig');
    expect(text()).toContain('Zum Stapel');

    // Keine weiteren Anfragen mehr, auch nach mehreren Takten nicht.
    await tick(6000);
    httpMock.expectNone(jobUrl);
  });

  it('should show the estimated remaining time', async () => {
    await fixture.whenStable();
    httpMock.expectOne(jobUrl).flush(job({ totalChunks: 30, processedChunks: 0 }));
    await fixture.whenStable();

    // 30 offene Abschnitte bei etwa 10 Anfragen pro Minute.
    expect(text()).toContain('noch etwa 3 Minuten');
  });

  it('should show skipped chunks and the note of a completed run', async () => {
    await fixture.whenStable();
    httpMock.expectOne(jobUrl).flush(
      job({
        status: 'COMPLETED',
        processedChunks: 10,
        skippedChunks: 2,
        errorMessage: '2 von 10 Abschnitten uebersprungen. Abschnitt 3: HTTP 429',
      })
    );
    await fixture.whenStable();

    expect(text()).toContain('Übersprungene Abschnitte');
    expect(text()).toContain('Abschnitt 3: HTTP 429');
  });

  it('should show the failure reason and stop polling', async () => {
    await fixture.whenStable();
    httpMock.expectOne(jobUrl).flush(
      job({ status: 'FAILED', errorMessage: 'Kein einziger Abschnitt konnte verarbeitet werden.' })
    );
    await fixture.whenStable();

    expect(text()).toContain('Fehlgeschlagen');
    expect(text()).toContain('Kein einziger Abschnitt');

    await tick(4000);
    httpMock.expectNone(jobUrl);
  });

  it('should stop polling when the request fails', async () => {
    await fixture.whenStable();
    httpMock
      .expectOne(jobUrl)
      .flush({ detail: 'Generierungslauf nicht gefunden' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(text()).toContain('Generierungslauf nicht gefunden');

    await tick(4000);
    httpMock.expectNone(jobUrl);
  });
});
