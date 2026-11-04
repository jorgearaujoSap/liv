#!/bin/sh
# Pruebas del comparador (compare.sh) y de la publicación (publish.sh) de los contratos (compuerta Q08, doc. 04 §8;
# ADR 0027). Tres grupos:
#   comparar  cada carpeta de comparar/ es un escenario: muestra.yaml (la versión de main) contra head.yaml (la del
#             PR), con un archivo caso;
#   git       compare.sh con una referencia de Git, sobre repositorios temporales con el proyecto en una
#             subcarpeta (como corre en CI);
#   publicar  publish.sh con un gh falso (fake-gh.sh) sobre repositorios temporales, sin tocar GitHub.
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
# Repositorio temporal con el proyecto en una subcarpeta, como backend/ en el repositorio real, y un primer commit
# con muestra.yaml. autocrlf apagado para que el commit guarde LF y la prueba de CRLF sea real.
repo_nuevo() { # repo_nuevo NOMBRE [sin-contratos]
  repo="$trabajo/git/$1"
  proyecto="$repo/proyecto"
  mkdir -p "$proyecto/contracts/openapi"
  git -C "$repo" init -q
  git -C "$repo" config core.autocrlf false
  git -C "$repo" config user.name prueba
  git -C "$repo" config user.email prueba@example.invalid
  if [ "${2:-}" = sin-contratos ]; then
    echo prueba > "$proyecto/README.md"
  else
    tr -d '\r' < "$pruebas/muestra.yaml" > "$proyecto/contracts/openapi/muestra.yaml"
  fi
  git -C "$repo" add -A
  git -C "$repo" commit -q -m inicial
}
caso_git() { # caso_git NOMBRE ESPERADO MOTIVO SALIDA REFERENCIA [CARPETA_DE_TRABAJO]
  resultado=$(correr "$repo.txt" env LIV_CONTRACTS_ROOT="$proyecto" LIV_CONTRACTS_WORK="${6:-$repo.trabajo}" \
    LIV_CONTRACTS_BREAKING_APPROVED=false sh "$backend/contracts/scripts/compare.sh" "$5")
  verificar "git/$1" "$resultado" "$2" "$3" "$4" "$repo.txt"
}
cabeza_quitada="$pruebas/comparar/campo-requerido-quitado/head.yaml"
cabeza_compatible="$pruebas/comparar/campo-opcional-agregado/head.yaml"

repo_nuevo crlf-sin-cambios
sed 's/$/\r/' "$proyecto/contracts/openapi/muestra.yaml" > "$repo/crlf"
mv "$repo/crlf" "$proyecto/contracts/openapi/muestra.yaml"
caso_git crlf-sin-cambios pasa '' 'sin cambios (1.0.0)' HEAD

repo_nuevo incompatible
cp "$cabeza_quitada" "$proyecto/contracts/openapi/muestra.yaml"
caso_git incompatible falla INCOMPATIBLE '' HEAD

repo_nuevo referencia-inexistente
caso_git referencia-inexistente falla GIT_ERROR '' no-existe

repo_nuevo nombre-invalido
cp "$pruebas/muestra.yaml" "$proyecto/contracts/openapi/Muestra-2.yaml"
caso_git nombre-invalido falla INVALID_NAME '' HEAD

repo_nuevo archivo-oculto
cp "$pruebas/muestra.yaml" "$proyecto/contracts/openapi/.oculto.yaml"
caso_git archivo-oculto falla INVALID_NAME '' HEAD

repo_nuevo extension-yml
cp "$pruebas/muestra.yaml" "$proyecto/contracts/openapi/otro.yml"
caso_git extension-yml falla UNEXPECTED_FILE '' HEAD

repo_nuevo subcarpeta
mkdir -p "$proyecto/contracts/openapi/comun"
cp "$pruebas/muestra.yaml" "$proyecto/contracts/openapi/comun/otro.yaml"
caso_git subcarpeta falla UNEXPECTED_FILE '' HEAD

repo_nuevo sin-contratos sin-contratos
caso_git sin-contratos falla NO_CONTRACTS '' HEAD

# Una carpeta de trabajo que es el propio proyecto no se borra.
repo_nuevo trabajo-inseguro
caso_git trabajo-inseguro falla UNSAFE_WORKDIR '' HEAD "$proyecto"
resultado=falla
[ -f "$proyecto/contracts/openapi/muestra.yaml" ] && resultado=pasa
echo "El proyecto sigue con sus contratos: $resultado" > "$repo.queda.txt"
verificar git/trabajo-inseguro-no-borra "$resultado" pasa '' '' "$repo.queda.txt"

