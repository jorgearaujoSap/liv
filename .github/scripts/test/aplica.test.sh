#!/usr/bin/env bash
# Pruebas de .github/scripts/aplica.sh (feature 003, plan, paso 1).
#
# aplica.sh decide si la verificación de un proyecto corre en un PR. Cada caso arma un repositorio temporal con un
# commit de merge real (dos padres: la punta de main y la del PR, como el checkout de GitHub Actions) y llama al
# script sobre él, sin red. Los commits se arman con comandos de bajo nivel de Git, sin archivos en disco, para poder
# probar nombres que Windows no deja crear (comillas, saltos de línea).
#
# Uso: bash .github/scripts/test/aplica.test.sh
# Sale con 0 si todo pasa y con 1 si algo falla.
set -u

aqui=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
script="$aqui/../aplica.sh"
if [ ! -f "$script" ]; then
  echo "FALTA $script: no hay nada que probar." >&2
  exit 1
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# Identidad fija y sin firmas: el repositorio de prueba no hereda la configuración de quien lo corre.
export GIT_AUTHOR_NAME=Prueba GIT_AUTHOR_EMAIL=prueba@example.com
export GIT_COMMITTER_NAME=Prueba GIT_COMMITTER_EMAIL=prueba@example.com

repo="$tmp/repo"
git init -q "$repo"
git -C "$repo" config commit.gpgsign false
# En Windows, Git rechaza por defecto nombres con comillas y saltos de línea; en Linux, donde corre el CI, no.
git -C "$repo" config core.protectNTFS false
# Con el valor por defecto, Git escribe entre comillas los nombres con tildes: aplica.sh debe evitarlo por sí mismo.
git -C "$repo" config core.quotePath true
git -C "$repo" symbolic-ref HEAD refs/heads/pr

total=0
fallos=0

ok() { total=$((total + 1)); echo "ok    - $1"; }
falla() { total=$((total + 1)); fallos=$((fallos + 1)); echo "FALLA - $1"; echo "        $2"; }

# ---------------------------------------------------------------------------------------------------------------
# Construcción de commits
# ---------------------------------------------------------------------------------------------------------------
# Cada blob lleva un contenido distinto (el contador vive en un archivo porque $(...) corre en un subproceso).
echo 0 > "$tmp/contador"
blob() {
  local n
  n=$(($(cat "$tmp/contador") + 1))
  echo "$n" > "$tmp/contador"
  printf 'contenido %s\n' "$n" | git -C "$repo" hash-object -w --stdin
}

