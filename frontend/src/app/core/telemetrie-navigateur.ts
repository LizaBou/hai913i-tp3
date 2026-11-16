import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { registerInstrumentations } from '@opentelemetry/instrumentation';
import { FetchInstrumentation } from '@opentelemetry/instrumentation-fetch';
import { resourceFromAttributes } from '@opentelemetry/resources';
import { BatchSpanProcessor, WebTracerProvider } from '@opentelemetry/sdk-trace-web';
import { ATTR_SERVICE_NAME, ATTR_SERVICE_VERSION } from '@opentelemetry/semantic-conventions';

import type { ConfigurationApplication } from './configuration';

function commencePar(origine: string): RegExp {
    return new RegExp('^' + origine.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
}

/** Seul fichier qui dépend des paquets OpenTelemetry expérimentaux du navigateur. */
export function demarrerTelemetrieNavigateur(config: ConfigurationApplication): void {
    if (!config.urlCollecteur) {
        return;
    }
    try {
        const fournisseur = new WebTracerProvider({
            resource: resourceFromAttributes({
                [ATTR_SERVICE_NAME]: config.nomService,
                [ATTR_SERVICE_VERSION]: config.versionService,
            }),
            spanProcessors: [
                new BatchSpanProcessor(
                    new OTLPTraceExporter({ url: `${config.urlCollecteur}/v1/traces` }),
                ),
            ],
        });
        fournisseur.register();

        registerInstrumentations({
            instrumentations: [
                new FetchInstrumentation({
                    propagateTraceHeaderCorsUrls: [commencePar(config.urlLocations)],
                    ignoreUrls: [commencePar(config.urlCollecteur)],
                }),
            ],
        });
    } catch (erreur) {
        console.warn('télémétrie du navigateur indisponible', erreur);
    }
}