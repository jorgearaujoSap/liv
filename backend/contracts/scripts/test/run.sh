#!/bin/sh
# Pruebas del comparador (compare.sh) y de la publicación (publish.sh) de los contratos (compuerta Q08, doc. 04 §8;
# ADR 0027). Tres grupos:
#   comparar  cada carpeta de comparar/ es un escenario: muestra.yaml (la versión de main) contra head.yaml (la del
#             PR), con un archivo caso;
#   git       compare.sh con una referencia de Git, sobre repositorios temporales (lo que corre en CI);
#   publicar  publish.sh con un gh falso (fake-gh.sh), sin tocar GitHub.
# El archivo caso de cada escenario de comparar/ dice:
#   esperado=pasa|falla
#   aprobado=si|no                si el PR tiene la etiqueta contrato-incompatible
#   base=muestra|archivo|ninguna  archivo: base.yaml; ninguna: el contrato es nuevo
#   head=archivo|muestra|ninguno  archivo: head.yaml; muestra: sin cambios; ninguno: el contrato se quitó
#   otro=si                       agrega otro.yaml, igual en la base y en el PR
#   motivo=CODIGO                 si falla, el código que debe dar, como [INCOMPATIBLE]
#   salida=TEXTO                  si pasa, la línea que debe dar, como «contrato nuevo (1.0.0)»
#   nota=TEXTO                    por qué existe el escenario; no se comprueba
# El motivo y la salida evitan que un fallo o un éxito por otra causa (un error del script, por ejemplo) cuente
# como el esperado.
# Uso, desde backend/ y en Windows desde Git Bash:  sh contracts/scripts/test/run.sh
# Sale con 0 si todo da lo esperado y con 1 si algo no.
set -u
cd "$(dirname "$0")/../../.."

backend=$(pwd)
pruebas="$backend/contracts/scripts/test"
trabajo="$backend/target/contracts-test"
rm -rf "$trabajo"
mkdir -p "$trabajo"
fallas=0

valor() { sed -n "s/^$1=//p" "$2" | tr -d '\r'; }

# Corre una orden, guarda su salida en el archivo y escribe pasa o falla.
correr() { # correr ARCHIVO_DE_SALIDA ORDEN...
  archivo=$1
  shift
  if "$@" > "$archivo" 2>&1; then echo pasa; else echo falla; fi
}

verificar() { # verificar NOMBRE RESULTADO ESPERADO MOTIVO SALIDA ARCHIVO_DE_SALIDA
  if [ "$2" != "$3" ]; then
    error="se esperaba que $3 y $2"
  elif [ -n "$4" ] && ! grep -qF "[$4]" "$6"; then
    error="falla, pero no con el motivo [$4]"
  elif [ -n "$5" ] && ! grep -qF "$5" "$6"; then
    error="pasa, pero sin «$5»"
  else
    error=
  fi

  if [ -z "$error" ]; then
    printf '  ok         %s (%s%s)\n' "$1" "$2" "${4:+, $4}"
  else
    printf '  FALLA      %s: %s. Salida:\n' "$1" "$error"
    sed 's/^/             /' "$6"
    fallas=$((fallas + 1))
  fi
}

