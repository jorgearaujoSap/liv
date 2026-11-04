#!/bin/sh
# Pruebas del comparador de contratos (compuerta Q08, doc. 04 §8). Cada carpeta es un escenario: compara
# muestra.yaml (la versión de main) con head.yaml (la del PR) y comprueba que el comparador pase o falle.
# El archivo caso de cada escenario dice:
#   esperado=pasa|falla
#   aprobado=si|no        si el PR tiene la etiqueta contrato-incompatible
#   base=muestra|ninguna  ninguna: el contrato es nuevo
#   head=archivo|muestra|ninguno  archivo: head.yaml; muestra: sin cambios; ninguno: el contrato se quitó
#   motivo=CODIGO         si falla, el código que debe dar el comparador, como [INCOMPATIBLE]; así un fallo por
#                         otra causa (un error del script, por ejemplo) no cuenta como el fallo esperado
# Uso, desde backend/ y en Windows desde Git Bash:  sh contracts/scripts/test/run.sh
# Sale con 0 si todos los escenarios dan lo esperado y con 1 si alguno no.
set -u
cd "$(dirname "$0")/../../.."

pruebas=contracts/scripts/test
trabajo=target/contracts-test
fallas=0

valor() { sed -n "s/^$1=//p" "$2" | tr -d '\r'; }

for caso in "$pruebas"/*/caso; do
  escenario=$(dirname "$caso")
  nombre=$(basename "$escenario")
  rm -rf "$trabajo/$nombre"
  mkdir -p "$trabajo/$nombre/base" "$trabajo/$nombre/head"

  [ "$(valor base "$caso")" = muestra ] && cp "$pruebas/muestra.yaml" "$trabajo/$nombre/base/muestra.yaml"
  case "$(valor head "$caso")" in
    archivo) cp "$escenario/head.yaml" "$trabajo/$nombre/head/muestra.yaml" ;;
    muestra) cp "$pruebas/muestra.yaml" "$trabajo/$nombre/head/muestra.yaml" ;;
  esac

  aprobado=false
  [ "$(valor aprobado "$caso")" = si ] && aprobado=true

  if LIV_CONTRACTS_BREAKING_APPROVED=$aprobado sh contracts/scripts/compare.sh --dirs \
      "$trabajo/$nombre/base" "$trabajo/$nombre/head" > "$trabajo/$nombre/salida.txt" 2>&1; then
    resultado=pasa
  else
    resultado=falla
  fi

  esperado=$(valor esperado "$caso")
  motivo=$(valor motivo "$caso")
  if [ "$resultado" != "$esperado" ]; then
    error="se esperaba que $esperado y $resultado"
  elif [ -n "$motivo" ] && ! grep -qF "[$motivo]" "$trabajo/$nombre/salida.txt"; then
    error="falla, pero no con el motivo [$motivo]"
  else
    error=
  fi

  if [ -z "$error" ]; then
    printf '  ok         %s (%s%s)\n' "$nombre" "$resultado" "${motivo:+, $motivo}"
  else
    printf '  FALLA      %s: %s. Salida:\n' "$nombre" "$error"
    sed 's/^/             /' "$trabajo/$nombre/salida.txt"
    fallas=$((fallas + 1))
  fi
done

if [ "$fallas" -gt 0 ]; then
  echo "$fallas escenarios no dieron lo esperado."
  exit 1
fi
echo "Todos los escenarios dieron lo esperado."
