import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ApiService } from './api.service';
import { environment } from '../../../environments/environment';

describe('ApiService', () => {
  let service: ApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should call GET /api/health on the configured backend', () => {
    const expected = {
      status: 'UP',
      application: 'flashcard-generator',
      database: 'UP',
      timestamp: '2026-01-01T00:00:00Z',
    };

    service.health().subscribe((health) => expect(health).toEqual(expected));

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/health`);
    expect(req.request.method).toBe('GET');
    req.flush(expected);
  });
});
