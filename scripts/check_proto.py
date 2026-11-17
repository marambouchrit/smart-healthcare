# Compile contracts/grpc/telemetrie.proto (Python) dans un dossier temporaire et vérifie le contrat :
# modes de streaming des RPC, énumération Niveau, sérialisation d'une Mesure.
# Prérequis (outil de vérification) : pip install grpcio-tools==1.85.1
# Usage : python scripts/check_proto.py
import sys, tempfile, importlib
from pathlib import Path
from grpc_tools import protoc
import grpc_tools

racine = Path(__file__).resolve().parent.parent
proto_dir = racine / "contracts" / "grpc"
include_std = Path(grpc_tools.__file__).parent / "_proto"

with tempfile.TemporaryDirectory() as out:
    code = protoc.main(["protoc", f"-I{proto_dir}", f"-I{include_std}",
                        f"--python_out={out}", f"--grpc_python_out={out}",
                        str(proto_dir / "telemetrie.proto")])
    if code != 0:
        print(f"ÉCHEC : protoc a renvoyé {code}")
        sys.exit(1)
    print("protoc OK :", ", ".join(sorted(p.name for p in Path(out).iterdir() if p.suffix == ".py")))
    sys.path.insert(0, out)
    pb = importlib.import_module("telemetrie_pb2")
    g = importlib.import_module("telemetrie_pb2_grpc")
    from google.protobuf.timestamp_pb2 import Timestamp

    svc = pb.DESCRIPTOR.services_by_name["Telemetrie"]
    for m in svc.methods:
        print(f"  {m.name:15s} client_streaming={m.client_streaming} server_streaming={m.server_streaming} "
              f"{m.input_type.name} -> {m.output_type.name}")
    em, sa = svc.methods_by_name["EnvoyerMesures"], svc.methods_by_name["SuivreAlertes"]
    assert em.client_streaming and not em.server_streaming, "EnvoyerMesures doit être client streaming"
    assert sa.server_streaming and not sa.client_streaming, "SuivreAlertes doit être server streaming"
    assert list(pb.Niveau.keys()) == ["NIVEAU_NON_SPECIFIE", "INFO", "URGENT", "CRITIQUE"]
    assert hasattr(g, "TelemetrieStub") and hasattr(g, "TelemetrieServicer")

    ts = Timestamp(); ts.FromJsonString("2026-11-17T10:30:00Z")
    m = pb.Mesure(identifiant_patient="TN-0012345", identifiant_capteur="capteur-TN-0012345-cardio",
                  type=pb.FREQUENCE_CARDIAQUE, valeur=165, horodatage=ts)
    assert pb.Mesure.FromString(m.SerializeToString()) == m
    print("OK : streaming conforme, Niveau = INFO/URGENT/CRITIQUE, Mesure sérialisée et relue, stubs générés")
