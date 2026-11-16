// Nos scénarios, en plus des sept du sujet :
//   node observabilite/scenarios-perso.mjs
//   node observabilite/scenarios-perso.mjs --parc-arrete   (scénario 12, parc arrêté à la main)
// 8  : restitution dans une station hors service (503 venant du parc)
// 9  : restitution dans une station inconnue (404 venant du parc)
// 10 : rapport d'une location inexistante (404 sans appel au parc, donc sans les 500 ms)
// 11 : trois allers-retours COMEDIE -> TRIOLET (alimente les compteurs et les montants)
// 12 : le parc est arrêté : locations répond 503 car le parc ne répond pas
import { ABONNE, SECRET, URL_LOCATIONS, appeler, exigerLesServices } from '../resources/lib.mjs';

const parcArrete = process.argv.includes('--parc-arrete');
const abonne = { courriel: ABONNE, motDePasse: SECRET };
const ouvrir = (codeStation) =>
  appeler(`${URL_LOCATIONS}/locations`, { methode: 'POST', corps: { codeStation, ...abonne } });
const restituer = (reference, codeStation) =>
  appeler(`${URL_LOCATIONS}/locations/${reference}/restitution`, {
    methode: 'POST',
    corps: { codeStation },
  });

const SCENARIOS = parcArrete
  ? [{ nom: '12. parc arrêté', attendu: 503, joue: () => ouvrir('ST-COMEDIE') }]
  : [
      {
        nom: '8. restitution, station hors service',
        attendu: 503,
        joue: async () => {
          const ouverte = await ouvrir('ST-ARCEAUX');
          return restituer(ouverte.corps.reference, 'ST-PEYROU');
        },
      },
      {
        nom: '9. restitution, station inconnue',
        attendu: 404,
        joue: async () => {
          const ouverte = await ouvrir('ST-ARCEAUX');
          return restituer(ouverte.corps.reference, 'ST-AILLEURS');
        },
      },
      {
        nom: '10. rapport, location inexistante',
        attendu: 404,
        joue: () => appeler(`${URL_LOCATIONS}/locations/L-9999/rapport`),
      },
      {
        nom: '11. trois allers-retours COMEDIE -> TRIOLET',
        attendu: 200,
        joue: async () => {
          let derniere;
          for (let i = 0; i < 3; i += 1) {
            const ouverte = await ouvrir('ST-COMEDIE');
            derniere = await restituer(ouverte.corps.reference, 'ST-TRIOLET');
          }
          return derniere;
        },
      },
    ];

if (!parcArrete) {
  await exigerLesServices();
}

let ecarts = 0;
console.log('scénario'.padEnd(46), 'statut', 'attendu', '  durée');
for (const s of SCENARIOS) {
  const debut = performance.now();
  const r = await s.joue();
  const ms = Math.round(performance.now() - debut);
  const ok = r.statut === s.attendu;
  if (!ok) ecarts += 1;
  console.log(
    s.nom.padEnd(46),
    String(r.statut).padStart(6),
    String(s.attendu).padStart(7),
    `${String(ms).padStart(5)} ms`,
    ok ? '' : '  <-- ÉCART',
  );
}
process.exit(ecarts === 0 ? 0 : 1);
