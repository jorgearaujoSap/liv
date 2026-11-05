import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CoreApi } from './api/core/core-api';
import { getCurrentUser } from './api/core/functions';
import { appConfig } from './app.config';

// La configuración real de la aplicación registra el interceptor de la feature 001 (T21).
describe('appConfig', () => {
  it('las peticiones de la aplicación pasan por el interceptor', async () => {
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);

    const result = TestBed.inject(CoreApi).invoke(getCurrentUser);
    const request = http.expectOne('/api/v1/me');
    expect(request.request.headers.has('X-Correlation-Id')).toBe(true);
    request.flush({});
    await result;
    http.verify();
  });
});