echo "Publicar:"
# Cada caso tiene un repositorio con dos o tres commits y un almacén vacío del gh falso. publish.sh publica el
# último commit (GITHUB_SHA) y lo compara con el anterior (HEAD^1).
etiqueta=contract-muestra-v1.0.0
otros=0
repo_publicacion() { # repo_publicacion NOMBRE
  dir="$trabajo/publicar/$1"
  repo="$dir/repo"
  almacen="$dir/almacen"
  mkdir -p "$repo/contracts/openapi" "$almacen"
  : > "$almacen/llamadas"
  git -C "$repo" init -q
  git -C "$repo" config core.autocrlf false
  git -C "$repo" config user.name prueba
  git -C "$repo" config user.email prueba@example.invalid
  publicado="$repo/contracts/openapi/muestra.yaml"
}
commit_contrato() { # commit_contrato ARCHIVO
  tr -d '\r' < "$1" > "$publicado"
  git -C "$repo" add -A
  git -C "$repo" commit -q -m contrato
}
commit_otro() {
  otros=$((otros + 1))
  echo "$otros" >> "$repo/README.md"
  git -C "$repo" add -A
  git -C "$repo" commit -q -m otro
}
sha_de() { tr -d '\r' < "$1" | sha256sum | cut -d ' ' -f 1; }
sembrar_release() { # sembrar_release ETIQUETA ESTADO YAML_O_VACIO SHA_O_VACIO
  mkdir -p "$almacen/releases/$1/assets"
  echo "$2" > "$almacen/releases/$1/estado"
  [ -n "$3" ] && tr -d '\r' < "$3" > "$almacen/releases/$1/assets/muestra.yaml"
  [ -n "$4" ] && printf '%s  muestra.yaml\n' "$4" > "$almacen/releases/$1/assets/muestra.yaml.sha256"
  if [ "$2" = published ]; then mkdir -p "$almacen/tags" && touch "$almacen/tags/$1"; fi
}
caso_publicar() { # caso_publicar NOMBRE ESPERADO MOTIVO SALIDA
  resultado=$(correr "$dir/salida.txt" env LIV_CONTRACTS_ROOT="$repo" LIV_CONTRACTS_WORK="$dir/trabajo" \
    LIV_GH="sh $pruebas/fake-gh.sh" FAKE_GH_STORE="$almacen" GH_REPO=liv/prueba \
    GITHUB_SHA="$(git -C "$repo" rev-parse HEAD)" sh "$backend/contracts/scripts/publish.sh" HEAD^1)
  verificar "publicar/$1" "$resultado" "$2" "$3" "$4" "$dir/salida.txt"
}
# Comprueba el almacén del gh falso después de publicar.
caso_almacen() { # caso_almacen NOMBRE CONDICION...
  nombre=$1
  shift
  if "$@"; then resultado=pasa; else resultado=falla; fi
  printf 'Llamadas al gh falso:\n' > "$dir/almacen.txt"
  cat "$almacen/llamadas" >> "$dir/almacen.txt"
  verificar "publicar/$nombre" "$resultado" pasa '' '' "$dir/almacen.txt"
}
# La release está publicada con el contenido del commit, no el del árbol de trabajo.
publicada_completa() {
  contenido=$(cd "$repo" && MSYS_NO_PATHCONV=1 git show HEAD:contracts/openapi/muestra.yaml | sha256sum | cut -d ' ' -f 1)
  [ "$(cat "$almacen/releases/$etiqueta/estado")" = published ] &&
    [ "$(sha256sum < "$almacen/releases/$etiqueta/assets/muestra.yaml" | cut -d ' ' -f 1)" = "$contenido" ] &&
    [ "$(cut -d ' ' -f 1 "$almacen/releases/$etiqueta/assets/muestra.yaml.sha256")" = "$contenido" ]
}
sin_crear() { ! grep -q '^release create' "$almacen/llamadas"; }
sin_publicar() { ! grep -q -- '--draft=false' "$almacen/llamadas"; }

repo_publicacion nueva
commit_otro
commit_contrato "$pruebas/muestra.yaml"
caso_publicar nueva pasa '' "publicada $etiqueta"
caso_almacen nueva-queda-completa publicada_completa

