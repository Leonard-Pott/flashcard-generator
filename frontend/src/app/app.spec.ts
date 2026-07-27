import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { App } from './app';
import { environment } from '../environments/environment';

const healthUrl = `${environment.apiBaseUrl}/health`;
const healthResponse = {
  status: 'UP',
  application: 'flashcard-generator',
  database: 'UP',
  timestamp: '2026-01-01T00:00:00Z',
};

describe('App', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
    httpMock.expectOne(healthUrl).flush(healthResponse);
  });

  it('should render the health data once the backend responds', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    httpMock.expectOne(healthUrl).flush(healthResponse);
    await fixture.whenStable();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('flashcard-generator');
    expect(text).toContain('UP');
  });

  it('should show a hint when the backend is unreachable', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    httpMock
      .expectOne(healthUrl)
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    await fixture.whenStable();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Backend nicht erreichbar');
  });
});
