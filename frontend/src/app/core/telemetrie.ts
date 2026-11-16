import {
    context,
    isSpanContextValid,
    SpanStatusCode,
    trace,
    type Context,
} from '@opentelemetry/api';

const PORTEE = 'velos-frontend';

let contexteGeste: Context | null = null;

/**
 * Exécute un geste de l'utilisateur dans un span « ui.<nom> ».
 *
 * À la fin du geste, une seule ligne de journal structurée est écrite dans la
 * console, avec le TraceId tenu depuis le début du geste. Le contexte
 * OpenTelemetry est déjà dépilé à ce moment-là, d'où le TraceId gardé en
 * variable. Rien de ce que l'utilisateur saisit n'y figure.
 */
export async function suivreGeste<T>(nom: string, travail: () => Promise<T>): Promise<T> {
    const span = trace.getTracer(PORTEE).startSpan(`ui.${nom}`);
    const identite = span.spanContext();
    const debut = performance.now();
    const precedent = contexteGeste;
    contexteGeste = trace.setSpan(context.active(), span);
    let resultat: 'succes' | 'echec' = 'succes';
    try {
        return await travail();
    } catch (cause) {
        resultat = 'echec';
        span.setStatus({
            code: SpanStatusCode.ERROR,
            message: cause instanceof Error ? cause.message : String(cause),
        });
        if (cause instanceof Error) {
            span.recordException(cause);
        }
        throw cause;
    } finally {
        contexteGeste = precedent;
        span.end();
        if (isSpanContextValid(identite)) {
            const ligne = JSON.stringify({
                evenement: 'ui.geste',
                geste: nom,
                resultat,
                duree_ms: Math.round(performance.now() - debut),
                trace_id: identite.traceId,
            });
            if (resultat === 'succes') {
                console.info(ligne);
            } else {
                console.warn(ligne);
            }
        }
    }
}

/** Lance l'appel dans le contexte du geste en cours : un seul TraceId par geste. */
export function dansLeGeste<T>(appel: () => T): T {
    return contexteGeste ? context.with(contexteGeste, appel) : appel();
}