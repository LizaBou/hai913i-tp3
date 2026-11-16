/** Point d'entrée, et uniquement cela. */
import { bootstrapApplication } from '@angular/platform-browser';

import { App } from './app/app';
import { appConfig } from './app/app.config';
import { configurationParDefaut } from './app/core/configuration';
import { demarrerTelemetrieNavigateur } from './app/core/telemetrie-navigateur';

demarrerTelemetrieNavigateur(configurationParDefaut());
bootstrapApplication(App, appConfig).catch((err) => console.error(err));
