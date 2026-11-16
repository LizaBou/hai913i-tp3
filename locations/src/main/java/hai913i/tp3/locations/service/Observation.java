package hai913i.tp3.locations.service;

import org.slf4j.Logger;

import hai913i.tp3.locations.parc.MouvementRefuse;
import hai913i.tp3.locations.parc.ParcIndisponible;
import hai913i.tp3.locations.parc.StationInconnue;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

/**
 * Toute la télémétrie métier du service des locations, à un seul endroit.
 * Sans agent, ces objets ne font rien (no-op) : les tests ne changent pas.
 * Les dimensions des métriques sont bornées (stations connues, causes en liste fermée).
 */
final class Observation {

    static final String PORTEE = "hai913i.tp3.locations";

    static final Tracer TRACER = GlobalOpenTelemetry.getTracer(PORTEE, "1.0.0");
    private static final Meter METRIQUES = GlobalOpenTelemetry.getMeter(PORTEE);

    static final AttributeKey<String> STATION = AttributeKey.stringKey("station");
    static final AttributeKey<String> OPERATION = AttributeKey.stringKey("operation");
    static final AttributeKey<String> CAUSE = AttributeKey.stringKey("cause");

    /** Q3 : locations ouvertes, par station de départ. */
    static final LongCounter OUVERTES = METRIQUES
            .counterBuilder("locations.ouvertes")
            .setDescription("Locations ouvertes, par station de départ")
            .setUnit("{location}")
            .build();

    /** Q3 : locations clôturées, par station d'arrivée. */
    static final LongCounter CLOTUREES = METRIQUES
            .counterBuilder("locations.cloturees")
            .setDescription("Locations clôturées, par station d'arrivée")
            .setUnit("{location}")
            .build();

    /** Q3 : montants facturés, par station d'arrivée. */
    static final DoubleHistogram MONTANT = METRIQUES
            .histogramBuilder("locations.montant")
            .setDescription("Montant facturé à la clôture")
            .setUnit("EUR")
            .build();

    /** Q2 : refus et pannes, par opération et cause. */
    static final LongCounter REFUS = METRIQUES
            .counterBuilder("locations.refus")
            .setDescription("Opérations refusées ou en échec")
            .setUnit("{operation}")
            .build();

    private Observation() {
    }

    /** La cause d'un échec, dans une liste fermée. */
    static String cause(RuntimeException faute) {
        if (faute instanceof AbonneRefuse) {
            return "abonne-refuse";
        }
        if (faute instanceof StationInconnue) {
            return "station-inconnue";
        }
        if (faute instanceof MouvementRefuse) {
            return "mouvement-refuse";
        }
        if (faute instanceof LocationIntrouvable) {
            return "location-introuvable";
        }
        if (faute instanceof LocationDejaCloturee) {
            return "location-cloturee";
        }
        if (faute instanceof ParcIndisponible) {
            return faute.getCause() == null ? "parc-indisponible" : "parc-injoignable";
        }
        return "inattendu";
    }

    /** Une panne, c'est un échec que personne n'a décidé. Un refus est une décision du métier. */
    static boolean panne(String cause) {
        return cause.equals("parc-injoignable") || cause.equals("inattendu");
    }

    /**
     * Enregistre un échec sur le span courant, dans la métrique et dans le journal.
     * À appeler depuis un catch placé À L'INTÉRIEUR de la portée du span,
     * sinon la ligne de journal n'aurait pas de trace_id.
     * Jamais de courriel ni de mot de passe.
     */
    static void echec(Logger journal, Span span, String operation, RuntimeException faute,
                      String station) {
        String cause = cause(faute);
        span.setAttribute("velos.resultat", cause);
        REFUS.add(1, Attributes.of(OPERATION, operation, CAUSE, cause));

        if (panne(cause)) {
            span.setStatus(StatusCode.ERROR, cause);
            span.recordException(faute);
            journal.atError()
                    .setMessage("operation.echec")
                    .addKeyValue("operation", operation)
                    .addKeyValue("cause", cause)
                    .addKeyValue("station", station)
                    .setCause(faute)
                    .log();
        } else {
            span.addEvent("refus", Attributes.of(CAUSE, cause));
            journal.atWarn()
                    .setMessage("operation.refusee")
                    .addKeyValue("operation", operation)
                    .addKeyValue("cause", cause)
                    .addKeyValue("station", station)
                    .log();
        }
    }
}