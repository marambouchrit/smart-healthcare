# Vérifie le WSDL SOAP comme le ferait un client : chargement, opérations, enveloppes conformes au XSD.
# Prérequis (outil de vérification, pas une dépendance du projet) : pip install zeep==4.3.3
# Usage : python scripts/check_wsdl.py contracts/soap/dossier.wsdl contracts/soap/dossier.xsd
import sys, datetime
from lxml import etree
from zeep import Client
wsdl, xsd = sys.argv[1], sys.argv[2]
client = Client(wsdl)
schema = etree.XMLSchema(etree.parse(xsd))
for svc in client.wsdl.services.values():
    for port in svc.ports.values():
        print(f"Service {svc.name} / port {port.name} -> {port.binding_options['address']}")
        ops = port.binding._operations
        print("Opérations :", ", ".join(sorted(ops)))
d = datetime.date(2026, 11, 17)
pec = lambda pid: {"identifiantPatient": pid, "lettreCle": "CS", "coefficient": 1, "datePrestation": d}
acte = {"lettreCle": "K", "coefficient": 10, "libelle": "ECG", "dateActe": d, "identifiantMedecin": "MED-001"}
calls = {
  "verifierDroitsCNAM": dict(identifiantPatient="TN-0012345", lettreCle="CS", datePrestation=d),
  "emettreFeuilleDeSoins": dict(identifiantPatient="TN-0012345", codeVignette="VIG-2026-100001", acte=[acte]),
  "consulterHistorique": dict(identifiantPatient="TN-0012345"),
  "comparerGaranties": dict(priseEnChargeA=pec("TN-0012345"), priseEnChargeB=pec("TN-0023456")),
}
ok = True
for op, args in calls.items():
    env = client.create_message(client.service, op, **args)
    body = env.find("{http://schemas.xmlsoap.org/soap/envelope/}Body")[0]
    action = client.service._binding._operations[op].soapaction
    valid = schema.validate(body)
    ok &= valid
    print(f"{op:24s} enveloppe construite, soapAction={action}, corps conforme au XSD: {valid}")
    if not valid: print("   ", schema.error_log.last_error)
sys.exit(0 if ok else 1)
