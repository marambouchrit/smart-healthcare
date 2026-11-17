// Vérifie les données fictives de contracts/seed/ : JSON valide, formats, énumérations, références.
// Usage : node scripts/check-seed.mjs   (code de sortie 1 en cas d'erreur)
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = join(dirname(fileURLToPath(import.meta.url)), '..', 'contracts', 'seed');
const errors = [];
const load = (f) => {
  try { return JSON.parse(readFileSync(join(dir, f), 'utf8')); }
  catch (e) { errors.push(`${f} : ${e.message}`); return []; }
};

const services = load('services-hospitaliers.json');
const medecins = load('medecins.json');
const patients = load('patients.json');
const couvertures = load('couvertures-cnam.json');
const lettres = load('lettres-cles.json');
const traitements = load('traitements.json');

const check = (cond, msg) => { if (!cond) errors.push(msg); };
const unique = (arr, key, f) => {
  const seen = new Set();
  for (const o of arr) { check(!seen.has(o[key]), `${f} : ${key} en double « ${o[key]} »`); seen.add(o[key]); }
  return seen;
};
const isDate = (s) => /^\d{4}-\d{2}-\d{2}$/.test(s) && !Number.isNaN(Date.parse(s));

const serviceCodes = unique(services, 'code', 'services-hospitaliers');
const medecinIds = unique(medecins, 'id', 'medecins');
const patientIds = unique(patients, 'id', 'patients');
unique(couvertures, 'codeVignette', 'couvertures-cnam');
unique(couvertures, 'patientId', 'couvertures-cnam');
unique(lettres, 'code', 'lettres-cles');
unique(traitements, 'id', 'traitements');

for (const m of medecins) {
  check(/^MED-\d{3}$/.test(m.id), `medecins : id invalide « ${m.id} »`);
  check(serviceCodes.has(m.serviceCode), `medecins ${m.id} : service inconnu « ${m.serviceCode} »`);
}
for (const p of patients) {
  check(/^TN-\d{7}$/.test(p.id), `patients : id invalide « ${p.id} »`);
  check(isDate(p.dateNaissance), `patients ${p.id} : dateNaissance invalide`);
  check(['M', 'F'].includes(p.sexe), `patients ${p.id} : sexe invalide`);
  check(serviceCodes.has(p.serviceCode), `patients ${p.id} : service inconnu « ${p.serviceCode} »`);
  check(['FAIBLE', 'MOYEN', 'ELEVE'].includes(p.niveauRisque), `patients ${p.id} : niveauRisque invalide`);
  check(medecinIds.has(p.medecinTraitantId), `patients ${p.id} : médecin inconnu « ${p.medecinTraitantId} »`);
}
for (const c of couvertures) {
  check(patientIds.has(c.patientId), `couvertures-cnam : patient inconnu « ${c.patientId} »`);
  check(/^VIG-\d{4}-\d{6}$/.test(c.codeVignette), `couvertures-cnam : vignette invalide « ${c.codeVignette} »`);
  check(['PUBLIQUE', 'PRIVEE', 'REMBOURSEMENT'].includes(c.filiere), `couvertures-cnam ${c.patientId} : filière invalide`);
  check([80, 50].includes(c.tauxRemboursement), `couvertures-cnam ${c.patientId} : taux invalide`);
  check(isDate(c.dateDebut) && isDate(c.dateFin) && c.dateDebut <= c.dateFin, `couvertures-cnam ${c.patientId} : période invalide`);
}
for (const l of lettres) {
  check(/^[A-Z]{1,3}$/.test(l.code), `lettres-cles : code invalide « ${l.code} »`);
  check(typeof l.valeurUnitaireTND === 'number' && l.valeurUnitaireTND > 0, `lettres-cles ${l.code} : valeur invalide`);
}
for (const t of traitements) {
  check(/^TRT-\d{3}$/.test(t.id), `traitements : id invalide « ${t.id} »`);
  check(patientIds.has(t.patientId), `traitements ${t.id} : patient inconnu « ${t.patientId} »`);
  check(medecinIds.has(t.medecinId), `traitements ${t.id} : médecin inconnu « ${t.medecinId} »`);
  check(['EN_COURS', 'TERMINE'].includes(t.statut), `traitements ${t.id} : statut invalide`);
  check(isDate(t.dateDebut), `traitements ${t.id} : dateDebut invalide`);
  check(t.statut === 'EN_COURS' ? t.dateFin === null : isDate(t.dateFin), `traitements ${t.id} : dateFin incohérente avec le statut`);
}

const plannings = load('plannings.json');
const JOURS = ['LUNDI', 'MARDI', 'MERCREDI', 'JEUDI', 'VENDREDI', 'SAMEDI', 'DIMANCHE'];
const isHeure = (s) => /^([01]\d|2[0-3]):[0-5]\d$/.test(s);
for (const p of plannings) {
  check(medecinIds.has(p.medecinId), `plannings : médecin inconnu « ${p.medecinId} »`);
  check(Array.isArray(p.jours) && p.jours.length > 0 && p.jours.every((j) => JOURS.includes(j)), `plannings ${p.medecinId} : jours invalides`);
  check(isHeure(p.debut) && isHeure(p.fin) && p.debut < p.fin, `plannings ${p.medecinId} : plage horaire invalide`);
  check(Number.isInteger(p.dureeMinutes) && p.dureeMinutes > 0, `plannings ${p.medecinId} : durée invalide`);
}
check(plannings.some((p) => medecins.find((m) => m.id === p.medecinId)?.serviceCode === 'URGENCES'),
  'plannings : aucun planning pour le service URGENCES');

if (errors.length) {
  console.error(`ÉCHEC : ${errors.length} erreur(s)`);
  for (const e of errors) console.error(' - ' + e);
  process.exit(1);
}
console.log(`OK : ${services.length} services, ${medecins.length} médecins, ${patients.length} patients, ` +
  `${couvertures.length} couvertures, ${lettres.length} lettres-clés, ${traitements.length} traitements, ${plannings.length} plannings`);
