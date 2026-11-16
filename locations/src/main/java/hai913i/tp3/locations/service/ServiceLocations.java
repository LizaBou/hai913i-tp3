package hai913i.tp3.locations.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import hai913i.tp3.locations.depot.LocationDepot;
import hai913i.tp3.locations.domaine.Location;
import hai913i.tp3.locations.domaine.Tarif;
import hai913i.tp3.locations.parc.MouvementDistant;
import hai913i.tp3.locations.parc.ParcDistant;
import hai913i.tp3.locations.parc.StationDistante;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

/**
 * Les règles de la location.
 * Elle ignore qu'il existe un protocole HTTP.
 */
@Service
public class ServiceLocations {

    private static final Logger journal = LoggerFactory.getLogger(ServiceLocations.class);

    private final LocationDepot depot;
    private final ParcDistant parc;
    private final Abonnes abonnes;
    private final Clock horloge;

    ServiceLocations(LocationDepot depot, ParcDistant parc, Abonnes abonnes, Clock horloge) {
        this.depot = depot;
        this.parc = parc;
        this.abonnes = abonnes;
        this.horloge = horloge;
    }

    /** Les stations, telles que le parc les décrit. */
    public List<StationDistante> stations() {
        return parc.stations();
    }

    public List<Location> locations() {
        return depot.toutes();
    }

    public Location location(String reference) {
        return depot.parReference(reference)
                .orElseThrow(() -> new LocationIntrouvable(reference));
    }

    /**
     * Ouvre une location : vérifie l'abonné, fait retirer un vélo par le parc,
     * enregistre le départ. Le mot de passe s'arrête à Abonnes.verifier ;
     * de l'adresse, seul le domaine est conservé.
     */
    public Location ouvrir(String codeStation, String courriel, String motDePasse) {
        Span span = Observation.TRACER.spanBuilder("location.ouvrir").startSpan();
        try (Scope portee = span.makeCurrent()) {
            // Le catch reste À L'INTÉRIEUR de la portée : l'échec est journalisé
            // pendant que le span est encore courant (donc avec son trace_id).
            try {
                span.setAttribute("velos.station.code", codeStation);
                String domaine = abonnes.verifier(courriel, motDePasse);
                span.setAttribute("velos.abonne.domaine", domaine);
                MouvementDistant mouvement = parc.retirer(codeStation);
                Location location = depot.ouvrir(mouvement.identifiantVelo(), codeStation,
                        horloge.instant(), domaine);

                span.setAttribute("velos.location.reference", location.reference());
                span.setAttribute("velos.velo.id", location.identifiantVelo());
                span.setAttribute("velos.resultat", "ouverte");
                Observation.OUVERTES.add(1, Attributes.of(Observation.STATION, codeStation));
                journal.atInfo()
                        .setMessage("location.ouverte")
                        .addKeyValue("location.reference", location.reference())
                        .addKeyValue("station", codeStation)
                        .addKeyValue("velo", location.identifiantVelo())
                        .addKeyValue("abonne.domaine", domaine)
                        .log();
                return location;
            } catch (RuntimeException faute) {
                Observation.echec(journal, span, "ouverture", faute, codeStation);
                throw faute;
            }
        } finally {
            span.end();
        }
    }

    /** Clôture une location : fait déposer le vélo, calcule durée et montant. */
    public Location restituer(String reference, String codeStation) {
        Span span = Observation.TRACER.spanBuilder("location.restituer").startSpan();
        try (Scope portee = span.makeCurrent()) {
            try {
                span.setAttribute("velos.location.reference", reference);
                span.setAttribute("velos.station.code", codeStation);
                Location location = location(reference);
                if (!location.enCours()) {
                    throw new LocationDejaCloturee(reference);
                }
                parc.deposer(codeStation, location.identifiantVelo());
                Instant fin = horloge.instant();
                int duree = Tarif.dureeMinutes(location.debut(), fin);
                BigDecimal montant = Tarif.montant(duree);
                Location close = depot.cloturer(reference, codeStation, fin, duree, montant);

                span.setAttribute("velos.duree.minutes", duree);
                span.setAttribute("velos.montant.eur", montant.doubleValue());
                span.setAttribute("velos.resultat", "cloturee");
                Attributes station = Attributes.of(Observation.STATION, codeStation);
                Observation.CLOTUREES.add(1, station);
                Observation.MONTANT.record(montant.doubleValue(), station);
                journal.atInfo()
                        .setMessage("location.cloturee")
                        .addKeyValue("location.reference", reference)
                        .addKeyValue("station", codeStation)
                        .addKeyValue("duree.minutes", duree)
                        .addKeyValue("montant.eur", montant)
                        .log();
                return close;
            } catch (RuntimeException faute) {
                Observation.echec(journal, span, "restitution", faute, codeStation);
                throw faute;
            }
        } finally {
            span.end();
        }
    }

    /** Établit le rapport d'exploitation (l'opération lente : elle appelle le parc). */
    public Rapport rapport(String reference) {
        Span span = Observation.TRACER.spanBuilder("location.rapport").startSpan();
        try (Scope portee = span.makeCurrent()) {
            try {
                span.setAttribute("velos.location.reference", reference);
                Location location = location(reference);
                span.setAttribute("velos.station.code", location.codeDepart());
                int indice = parc.indiceRotation(location.codeDepart());
                int locationsDepuis = depot.comptePourDepart(location.codeDepart());

                span.setAttribute("velos.indice.rotation", indice);
                span.setAttribute("velos.locations.depuis", locationsDepuis);
                journal.atInfo()
                        .setMessage("rapport.etabli")
                        .addKeyValue("location.reference", reference)
                        .addKeyValue("station", location.codeDepart())
                        .addKeyValue("indice.rotation", indice)
                        .addKeyValue("locations.depuis", locationsDepuis)
                        .log();
                return new Rapport(reference, location.codeDepart(), indice, locationsDepuis);
            } catch (RuntimeException faute) {
                Observation.echec(journal, span, "rapport", faute, null);
                throw faute;
            }
        } finally {
            span.end();
        }
    }
}