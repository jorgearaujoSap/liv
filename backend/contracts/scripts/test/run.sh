#!/bin/sh
# Pruebas del comparador (compare.sh) y de la publicación (publish.sh) de los contratos (compuerta Q08, doc. 04 §8;
# ADR 0027). Tres grupos:
#   comparar  cada carpeta de comparar/ es un escenario: muestra.yaml (la versión de main) contra head.yaml (la del
#             PR), con un archivo caso;
#   git       compare.sh con una referencia de Git, sobre repositorios temporales con el proyecto en una
#             subcarpeta (como corre en CI);
#   publicar  publish.sh con un gh falso (fake-gh.sh) sobre repositorios temporales, sin tocar GitHub: juzga
#             cada versión contra la release publicada más alta de su contrato.
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


# Una carpeta de trabajo dentro del proyecto, fuera de target/, tampoco se borra.
repo_nuevo trabajo-dentro-del-proyecto
caso_git trabajo-dentro-del-proyecto falla UNSAFE_WORKDIR '' HEAD "$proyecto/contracts/openapi"
resultado=falla
[ -f "$proyecto/contracts/openapi/muestra.yaml" ] && resultado=pasa
echo "El proyecto sigue con sus contratos: $resultado" > "$repo.queda.txt"
verificar git/trabajo-dentro-del-proyecto-no-borra "$resultado" pasa '' '' "$repo.queda.txt"

