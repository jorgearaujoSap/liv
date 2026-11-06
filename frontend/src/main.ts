import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { whenOpened } from './app/core/browser/when-opened';

// Durante la precarga del navegador la aplicación no arranca: espera a que la persona abra la página.
whenOpened(document)
  .then(() => bootstrapApplication(App, appConfig))
  .catch((err) => console.error(err));
