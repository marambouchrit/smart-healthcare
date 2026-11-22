// Copie dans public/seed/ les fichiers du référentiel affichés par le client (noms des médecins et
// des services). Source unique : contracts/seed/ (D49) ; Angular n'accepte pas d'assets hors du projet.
// Lancé automatiquement par `npm start`, `npm run build` et `npm test` (scripts pre*).
import { copyFileSync, mkdirSync } from 'node:fs';

const FICHIERS = ['medecins.json', 'services-hospitaliers.json'];
const source = new URL('../contracts/seed/', import.meta.url);
const cible = new URL('./public/seed/', import.meta.url);

mkdirSync(cible, { recursive: true });
for (const f of FICHIERS) copyFileSync(new URL(f, source), new URL(f, cible));
console.log(`Référentiel copié depuis contracts/seed/ : ${FICHIERS.join(', ')}`);
