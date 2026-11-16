package hai913i.tp3.parc.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import hai913i.tp3.parc.depot.ParcDepot;
import hai913i.tp3.parc.domaine.EtatVelo;
import hai913i.tp3.parc.domaine.Station;
import hai913i.tp3.parc.domaine.Velo;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;

/**
 * Les règles du parc.
 *
 * <p>Cette classe ignore qu'il existe un protocole HTTP.
 * Elle rend des objets du métier ou lève une faute du métier.
 * C'est la couche API qui décide du code de statut.</p>
 */
@Service
public class ServiceParc {

    private static final Logger journal = LoggerFactory.getLogger(ServiceParc.class);

    private final ParcDepot depot;
    private final int consolidationMs;

    ServiceParc(ParcDepot depot, @Value("${parc.consolidation-ms:500}") int consolidationMs) {
        this.depot = depot;
        this.consolidationMs = consolidationMs;
    }

    public List<Station> stations() {
        return depot.stations();
    }

    public Station station(String code) {
        return depot.parCode(code).orElseThrow(() -> {
            journal.atWarn()
                    .setMessage("station.inconnue")
                    .addKeyValue("station", code)
                    .log();
            return new StationIntrouvable(code);
        });
    }

    public int disponibles(String code) {
        return depot.disponibles(code);
    }

    public Velo retirer(String code) {
        Station station = station(code);
        if (station.horsService()) {
            refuser("retrait", code, "station-hors-service");
            throw new StationIndisponible(code);
        }
        Velo velo = depot.premierDisponible(code)
                .orElseThrow(() -> {
                    refuser("retrait", code, "aucun-velo-disponible");
                    return new MouvementImpossible(
                            "aucun vélo disponible à la station " + code);
                });
        depot.retirer(velo.identifiant());
        journal.atInfo()
                .setMessage("velo.retire")
                .addKeyValue("station", code)
                .addKeyValue("velo", velo.identifiant())
                .log();
        return new Velo(velo.identifiant(), null, EtatVelo.EN_COURSE);
    }

    public Velo deposer(String code, String identifiant) {
        Station station = station(code);
        if (station.horsService()) {
            refuser("depot", code, "station-hors-service");
            throw new StationIndisponible(code);
        }
        Velo velo = depot.velo(identifiant)
                .orElseThrow(() -> {
                    refuser("depot", code, "velo-inconnu");
                    return new MouvementImpossible("vélo inconnu : " + identifiant);
                });
        if (velo.etat() != EtatVelo.EN_COURSE) {
            refuser("depot", code, "velo-pas-en-course");
            throw new MouvementImpossible("le vélo " + identifiant + " n'est pas en course");
        }
        if (depot.presents(code) >= station.capacite()) {
            refuser("depot", code, "bornes-occupees");
            throw new MouvementImpossible("toutes les bornes de " + code + " sont occupées");
        }
        depot.deposer(identifiant, code);
        journal.atInfo()
                .setMessage("velo.depose")
                .addKeyValue("station", code)
                .addKeyValue("velo", identifiant)
                .log();
        return new Velo(identifiant, code, EtatVelo.DISPONIBLE);
    }

    public int indiceRotation(String code) {
        Station station = station(code);
        List<Velo> velos = depot.velosDe(station.code());
        consolider(station.code(), consolidationMs);
        int indice = 0;
        if (!velos.isEmpty()) {
            long retirables = velos.stream().filter(v -> v.etat() == EtatVelo.DISPONIBLE).count();
            indice = (int) Math.round(100.0 * retirables / velos.size());
        }
        journal.atInfo()
                .setMessage("indice.consolide")
                .addKeyValue("station", station.code())
                .addKeyValue("indice.rotation", indice)
                .addKeyValue("consolidation.ms", consolidationMs)
                .log();
        return indice;
    }

    /** L'étape lente, isolée pour avoir son propre span (créé par l'agent). */
    @WithSpan("parc.consolider-indice")
    private static void consolider(@SpanAttribute("velos.station.code") String code,
                                   @SpanAttribute("parc.consolidation.ms") int millisecondes) {
        attendre(millisecondes);
    }

    /** Un refus du parc est une décision du métier : WARN, pas ERROR. */
    private static void refuser(String operation, String code, String cause) {
        journal.atWarn()
                .setMessage("mouvement.refuse")
                .addKeyValue("operation", operation)
                .addKeyValue("station", code)
                .addKeyValue("cause", cause)
                .log();
    }

    private static void attendre(int millisecondes) {
        try {
            Thread.sleep(millisecondes);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}