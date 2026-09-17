#!/usr/bin/env bash
#
# RA4/RA5: demuestra que el dominio y la aplicación no necesitan infraestructura.
# Copia el repo a un directorio temporal, BORRA por completo
# src/main/java/.../infraestructura y src/test/java/.../infraestructura (el
# CSV, el adaptador JPA, la entidad, Flyway-facing config, todo) y corre SOLO
# las pruebas de dominio y aplicación sobre esa copia mutilada.
#
# No hace falta excluir nada más: web/, arquitectura/ y
# FitodiagnosticoApplicationTests siguen compilando sin infraestructura (nada
# en ellos importa una clase de ese paquete en tiempo de compilación), y el
# filtro -Dtest de abajo nunca los ejecuta. Si en el futuro alguno de esos
# paquetes empieza a fallar la compilación por depender de infraestructura,
# la respuesta correcta es borrar también esa copia de src/test/.../web,
# src/test/.../arquitectura y FitodiagnosticoApplicationTests aquí abajo (NO
# relajar el filtro ni la regla): lo que este script demuestra es que
# dominio y aplicación no necesitan infraestructura, no que todo el árbol
# de pruebas la ignore.
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
MAIN_INFRA="$TMP_DIR/repo/src/main/java/$PKG_BASE/infraestructura"
TEST_INFRA="$TMP_DIR/repo/src/test/java/$PKG_BASE/infraestructura"

echo "== borrando infraestructura de la copia =="
echo "  - ${MAIN_INFRA#"$TMP_DIR/repo/"}"
echo "  - ${TEST_INFRA#"$TMP_DIR/repo/"}"
rm -rf "$MAIN_INFRA" "$TEST_INFRA"

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
    echo "pruebas sin que exista un solo archivo de infraestructura."
else
    echo "RESULTADO: FALLA — revisa el log de Maven arriba (relanza sin -q"
    echo "si hace falta más detalle: quita el flag '-q' de este script)."
fi
echo "=========================================================="

exit "$RESULTADO"
