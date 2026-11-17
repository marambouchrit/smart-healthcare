#!/usr/bin/env node
// Affiche l'état de ROADMAP.md : avancement par phase et tâche suivante.
// Usage : node scripts/roadmap-status.mjs   (depuis n'importe quel dossier du projet)
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
let text;
try {
  text = readFileSync(join(root, "ROADMAP.md"), "utf8");
} catch {
  console.log("ROADMAP.md introuvable à la racine du projet.");
  process.exit(0);
}

const phases = [];
let current = null;
for (const line of text.split(/\r?\n/)) {
  const ph = line.match(/^##\s+(Phase\s+\d+.*)$/);
  if (ph) { current = { name: ph[1].trim(), tasks: [] }; phases.push(current); continue; }
  const t = line.match(/^\s*-\s+\[( |x|X|~)\]\s+(\S+)\s+(.*)$/);
  if (t && current) {
    const s = t[1].toLowerCase() === "x" ? "done" : t[1] === "~" ? "doing" : "todo";
    current.tasks.push({ state: s, id: t[2], label: t[3].trim() });
  }
}

const all = phases.flatMap(p => p.tasks);
const done = all.filter(t => t.state === "done").length;
const pct = all.length ? Math.round((done / all.length) * 100) : 0;

console.log(`=== Avancement Smart Healthcare : ${done}/${all.length} tâches vérifiées (${pct} %) ===`);
for (const p of phases) {
  const d = p.tasks.filter(t => t.state === "done").length;
  const w = p.tasks.filter(t => t.state === "doing").length;
  const mark = d === p.tasks.length && p.tasks.length ? "OK " : w || d ? ">> " : "   ";
  console.log(`${mark}${p.name.padEnd(48)} ${d}/${p.tasks.length}${w ? `  (${w} en cours)` : ""}`);
}

const doing = all.filter(t => t.state === "doing");
if (doing.length) {
  console.log("\nEn cours :");
  doing.forEach(t => console.log(`  [~] ${t.id} ${t.label}`));
}
const next = all.find(t => t.state === "todo");
const activePhase = phases.find(p => p.tasks.some(t => t.state !== "done"));
console.log(`\nPhase active : ${activePhase ? activePhase.name : "toutes les phases sont terminées"}`);
console.log(`Tâche suivante : ${next ? `${next.id} ${next.label}` : "aucune"}`);

const bonusStarted = phases.find(p => /^Phase\s+6/.test(p.name))?.tasks.some(t => t.state !== "todo");
const coreDone = phases.filter(p => !/^Phase\s+6/.test(p.name)).every(p => p.tasks.every(t => t.state === "done"));
if (bonusStarted && !coreDone) console.log("\nATTENTION : un bonus (phase 6) est commencé alors que les phases 0 à 5 ne sont pas terminées.");
console.log("\nRappel : sources de vérité = docs/sujet/SUJET.md, ROADMAP.md, docs/DECISIONS.md, contracts/.");