echo "Publicar:"
# Cada caso tiene un repositorio con un commit (GITHUB_SHA) y un almacén vacío del gh falso. publish.sh juzga
# cada versión sin release contra la release publicada más alta de su contrato y solo entonces la publica.
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
commit_vacio() {
  echo prueba > "$repo/README.md"
  git -C "$repo" add -A
  git -C "$repo" commit -q -m vacio
}
sha_de() { tr -d '\r' < "$1" | sha256sum | cut -d ' ' -f 1; }
sembrar_release() { # sembrar_release ETIQUETA ESTADO YAML_O_VACIO SHA_O_VACIO
  nombre_release=${1#contract-}
  nombre_release=${nombre_release%-v*}
  mkdir -p "$almacen/releases/$1/assets"
  echo "$2" > "$almacen/releases/$1/estado"
  [ -n "$3" ] && tr -d '\r' < "$3" > "$almacen/releases/$1/assets/$nombre_release.yaml"
  [ -n "$4" ] && printf '%s  %s.yaml\n' "$4" "$nombre_release" > "$almacen/releases/$1/assets/$nombre_release.yaml.sha256"
  if [ "$2" = published ]; then mkdir -p "$almacen/tags" && touch "$almacen/tags/$1"; fi
}
sembrar_publicada() { sembrar_release "$1" published "$2" "$(sha_de "$2")"; } # sembrar_publicada ETIQUETA YAML
caso_publicar() { # caso_publicar NOMBRE ESPERADO MOTIVO SALIDA [APROBADO]
  resultado=$(correr "$dir/salida.txt" env LIV_CONTRACTS_ROOT="$repo" LIV_CONTRACTS_WORK="$dir/trabajo" \
    LIV_CONTRACTS_BREAKING_APPROVED="${5:-false}" LIV_GH="sh $pruebas/fake-gh.sh" FAKE_GH_STORE="$almacen" \
    GH_REPO=liv/prueba GITHUB_SHA="$(git -C "$repo" rev-parse HEAD)" sh "$backend/contracts/scripts/publish.sh")
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
publicada_completa() { # publicada_completa ETIQUETA
  contenido=$(cd "$repo" && MSYS_NO_PATHCONV=1 git show HEAD:contracts/openapi/muestra.yaml | sha256sum | cut -d ' ' -f 1)
  [ "$(cat "$almacen/releases/$1/estado")" = published ] &&
    [ "$(sha256sum < "$almacen/releases/$1/assets/muestra.yaml" | cut -d ' ' -f 1)" = "$contenido" ] &&
    [ "$(cut -d ' ' -f 1 "$almacen/releases/$1/assets/muestra.yaml.sha256")" = "$contenido" ]
}
sin_crear() { ! grep -q '^release create' "$almacen/llamadas"; }
sin_publicar() { ! grep -q -- '--draft=false' "$almacen/llamadas"; }

v1=contract-muestra-v1.0.0
v1_1=contract-muestra-v1.1.0
v2=contract-muestra-v2.0.0
cabeza_mayor="$pruebas/comparar/campo-requerido-quitado-aprobado/head.yaml"
cabeza_invalida="$pruebas/comparar/contrato-invalido/head.yaml"

repo_publicacion nueva
commit_contrato "$pruebas/muestra.yaml"
caso_publicar nueva pasa '' "publicada $v1"
caso_almacen nueva-queda-completa publicada_completa "$v1"

repo_publicacion arbol-con-cambios
commit_contrato "$pruebas/muestra.yaml"
tr -d '\r' < "$cabeza_compatible" > "$publicado"
caso_publicar arbol-con-cambios pasa '' "publicada $v1"
caso_almacen arbol-con-cambios-publica-el-commit publicada_completa "$v1"

repo_publicacion ya-publicada
commit_contrato "$pruebas/muestra.yaml"
sembrar_publicada "$v1" "$publicado"
caso_publicar ya-publicada pasa '' "$v1 ya está publicada"
caso_almacen ya-publicada-sin-crear sin_crear

repo_publicacion otro-contenido
commit_contrato "$pruebas/muestra.yaml"
sembrar_publicada "$v1" "$cabeza_compatible"
caso_publicar otro-contenido falla PUBLISHED_CHANGED ''

repo_publicacion yaml-reemplazado
commit_contrato "$pruebas/muestra.yaml"
sembrar_release "$v1" published "$cabeza_compatible" "$(sha_de "$publicado")"
caso_publicar yaml-reemplazado falla PUBLISHED_CHANGED ''

# Contra la release más alta: un cambio compatible con la versión subida se publica.
repo_publicacion compatible-sobre-release
commit_contrato "$cabeza_compatible"
sembrar_publicada "$v1" "$pruebas/muestra.yaml"
caso_publicar compatible-sobre-release pasa '' "publicada $v1_1"
caso_almacen compatible-sobre-release-completa publicada_completa "$v1_1"

# Un incompatible sin la etiqueta no se publica, aunque el commit anterior lo tuviera: se juzga contra lo publicado.
repo_publicacion incompatible-sin-etiqueta
commit_contrato "$cabeza_quitada"
sembrar_publicada "$v1" "$pruebas/muestra.yaml"
caso_publicar incompatible-sin-etiqueta falla INCOMPATIBLE ''
caso_almacen incompatible-sin-etiqueta-sin-crear sin_crear

repo_publicacion incompatible-aprobado
commit_contrato "$cabeza_mayor"
sembrar_publicada "$v1" "$pruebas/muestra.yaml"
caso_publicar incompatible-aprobado pasa '' "publicada $v2" true

# Un contrato vuelto a agregar con una versión menor que la publicada no pasa.
repo_publicacion version-menor-que-la-publicada
commit_contrato "$cabeza_compatible"
sembrar_publicada "$v2" "$cabeza_mayor"
caso_publicar version-menor-que-la-publicada falla VERSION_NOT_BUMPED ''

# contract-muestra-v2-v9.0.0 es de otro contrato (muestra-v2): muestra sigue sin releases.
repo_publicacion otro-contrato-con-prefijo
commit_contrato "$pruebas/muestra.yaml"
sembrar_publicada contract-muestra-v2-v9.0.0 "$cabeza_mayor"
caso_publicar otro-contrato-con-prefijo pasa '' "publicada $v1"

repo_publicacion contrato-nuevo-invalido
commit_contrato "$cabeza_invalida"
caso_publicar contrato-nuevo-invalido falla COMPARE_ERROR ''
caso_almacen contrato-nuevo-invalido-sin-crear sin_crear

repo_publicacion borrador-a-medias
commit_contrato "$pruebas/muestra.yaml"
sembrar_release "$v1" draft '' "$(sha_de "$publicado")"
caso_publicar borrador-a-medias pasa '' "publicada $v1"
caso_almacen borrador-a-medias-queda-completa publicada_completa "$v1"

# Un borrador también se juzga antes de completarse.
repo_publicacion borrador-incompatible
commit_contrato "$cabeza_quitada"
sembrar_publicada "$v1" "$pruebas/muestra.yaml"
sembrar_release "$v1_1" draft "$cabeza_quitada" "$(sha_de "$cabeza_quitada")"
caso_publicar borrador-incompatible falla INCOMPATIBLE ''
caso_almacen borrador-incompatible-sin-publicar sin_publicar

repo_publicacion borrador-no-verifica
commit_contrato "$pruebas/muestra.yaml"
sembrar_release "$v1" draft '' "$(sha_de "$publicado")"
touch "$almacen/download-altera"
caso_publicar borrador-no-verifica falla PUBLISH_ERROR ''
caso_almacen borrador-no-verifica-sin-publicar sin_publicar

repo_publicacion subida-falla
commit_contrato "$pruebas/muestra.yaml"
sembrar_release "$v1" draft '' "$(sha_de "$publicado")"
touch "$almacen/upload-falla"
caso_publicar subida-falla falla PUBLISH_ERROR ''
caso_almacen subida-falla-sin-publicar sin_publicar

repo_publicacion etiqueta-sin-release
commit_contrato "$pruebas/muestra.yaml"
mkdir -p "$almacen/tags" && touch "$almacen/tags/$v1"
caso_publicar etiqueta-sin-release falla PUBLISH_ERROR ''
caso_almacen etiqueta-sin-release-sin-crear sin_crear

repo_publicacion error-de-red
commit_contrato "$pruebas/muestra.yaml"
touch "$almacen/view-falla"
caso_publicar error-de-red falla PUBLISH_ERROR ''
caso_almacen error-de-red-sin-crear sin_crear

# Si no se pueden listar las releases publicadas, no se toma como «nunca publicado».
repo_publicacion error-api
commit_contrato "$pruebas/muestra.yaml"
touch "$almacen/api-falla"
caso_publicar error-api falla PUBLISH_ERROR ''
caso_almacen error-api-sin-crear sin_crear

repo_publicacion sin-contratos
commit_vacio
caso_publicar sin-contratos falla NO_CONTRACTS ''

if [ "$fallas" -gt 0 ]; then
  echo "$fallas pruebas no dieron lo esperado."
  exit 1
fi
echo "Todas las pruebas dieron lo esperado."