# Acciones sobre el índice en GIT_INDEX_FILE: +ruta crea o modifica, -ruta borra, ~origen>destino mueve.
aplicar() {
  local a par origen destino sha
  for a in "$@"; do
    case "$a" in
      +*) git -C "$repo" update-index --add --cacheinfo "100644,$(blob),${a#+}" ;;
      -*) git -C "$repo" update-index --force-remove -- "${a#-}" ;;
      '~'*)
        par=${a#\~}
        origen=${par%%>*}
        destino=${par#*>}
        sha=$(git -C "$repo" rev-parse --verify ":0:$origen")
        git -C "$repo" update-index --force-remove -- "$origen"
        git -C "$repo" update-index --add --cacheinfo "100644,$sha,$destino"
        ;;
    esac
  done
}

base_paths=(
  backend/pom.xml backend/mvnw backend/mvnw.cmd backend/.mvn/wrapper/maven-wrapper.properties
  backend/contracts/openapi/api.yaml backend/src/main/java/A.java backend/README.md
  backend/bff/pom.xml backend/bff/mvnw backend/bff/mvnw.cmd backend/bff/.mvn/wrapper/maven-wrapper.properties
  backend/bff/contracts/openapi/bff.yaml backend/bff/src/main/java/B.java
  frontend/src/a.ts frontend/README.md ui/src/b.ts
  docs/x.md spec/s.md e2e/e.ts tools/t.sh .claude/c.md
  README.md AGENTS.md .gitignore .mcp.json .gitattributes Makefile .github/workflows/w.yml
)

# El índice de base nunca se modifica: cada caso parte de una copia en GIT_INDEX_FILE.
export GIT_INDEX_FILE="$tmp/indice-base"
aplicar "${base_paths[@]/#/+}"
arbol_base=$(git -C "$repo" write-tree)
base=$(git -C "$repo" commit-tree "$arbol_base" -m base)

# La punta de main avanzó con un cambio ajeno al PR: el merge no es un avance rápido.
export GIT_INDEX_FILE="$tmp/indice-trabajo"
cp "$tmp/indice-base" "$GIT_INDEX_FILE"
aplicar +zz-main-avanzo.txt
cp "$GIT_INDEX_FILE" "$tmp/indice-main"
main=$(git -C "$repo" commit-tree "$(git -C "$repo" write-tree)" -p "$base" -m main)

proyectos=(backend bff frontend ui contratos)
TODOS=TTTTT
NINGUNO=FFFFF

# Corre aplica.sh para cada proyecto y compara con lo esperado.
# $1 nombre, $2 esperado en el orden de $proyectos (T aplica, F no), $3 directorio de trabajo, $4 qué se compara:
# `salida` (solo la salida estándar) o `todo` (salida estándar y de error juntas, para probar que no se filtra nada).
verifica_proyectos() {
  local nombre=$1 esperado=$2 dir=$3 flujos=${4:-salida}
  local i obtenido esperado_i codigo lineas
  # Los cinco proyectos en paralelo: cada llamada a Bash y a Git es lenta en Windows.
  for i in 0 1 2 3 4; do
    (
      cd "$dir" || exit 1
      if [ "$flujos" = todo ]; then
        bash "$script" "${proyectos[$i]}" > "$tmp/salida.$i" 2>&1
      else
        bash "$script" "${proyectos[$i]}" > "$tmp/salida.$i" 2> /dev/null
      fi
      echo $? > "$tmp/codigo.$i"
    ) &
  done
  wait
  for i in 0 1 2 3 4; do
    mapfile -t lineas < "$tmp/salida.$i"
    obtenido=$(cat "$tmp/salida.$i")
    codigo=$(cat "$tmp/codigo.$i")
    esperado_i=aplica=false
    [ "${esperado:$i:1}" = T ] && esperado_i=aplica=true
    # Exactamente una línea: una salida vacía o con líneas de más es un fallo, aunque el valor sea el esperado.
    if [ "$obtenido" != "$esperado_i" ] || [ "$codigo" -ne 0 ] || [ "${#lineas[@]}" -ne 1 ]; then
      falla "$nombre · ${proyectos[$i]}" "esperado «$esperado_i» (código 0), obtenido «$obtenido» (código $codigo)"
    else
      ok "$nombre · ${proyectos[$i]}"
    fi
  done
}

# PR con esas acciones, integrado en main con un merge real. $1 nombre, $2 esperado, resto: acciones; si la primera es
# `--todo`, se compara también la salida de error (ver verifica_proyectos).
pr() {
  local nombre=$1 esperado=$2 flujos=salida arbol_pr commit_pr merge
  shift 2
  if [ "${1:-}" = --todo ]; then flujos=todo; shift; fi
  cp "$tmp/indice-base" "$GIT_INDEX_FILE"
  aplicar "$@"
  arbol_pr=$(git -C "$repo" write-tree)
  commit_pr=$(git -C "$repo" commit-tree "$arbol_pr" -p "$base" -m pr)
  cp "$tmp/indice-main" "$GIT_INDEX_FILE"
  aplicar "$@"
  merge=$(git -C "$repo" commit-tree "$(git -C "$repo" write-tree)" -p "$main" -p "$commit_pr" -m merge)
  git -C "$repo" update-ref refs/heads/pr "$merge"
  verifica_proyectos "$nombre" "$esperado" "$repo" "$flujos"
}

# Un solo resultado esperado, con otros argumentos o en otro directorio.
verifica() {
  local nombre=$1 esperado=$2 dir=$3 obtenido codigo
  shift 3
  obtenido=$(cd "$dir" && bash "$script" "$@" 2> /dev/null)
  codigo=$?
  if [ "$obtenido" != "$esperado" ] || [ "$codigo" -ne 0 ]; then
    falla "$nombre" "esperado «$esperado» (código 0), obtenido «$obtenido» (código $codigo)"
  else
    ok "$nombre"
  fi
}

# ---------------------------------------------------------------------------------------------------------------
# Un archivo del proyecto aplica; uno de otro proyecto, no.            backend bff frontend ui contratos
# ---------------------------------------------------------------------------------------------------------------
pr "código del backend"                      TFFFF  +backend/src/main/java/A.java
pr "código del BFF"                          FTFFF  +backend/bff/src/main/java/B.java
pr "código nuevo del backend"                TFFFF  +backend/src/main/java/Nueva.java
pr "frontend"                                FFTFF  +frontend/src/a.ts
pr "ui"                                      FFFTF  +ui/src/b.ts
pr "backend y ui juntos"                     TFFTF  +backend/src/main/java/A.java +ui/src/b.ts
pr "documentación y frontend"                FFTFF  +docs/x.md +frontend/src/a.ts

# Archivos que salen del proyecto: se mueven o se borran.
pr "frontend movido a docs"                  FFTFF  '~frontend/src/a.ts>docs/a.ts'
pr "ui borrado"                              FFFTF  -ui/src/b.ts
pr "backend movido a frontend"               TFTFF  '~backend/src/main/java/A.java>frontend/src/A.java'
pr "bff movido a docs"                       FTFFF  '~backend/bff/src/main/java/B.java>docs/B.java'
pr "docs movido a ui"                        FFFTF  '~docs/x.md>ui/x.md'

# Lo que está en la raíz o fuera de la lista conocida hace aplicar a todos.
pr "workflow de .github"                     $TODOS +.github/workflows/w.yml
pr "script nuevo de .github"                 $TODOS +.github/scripts/x.sh
pr ".github/x.md no es neutro"               $TODOS +.github/x.md
pr ".gitattributes"                          $TODOS +.gitattributes
pr "archivo desconocido de la raíz"          $TODOS +Makefile
pr "archivo nuevo de la raíz"                $TODOS +LICENSE
pr "carpeta desconocida"                     $TODOS +nuevo/x.txt
pr "carpeta desconocida con documentación"   $TODOS +nuevo/x.txt +docs/x.md
pr "raíz junto a un solo proyecto"           $TODOS +.gitattributes +ui/src/b.ts
pr "carpeta con nombre parecido a docs"      $TODOS +docs-extra/x.txt

# Lo que no afecta a ningún proyecto.
pr "solo docs"                               $NINGUNO +docs/x.md
pr "solo spec"                               $NINGUNO +spec/s.md
pr "solo e2e"                                $NINGUNO +e2e/e.ts
pr "solo tools"                              $NINGUNO +tools/t.sh
pr "solo .claude"                            $NINGUNO +.claude/c.md
pr "README.md de la raíz"                    $NINGUNO +README.md
pr "AGENTS.md de la raíz"                    $NINGUNO +AGENTS.md
pr ".gitignore"                              $NINGUNO +.gitignore
pr ".mcp.json"                               $NINGUNO +.mcp.json
pr "todo lo neutro junto"                    $NINGUNO +docs/x.md +spec/s.md +e2e/e.ts +tools/t.sh +.claude/c.md +README.md +.gitignore +.mcp.json
pr "documentación borrada"                   $NINGUNO -docs/x.md
pr "documentación movida a spec"             $NINGUNO '~docs/x.md>spec/x.md'

# *.md solo es neutro en la raíz.
pr "backend/README.md"                       TFFFF  +backend/README.md
pr "frontend/README.md"                      FFTFF  +frontend/README.md
pr "README.md dentro de una carpeta nueva"   $TODOS +nuevo/README.md

# Nombres raros: se leen separados por NUL y nunca se ejecutan ni se interpretan.
pr "tilde en un archivo del frontend"        FFTFF  +'frontend/src/café.ts'
pr "ñ y espacios en un archivo de docs"      $NINGUNO +'docs/ñandú del sur.md'
pr "ñ y espacios en una carpeta desconocida" $TODOS +'nuevo/ñandú del sur.md'
pr "comillas en un archivo de ui"            FFFTF  +'ui/src/"comillas".ts'
pr "comillas en un archivo de docs"          $NINGUNO +'docs/"comillas".md'
pr "salto de línea en un archivo del backend" TFFFF +$'backend/src/con\nsalto.java'
pr "salto de línea que imita un archivo de docs" $TODOS +$'nuevo/x\ndocs/y.md'
pr "salto de línea que imita una ruta de la raíz" $NINGUNO +$'docs/x\n.github/workflows/w.yml'
pr "nombre con parte de una orden"           $NINGUNO +'docs/$(touch pwn);`id`.md'
# El nombre no se imprime ni se interpreta como una orden de GitHub Actions: la salida es solo aplica=false.
pr "nombre ::error:: no llega a la salida"   $NINGUNO --todo +'docs/::error::fuga-secreta.md'

# Contratos: backend/contracts, backend/bff/contracts, los dos pom.xml y el wrapper de Maven de los dos.
pr "contratos del backend"                   TFFFT  +backend/contracts/openapi/api.yaml
pr "contratos del BFF"                       FTFFT  +backend/bff/contracts/openapi/bff.yaml
pr "pom.xml del backend"                     TFFFT  +backend/pom.xml
pr "pom.xml del BFF"                         FTFFT  +backend/bff/pom.xml
pr "mvnw del backend"                        TFFFT  +backend/mvnw
pr "mvnw.cmd del backend"                    TFFFT  +backend/mvnw.cmd
pr ".mvn del backend"                        TFFFT  +backend/.mvn/wrapper/maven-wrapper.properties
pr "mvnw del BFF"                            FTFFT  +backend/bff/mvnw
pr "mvnw.cmd del BFF"                        FTFFT  +backend/bff/mvnw.cmd
pr ".mvn del BFF"                            FTFFT  +backend/bff/.mvn/wrapper/maven-wrapper.properties
pr "contratos y documentación"               TFFFT  +docs/x.md +backend/contracts/openapi/api.yaml
pr "contratos del backend y del BFF"         TTFFT  +backend/contracts/openapi/api.yaml +backend/bff/contracts/openapi/bff.yaml
pr "contrato borrado"                        TFFFT  -backend/contracts/openapi/api.yaml
pr "contrato movido a docs"                  TFFFT  '~backend/contracts/openapi/api.yaml>docs/api.yaml'
pr "código Java del backend, sin contratos"  TFFFF  +backend/src/main/java/A.java
pr "código Java del BFF, sin contratos"      FTFFF  +backend/bff/src/main/java/B.java
pr "frontend y ui, sin contratos"            FFTTF  +frontend/src/a.ts +ui/src/b.ts
pr "solo documentación, sin contratos"       $NINGUNO +docs/x.md
pr ".github hace correr los contratos"       $TODOS +.github/workflows/w.yml
pr "ruta desconocida hace correr contratos"  $TODOS +nuevo/x.txt

# Una lista de archivos vacía no prueba que nada cambió: aplica a todos.
cp "$tmp/indice-main" "$GIT_INDEX_FILE"
arbol_main=$(git -C "$repo" write-tree)
# Un merge cuyo árbol es el de main: el PR no cambia ningún archivo.
igual_a_main=$(git -C "$repo" commit-tree "$arbol_main" -p "$base" -m igual)
vacio=$(git -C "$repo" commit-tree "$arbol_main" -p "$main" -p "$igual_a_main" -m vacio)
git -C "$repo" update-ref refs/heads/pr "$vacio"
verifica_proyectos "lista de archivos vacía" $TODOS "$repo"

# ---------------------------------------------------------------------------------------------------------------
# Errores: ante la duda, aplica.
# ---------------------------------------------------------------------------------------------------------------
# Un PR de solo documentación, que sin errores no aplicaría a ningún proyecto.
solo_docs=$(cp "$tmp/indice-base" "$GIT_INDEX_FILE"; aplicar +docs/x.md > /dev/null; git -C "$repo" write-tree)
solo_docs_main=$(cp "$tmp/indice-main" "$GIT_INDEX_FILE"; aplicar +docs/x.md > /dev/null; git -C "$repo" write-tree)
commit_docs=$(git -C "$repo" commit-tree "$solo_docs" -p "$base" -m docs)
merge_docs=$(git -C "$repo" commit-tree "$solo_docs_main" -p "$main" -p "$commit_docs" -m merge)
git -C "$repo" update-ref refs/heads/pr "$merge_docs"

verifica "sin argumento" "aplica=true" "$repo"
verifica "proyecto desconocido" "aplica=true" "$repo" noexiste
verifica "proyecto vacío" "aplica=true" "$repo" ""
verifica "proyecto con mayúsculas" "aplica=true" "$repo" Backend
verifica "más de un argumento" "aplica=true" "$repo" backend ui

# Sin padre: un solo commit.
sin_padre="$tmp/sin-padre"
git init -q "$sin_padre"
git -C "$sin_padre" config commit.gpgsign false
git -C "$sin_padre" symbolic-ref HEAD refs/heads/pr
raiz=$(git -C "$sin_padre" commit-tree "$(git -C "$sin_padre" hash-object -t tree /dev/null)" -m raiz)
git -C "$sin_padre" update-ref refs/heads/pr "$raiz"
for p in "${proyectos[@]}"; do
  verifica "sin padre · $p" "aplica=true" "$sin_padre" "$p"
done

# HEAD inválido: repositorio sin commits.
sin_commits="$tmp/sin-commits"
git init -q "$sin_commits"
for p in "${proyectos[@]}"; do
  verifica "HEAD inválido · $p" "aplica=true" "$sin_commits" "$p"
done

# HEAD que apunta a un objeto que no existe.
roto="$tmp/roto"
git init -q "$roto"
git -C "$roto" symbolic-ref HEAD refs/heads/pr
mkdir -p "$roto/.git/refs/heads"
echo 0123456789012345678901234567890123456789 > "$roto/.git/refs/heads/pr"
for p in "${proyectos[@]}"; do
  verifica "HEAD roto · $p" "aplica=true" "$roto" "$p"
done

# Fuera de un repositorio de Git.
mkdir "$tmp/fuera"
export GIT_CEILING_DIRECTORIES="$tmp"
for p in "${proyectos[@]}"; do
  verifica "fuera de un repositorio · $p" "aplica=true" "$tmp/fuera" "$p"
done
unset GIT_CEILING_DIRECTORIES

# ---------------------------------------------------------------------------------------------------------------
echo
if [ "$fallos" -ne 0 ]; then
  echo "$fallos de $total pruebas fallaron."
  exit 1
fi
echo "$total pruebas en verde."
