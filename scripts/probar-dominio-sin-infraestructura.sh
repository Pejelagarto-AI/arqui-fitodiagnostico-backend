#!/usr/bin/env bash
#
# RA4/RA5: demuestra que el dominio y la aplicación no necesitan infraestructura.
# Copia el repo a un directorio temporal, BORRA por completo
# src/main/java/.../adaptador y .../config (y sus pruebas): los adaptadores de
# entrada REST, dispositivo y WebSocket, los de salida JPA, CSV, JWT y correo, y
# la composición con Spring). Corre SOLO las pruebas de dominio y aplicación
# sobre esa copia mutilada. Es la prueba material de la arquitectura hexagonal:
# el hexágono (dominio + puertos + aplicación) compila sin un solo adaptador.
#
# Las pruebas de integración de la raíz de src/test (que arrancan Spring con
# todos los adaptadores) y arquitectura/ no pueden compilar sin adaptadores, así
# que también se borran de la copia; el filtro -Dtest solo ejecuta dominio y
# aplicación. Lo que este script demuestra es que dominio y aplicación no
# necesitan adaptadores, no que todo el árbol de pruebas los ignore.
#
# Responde en la sustentación a: "si borro infraestructura, ¿compilan/pasan
# tus pruebas de dominio? Ejecútalas."

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TMP_DIR="$(mktemp -d -t fito-sin-infra-XXXXXX)"

limpiar() {
    rm -rf "$TMP_DIR"
}
trap limpiar EXIT

echo "== copiando el repo a $TMP_DIR =="
cp -r "$REPO_ROOT" "$TMP_DIR/repo"
# target/ y .git/ son ruido para esta prueba: target puede traer .class
# compilados de infraestructura que harían pasar la prueba por las razones
# equivocadas (Maven los vería "up to date" y no notaría que el .java ya no
# existe), y .git no aporta nada a la compilación.
rm -rf "$TMP_DIR/repo/target" "$TMP_DIR/repo/.git"

PKG_BASE="com/vivero/fitodiagnostico"
BORRAR=(
    "src/main/java/$PKG_BASE/adaptador"
    "src/main/java/$PKG_BASE/config"
    "src/main/java/$PKG_BASE/FitodiagnosticoApplication.java"
    "src/test/java/$PKG_BASE/adaptador"
    "src/test/java/$PKG_BASE/arquitectura"
)

echo "== borrando adaptadores y composicion de la copia =="
for ruta in "${BORRAR[@]}"; do
    echo "  - $ruta"
    rm -rf "$TMP_DIR/repo/$ruta"
done
# Pruebas de integracion de la raiz: arrancan Spring con todos los adaptadores.
find "$TMP_DIR/repo/src/test/java/$PKG_BASE" -maxdepth 1 -name '*.java' -delete

cd "$TMP_DIR/repo"

echo "== corriendo dominio + aplicacion sin infraestructura =="
set +e
./mvnw -q test \
    -Dtest='com.vivero.fitodiagnostico.dominio.**.*Test,com.vivero.fitodiagnostico.aplicacion.**.*Test' \
    -Dsurefire.failIfNoSpecifiedTests=false
RESULTADO=$?
set -e

echo
echo "=========================================================="
if [ "$RESULTADO" -eq 0 ]; then
    echo "RESULTADO: PASA — dominio y aplicación compilan y pasan sus"
    echo "pruebas sin que exista un solo adaptador ni la composición."
else
    echo "RESULTADO: FALLA — revisa el log de Maven arriba (relanza sin -q"
    echo "si hace falta más detalle: quita el flag '-q' de este script)."
fi
echo "=========================================================="

exit "$RESULTADO"
