// Proxy de `ng serve` (D48) : le client (4200) parle à la gateway comme s'il était servi par elle,
// sans CORS. Port de la gateway : variable GATEWAY_PORT, sinon le `.env` de la racine (comme
// docker compose), sinon 8080 (D43).
import { readFileSync } from 'node:fs';

function portGateway() {
  if (process.env.GATEWAY_PORT) return process.env.GATEWAY_PORT;
  try {
    const env = readFileSync(new URL('../.env', import.meta.url), 'utf8');
    const ligne = env.split(/\r?\n/).find((l) => /^\s*GATEWAY_PORT\s*=/.test(l));
    if (ligne) return ligne.split('=')[1].trim();
  } catch {
    // pas de .env : port par défaut
  }
  return '8080';
}

const cible = `http://localhost:${portGateway()}`;
const route = { target: cible, secure: false, changeOrigin: false };

export default {
  '/auth': route,
  '/api': route,
  '/graphql': route,
  '/ws': route,
  '/events': route,
};