repo_publicacion arbol-con-cambios
commit_otro
commit_contrato "$pruebas/muestra.yaml"
tr -d '\r' < "$cabeza_compatible" > "$publicado"
caso_publicar arbol-con-cambios pasa '' "publicada $etiqueta"
caso_almacen arbol-con-cambios-publica-el-commit publicada_completa

repo_publicacion ya-publicada
commit_otro
commit_contrato "$pruebas/muestra.yaml"
sembrar_release "$etiqueta" published "$publicado" "$(sha_de "$publicado")"
caso_publicar ya-publicada pasa '' "$etiqueta ya está publicada"
caso_almacen ya-publicada-sin-crear sin_crear

repo_publicacion otro-contenido
commit_otro
commit_contrato "$pruebas/muestra.yaml"
sembrar_release "$etiqueta" published "$cabeza_compatible" "$(sha_de "$cabeza_compatible")"
caso_publicar otro-contenido falla PUBLISHED_CHANGED ''

repo_publicacion yaml-reemplazado
commit_otro
commit_contrato "$pruebas/muestra.yaml"
sembrar_release "$etiqueta" published "$cabeza_compatible" "$(sha_de "$publicado")"
caso_publicar yaml-reemplazado falla PUBLISHED_CHANGED ''

repo_publicacion borrador-a-medias
commit_otro
commit_contrato "$pruebas/muestra.yaml"
sembrar_release "$etiqueta" draft '' "$(sha_de "$publicado")"
caso_publicar borrador-a-medias pasa '' "publicada $etiqueta"
caso_almacen borrador-a-medias-queda-completa publicada_completa

repo_publicacion borrador-no-verifica
commit_otro
commit_contrato "$pruebas/muestra.yaml"
sembrar_release "$etiqueta" draft '' "$(sha_de "$publicado")"
touch "$almacen/download-altera"
caso_publicar borrador-no-verifica falla PUBLISH_ERROR ''
caso_almacen borrador-no-verifica-sin-publicar sin_publicar

repo_publicacion subida-falla
commit_otro
commit_contrato "$pruebas/muestra.yaml"
sembrar_release "$etiqueta" draft '' "$(sha_de "$publicado")"
touch "$almacen/upload-falla"
caso_publicar subida-falla falla PUBLISH_ERROR ''
caso_almacen subida-falla-sin-publicar sin_publicar

repo_publicacion etiqueta-sin-release
commit_otro
commit_contrato "$pruebas/muestra.yaml"
mkdir -p "$almacen/tags" && touch "$almacen/tags/$etiqueta"
caso_publicar etiqueta-sin-release falla PUBLISH_ERROR ''
caso_almacen etiqueta-sin-release-sin-crear sin_crear

repo_publicacion error-de-red
commit_otro
commit_contrato "$pruebas/muestra.yaml"
touch "$almacen/view-falla"
caso_publicar error-de-red falla PUBLISH_ERROR ''
caso_almacen error-de-red-sin-crear sin_crear

repo_publicacion error-api-etiqueta
commit_otro
commit_contrato "$pruebas/muestra.yaml"
touch "$almacen/api-falla"
caso_publicar error-api-etiqueta falla PUBLISH_ERROR ''
caso_almacen error-api-etiqueta-sin-crear sin_crear

# El contrato no cambia en este commit, pero nunca tuvo release: es la primera publicación.
repo_publicacion nunca-publicado
commit_contrato "$pruebas/muestra.yaml"
commit_otro
caso_publicar nunca-publicado pasa '' "publicada $etiqueta"

# 1.1.0 llegó en un commit anterior cuya publicación no corrió o falló: no se publica sin haberla juzgado.
repo_publicacion anterior-sin-publicar
commit_contrato "$pruebas/muestra.yaml"
sembrar_release "$etiqueta" published "$publicado" "$(sha_de "$publicado")"
commit_contrato "$cabeza_compatible"
commit_otro
caso_publicar anterior-sin-publicar falla UNPUBLISHED_EARLIER ''
caso_almacen anterior-sin-publicar-sin-crear sin_crear

repo_publicacion sin-contratos
commit_otro
commit_otro
caso_publicar sin-contratos falla NO_CONTRACTS ''

if [ "$fallas" -gt 0 ]; then
  echo "$fallas pruebas no dieron lo esperado."
  exit 1
fi
echo "Todas las pruebas dieron lo esperado."
