"""Génère le code Python gRPC depuis contracts/grpc/telemetrie.proto dans telemetrie/gen/ (non versionné).

Usage : python generer.py
Variable PROTO_DIR : dossier du .proto (défaut : ../../contracts/grpc ; dans Docker, le dossier copié).
"""
import os
import sys
from pathlib import Path

import grpc_tools
from grpc_tools import protoc

ici = Path(__file__).resolve().parent
proto_dir = Path(os.environ.get("PROTO_DIR", ici.parent.parent / "contracts" / "grpc")).resolve()
sortie = ici / "telemetrie" / "gen"
sortie.mkdir(parents=True, exist_ok=True)
(sortie / "__init__.py").touch()

code = protoc.main([
    "protoc",
    f"-I{proto_dir}",
    f"-I{Path(grpc_tools.__file__).parent / '_proto'}",
    f"--python_out={sortie}",
    f"--grpc_python_out={sortie}",
    str(proto_dir / "telemetrie.proto"),
])
if code != 0:
    sys.exit(f"protoc a échoué ({code})")

# Le code généré importe « telemetrie_pb2 » en absolu : on le rend relatif au paquet gen/.
grpc_py = sortie / "telemetrie_pb2_grpc.py"
grpc_py.write_text(grpc_py.read_text(encoding="utf-8").replace(
    "import telemetrie_pb2 as telemetrie__pb2", "from . import telemetrie_pb2 as telemetrie__pb2"), encoding="utf-8")
print(f"Code généré depuis {proto_dir / 'telemetrie.proto'} dans {sortie}")
