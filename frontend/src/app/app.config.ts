import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withNavigationErrorHandler } from '@angular/router';

import { routes } from './app.routes';
import { provideLivHttp } from './core/http/provide-liv-http';
import { redirectToLoadError } from './core/navigation/navigation-errors';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withNavigationErrorHandler(redirectToLoadError)),
    provideLivHttp(),
  ],
};