echo "Comparar carpetas:"
for caso in "$pruebas"/comparar/*/caso; do
  escenario=$(dirname "$caso")
  nombre=$(basename "$escenario")
  dir="$trabajo/comparar/$nombre"
  mkdir -p "$dir/base" "$dir/head"

  case "$(valor base "$caso")" in
    muestra) cp "$pruebas/muestra.yaml" "$dir/base/muestra.yaml" ;;
    archivo) cp "$escenario/base.yaml" "$dir/base/muestra.yaml" ;;
  esac
  case "$(valor head "$caso")" in
    archivo) cp "$escenario/head.yaml" "$dir/head/muestra.yaml" ;;
    muestra) cp "$pruebas/muestra.yaml" "$dir/head/muestra.yaml" ;;
  esac
  if [ "$(valor otro "$caso")" = si ]; then
    cp "$pruebas/muestra.yaml" "$dir/base/otro.yaml"
    cp "$pruebas/muestra.yaml" "$dir/head/otro.yaml"
  fi
  aprobado=false
  [ "$(valor aprobado "$caso")" = si ] && aprobado=true

  resultado=$(correr "$dir/salida.txt" env LIV_CONTRACTS_BREAKING_APPROVED=$aprobado LIV_CONTRACTS_WORK="$dir/trabajo" \
    sh "$backend/contracts/scripts/compare.sh" --dirs "$dir/base" "$dir/head")
  verificar "$nombre" "$resultado" "$(valor esperado "$caso")" "$(valor motivo "$caso")" "$(valor salida "$caso")" \
    "$dir/salida.txt"
done

echo "Comparar con una referencia de Git:"
# Repositorio temporal con muestra.yaml en contracts/openapi/ y un primer commit; autocrlf apagado para que el
# commit guarde LF y la prueba de CRLF sea real.
repo_nuevo() { # repo_nuevo NOMBRE [sin-contratos]
  repo="$trabajo/git/$1"
  mkdir -p "$repo/contracts/openapi"
  git -C "$repo" init -q
  git -C "$repo" config core.autocrlf false
  git -C "$repo" config user.name prueba
  git -C "$repo" config user.email prueba@example.invalid
  if [ "${2:-}" = sin-contratos ]; then
    echo prueba > "$repo/README.md"
  else
    tr -d '\r' < "$pruebas/muestra.yaml" > "$repo/contracts/openapi/muestra.yaml"
  fi
  git -C "$repo" add -A
  git -C "$repo" commit -q -m inicial
}
caso_git() { # caso_git NOMBRE ESPERADO MOTIVO SALIDA REFERENCIA
  resultado=$(correr "$repo.txt" env LIV_CONTRACTS_ROOT="$repo" LIV_CONTRACTS_WORK="$repo.trabajo" \
    LIV_CONTRACTS_BREAKING_APPROVED=false sh "$backend/contracts/scripts/compare.sh" "$5")
  verificar "git/$1" "$resultado" "$2" "$3" "$4" "$repo.txt"
}
cabeza_quitada="$pruebas/comparar/campo-requerido-quitado/head.yaml"
cabeza_compatible="$pruebas/comparar/campo-opcional-agregado/head.yaml"

repo_nuevo crlf-sin-cambios
sed 's/$/\r/' "$repo/contracts/openapi/muestra.yaml" > "$repo/crlf" && mv "$repo/crlf" "$repo/contracts/openapi/muestra.yaml"
caso_git crlf-sin-cambios pasa '' 'sin cambios (1.0.0)' HEAD

repo_nuevo incompatible
cp "$cabeza_quitada" "$repo/contracts/openapi/muestra.yaml"
caso_git incompatible falla INCOMPATIBLE '' HEAD

repo_nuevo referencia-inexistente
caso_git referencia-inexistente falla GIT_ERROR '' no-existe

repo_nuevo nombre-invalido
cp "$pruebas/muestra.yaml" "$repo/contracts/openapi/Muestra-2.yaml"
caso_git nombre-invalido falla INVALID_NAME '' HEAD

repo_nuevo extension-yml
cp "$pruebas/muestra.yaml" "$repo/contracts/openapi/otro.yml"
caso_git extension-yml falla UNEXPECTED_FILE '' HEAD

repo_nuevo subcarpeta
mkdir -p "$repo/contracts/openapi/comun"
cp "$pruebas/muestra.yaml" "$repo/contracts/openapi/comun/otro.yaml"
caso_git subcarpeta falla UNEXPECTED_FILE '' HEAD

repo_nuevo comparador-y-contrato
mkdir -p "$repo/contracts/scripts"
echo 'regla=1' > "$repo/contracts/scripts/regla.sh"
git -C "$repo" add -A
git -C "$repo" commit -q -m comparador
echo 'regla=2' > "$repo/contracts/scripts/regla.sh"
cp "$cabeza_compatible" "$repo/contracts/openapi/muestra.yaml"
caso_git comparador-y-contrato falla GATE_AND_CONTRACTS '' HEAD

repo_nuevo sin-contratos sin-contratos
caso_git sin-contratos falla NO_CONTRACTS '' HEAD

echo "Publicar:"
# Cada caso tiene una raíz con contracts/openapi/muestra.yaml y un almacén vacío del gh falso.
etiqueta=contract-muestra-v1.0.0
preparar_publicacion() { # preparar_publicacion NOMBRE [sin-contratos]
  dir="$trabajo/publicar/$1"
  almacen="$dir/almacen"
  mkdir -p "$dir/raiz/contracts/openapi" "$almacen"
  : > "$almacen/llamadas"
  [ "${2:-}" = sin-contratos ] || tr -d '\r' < "$pruebas/muestra.yaml" > "$dir/raiz/contracts/openapi/muestra.yaml"
  publicado="$dir/raiz/contracts/openapi/muestra.yaml"
}
sembrar_release() { # sembrar_release ESTADO YAML_O_VACIO SHA_O_VACIO
  mkdir -p "$almacen/releases/$etiqueta/assets"
  echo "$1" > "$almacen/releases/$etiqueta/estado"
  [ -n "$2" ] && tr -d '\r' < "$2" > "$almacen/releases/$etiqueta/assets/muestra.yaml"
  [ -n "$3" ] && printf '%s  muestra.yaml\n' "$3" > "$almacen/releases/$etiqueta/assets/muestra.yaml.sha256"
  if [ "$1" = published ]; then mkdir -p "$almacen/tags" && touch "$almacen/tags/$etiqueta"; fi
}
caso_publicar() { # caso_publicar NOMBRE ESPERADO MOTIVO SALIDA
  resultado=$(correr "$dir/salida.txt" env LIV_CONTRACTS_ROOT="$dir/raiz" LIV_CONTRACTS_WORK="$dir/trabajo" \
    LIV_GH="sh $pruebas/fake-gh.sh" FAKE_GH_STORE="$almacen" GH_REPO=liv/prueba GITHUB_SHA=0123abc \
    sh "$backend/contracts/scripts/publish.sh")
  verificar "publicar/$1" "$resultado" "$2" "$3" "$4" "$dir/salida.txt"
}
# Comprueba el estado del almacén después de publicar.
caso_almacen() { # caso_almacen NOMBRE CONDICION...
  nombre=$1
  shift
  if "$@"; then resultado=pasa; else resultado=falla; fi
  printf 'Llamadas al gh falso:\n' > "$dir/almacen.txt"
  cat "$almacen/llamadas" >> "$dir/almacen.txt"
  verificar "publicar/$nombre" "$resultado" pasa '' '' "$dir/almacen.txt"
}
publicada_completa() {
  [ "$(cat "$almacen/releases/$etiqueta/estado")" = published ] &&
    cmp -s "$almacen/releases/$etiqueta/assets/muestra.yaml" "$publicado" &&
    [ "$(cut -d ' ' -f 1 "$almacen/releases/$etiqueta/assets/muestra.yaml.sha256")" = "$(sha256sum "$publicado" | cut -d ' ' -f 1)" ]
}
sin_crear() { ! grep -q '^release create' "$almacen/llamadas"; }

preparar_publicacion nueva
caso_publicar nueva pasa '' "publicada $etiqueta"
caso_almacen nueva-queda-completa publicada_completa

preparar_publicacion ya-publicada
sembrar_release published "$publicado" "$(sha256sum "$publicado" | cut -d ' ' -f 1)"
caso_publicar ya-publicada pasa '' "$etiqueta ya está publicada"
caso_almacen ya-publicada-sin-crear sin_crear

preparar_publicacion otro-contenido
sembrar_release published "$cabeza_compatible" "$(tr -d '\r' < "$cabeza_compatible" | sha256sum | cut -d ' ' -f 1)"
caso_publicar otro-contenido falla PUBLISHED_CHANGED ''

preparar_publicacion yaml-reemplazado
sembrar_release published "$cabeza_compatible" "$(sha256sum "$publicado" | cut -d ' ' -f 1)"
caso_publicar yaml-reemplazado falla PUBLISHED_CHANGED ''

preparar_publicacion borrador-a-medias
sembrar_release draft '' "$(sha256sum "$publicado" | cut -d ' ' -f 1)"
caso_publicar borrador-a-medias pasa '' "publicada $etiqueta"
caso_almacen borrador-a-medias-queda-completa publicada_completa

preparar_publicacion etiqueta-sin-release
mkdir -p "$almacen/tags" && touch "$almacen/tags/$etiqueta"
caso_publicar etiqueta-sin-release falla PUBLISH_ERROR ''
caso_almacen etiqueta-sin-release-sin-crear sin_crear

preparar_publicacion error-de-red
touch "$almacen/view-falla"
caso_publicar error-de-red falla PUBLISH_ERROR ''
caso_almacen error-de-red-sin-crear sin_crear

preparar_publicacion sin-contratos sin-contratos
caso_publicar sin-contratos falla NO_CONTRACTS ''

if [ "$fallas" -gt 0 ]; then
  echo "$fallas pruebas no dieron lo esperado."
  exit 1
fi
echo "Todas las pruebas dieron lo esperado."
