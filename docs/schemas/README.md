# Schémas (P5.6)

| Schéma | Fichier | Format |
|---|---|---|
| Architecture microservices | [`architecture.md`](architecture.md) | Mermaid (affiché par GitHub) |
| Workflow de prise en charge (BPMN) | [`workflow-prise-en-charge.bpmn`](workflow-prise-en-charge.bpmn) | BPMN 2.0 XML avec diagramme (Camunda Modeler, https://demo.bpmn.io) |
| Workflow, message par message | [`workflow.md`](workflow.md) | Mermaid (diagramme de séquence) |
| Déploiement Docker | [`deploiement.md`](deploiement.md) | Mermaid |

Images SVG pour le rapport, générées depuis les fichiers Mermaid : dossier [`svg/`](svg/).

Régénérer les SVG et vérifier les schémas (Node 20) :

```
npx -y @mermaid-js/mermaid-cli@11.17.1 -i docs/schemas/architecture.md -o docs/schemas/svg/architecture.md
npx -y @mermaid-js/mermaid-cli@11.17.1 -i docs/schemas/workflow.md -o docs/schemas/svg/workflow.md
npx -y @mermaid-js/mermaid-cli@11.17.1 -i docs/schemas/deploiement.md -o docs/schemas/svg/deploiement.md
node scripts/check-bpmn.mjs docs/schemas/workflow-prise-en-charge.bpmn
```
