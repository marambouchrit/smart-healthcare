// Contrôle du schéma BPMN 2.0 (P5.6) avec bpmn-moddle, la bibliothèque de lecture de bpmn.io / Camunda Modeler.
// Prérequis (une fois) : npm install --no-save --prefix .outils bpmn-moddle@10.3.1
// Usage : node scripts/check-bpmn.mjs docs/schemas/workflow-prise-en-charge.bpmn
// Vérifie : lecture sans avertissement, références résolues, chaque élément du processus et chaque flux de messages
// dessiné dans le diagramme, chaque flux de séquence relié des deux côtés. Code de sortie 0 si conforme.
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const racine = resolve(import.meta.dirname, '..');
const { BpmnModdle } = await import(pathToFileURL(resolve(racine, '.outils/node_modules/bpmn-moddle/dist/index.js')).href);

const fichier = process.argv[2];
const xml = readFileSync(fichier, 'utf8');
const { rootElement: definitions, warnings } = await new BpmnModdle().fromXML(xml);
const erreurs = warnings.map((w) => `lecture : ${w.message}`);

const collaboration = definitions.rootElements.find((e) => e.$type === 'bpmn:Collaboration');
const processus = definitions.rootElements.filter((e) => e.$type === 'bpmn:Process');
const plan = definitions.diagrams[0].plane;
const dessines = new Set(plan.planeElement.map((d) => d.bpmnElement?.id));

const noeuds = processus.flatMap((p) => p.flowElements);
for (const e of noeuds) {
  if (!dessines.has(e.id)) erreurs.push(`non dessiné : ${e.$type} ${e.id}`);
  if (e.$type === 'bpmn:SequenceFlow' && (!e.sourceRef || !e.targetRef)) erreurs.push(`flux incomplet : ${e.id}`);
}
for (const m of collaboration?.messageFlows ?? []) {
  if (!dessines.has(m.id)) erreurs.push(`flux de messages non dessiné : ${m.id}`);
  if (!m.sourceRef || !m.targetRef) erreurs.push(`flux de messages incomplet : ${m.id}`);
}
for (const p of collaboration?.participants ?? []) {
  if (!dessines.has(p.id)) erreurs.push(`participant non dessiné : ${p.id}`);
}

const compter = (type) => noeuds.filter((e) => e.$type === type).length;
console.log(`${fichier}`);
console.log(`  participants : ${collaboration?.participants.length ?? 0}, flux de messages : ${collaboration?.messageFlows.length ?? 0}`);
console.log(`  tâches de service : ${compter('bpmn:ServiceTask')}, d'envoi : ${compter('bpmn:SendTask')}, de script : ${compter('bpmn:ScriptTask')}`);
console.log(`  passerelles : ${compter('bpmn:ExclusiveGateway')}, événements : ${compter('bpmn:StartEvent')} début, ${compter('bpmn:EndEvent')} fin, ${compter('bpmn:BoundaryEvent')} d'erreur`);
console.log(`  flux de séquence : ${compter('bpmn:SequenceFlow')}, formes et liens dessinés : ${plan.planeElement.length}`);
if (erreurs.length) {
  erreurs.forEach((e) => console.log(`  ÉCHEC ${e}`));
  process.exit(1);
}
console.log('BPMN CONFORME');
