# Funciones comunes de compare.sh y publish.sh, que la cargan con `. "$scripts/lib.sh"`. El script que la carga
# define fallas=0: falla() la incrementa.
#
# Un contrato es cada archivo .yaml de contracts/openapi/ del proyecto: este (backend/) y, desde T18, el BFF
# (backend/bff/), que tiene su propia copia de estos scripts para seguir siendo independiente. El nombre del
# archivo identifica al contrato y nombra su release (contract-<nombre>-vX.Y.Z), así que es único en todo el
# repositorio. La carpeta no admite otros archivos ni subcarpetas: un .yml o un archivo referido con $ref
# quedarían fuera de la comparación y de la publicación.
CONTRACT_DIR=contracts/openapi

# Reporte de cada contrato. En GitHub Actions, las fallas salen además como anotaciones.
ok() { printf '  ok         %s\n' "$1"; }
falla() { # falla CODIGO CONTRATO MENSAJE
  printf '  FALLA      [%s] %s: %s\n' "$1" "$2" "$3"
  [ "${GITHUB_ACTIONS:-}" = true ] && printf '::error title=Contrato %s [%s]::%s\n' "$2" "$1" "$3"
  fallas=$((fallas + 1))
}

# Ruta que entiende Java: en Git Bash, /c/Users/... pasa a C:/Users/...; en Linux queda igual.
java_path() {
  if command -v cygpath > /dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi
}

# Copia un contrato a la carpeta de trabajo sin retornos de carro, para que un checkout con autocrlf en
# Windows no cuente como cambio. Rechaza lo que no es un contrato con un nombre válido.
add_contract() { # add_contract NOMBRE_DE_ARCHIVO CARPETA_DESTINO < contenido
  case "$1" in
    *.yaml) ;;
    *)
      falla UNEXPECTED_FILE "$1" "en $CONTRACT_DIR/ solo van contratos .yaml, sin subcarpetas ni otros archivos."
      return 1
      ;;
  esac
  if ! printf '%s' "$1" | grep -Eq '^[a-z][a-z0-9-]*\.yaml$'; then
    falla INVALID_NAME "$1" "el nombre lleva solo minúsculas, dígitos y guiones, porque nombra la release."
    return 1
  fi
  tr -d '\r' > "$2/$1"
}

# Copia los contratos del árbol de trabajo, también los archivos ocultos: Git sí los ve, y uno que pasara aquí
# bloquearía después la comparación en main.
collect_worktree() { # collect_worktree CARPETA_DESTINO
  [ -d "$CONTRACT_DIR" ] || return 0
  for entrada in "$CONTRACT_DIR"/* "$CONTRACT_DIR"/.[!.]* "$CONTRACT_DIR"/..?*; do
    # Un enlace simbólico se lee distinto aquí (su destino) que en Git (su ruta): se rechaza, también si está roto.
    if [ -L "$entrada" ]; then
      falla UNEXPECTED_FILE "$(basename "$entrada")" "en $CONTRACT_DIR/ no van enlaces simbólicos."
      return 1
    fi
    [ -e "$entrada" ] || continue
    if [ -d "$entrada" ]; then
      falla UNEXPECTED_FILE "$(basename "$entrada")" "en $CONTRACT_DIR/ no van subcarpetas."
      return 1
    fi
    add_contract "$(basename "$entrada")" "$1" < "$entrada" || return 1
  done
}

# Copia los contratos tal como están en una referencia de Git (rama, etiqueta o commit). Cada orden de Git se
# comprueba: un error no puede dejar la base vacía, porque todo pasaría como contrato nuevo.
collect_ref() { # collect_ref REFERENCIA CARPETA_DESTINO
  if ! git rev-parse -q --verify "$1^{commit}" > /dev/null; then
    falla GIT_ERROR "$1" "no existe esa referencia de Git."
    return 1
  fi
  lista="$2.ls-tree"
  if ! git ls-tree "$1" -- "$CONTRACT_DIR/" > "$lista"; then
    falla GIT_ERROR "$1" "no se pudo listar $CONTRACT_DIR/."
    return 1
  fi
  # Cada línea de ls-tree: modo, tipo y objeto, y tras un tabulador la ruta. El modo 120000 es un enlace simbólico.
  while read -r modo tipo _objeto ruta; do
    if [ "$tipo" != blob ] || [ "$modo" = 120000 ]; then
      falla UNEXPECTED_FILE "$(basename "$ruta")" "en $CONTRACT_DIR/ no van subcarpetas ni enlaces simbólicos."
      return 1
    fi
    # MSYS_NO_PATHCONV: Git Bash no convierte «referencia:ruta» como si fuera una ruta de Windows.
    if ! MSYS_NO_PATHCONV=1 git show "$1:./$ruta" > "$lista.contenido"; then
      falla GIT_ERROR "$(basename "$ruta")" "no se pudo leer en $1."
      return 1
    fi
    add_contract "$(basename "$ruta")" "$2" < "$lista.contenido" || return 1
  done < "$lista"
}

# info.version del contrato: la clave version dentro del bloque info del primer nivel, con la clave entre comillas
# o sin ellas y con espacio o sin él antes de los dos puntos. Si el valor va entre comillas, es lo de dentro (un #
# ahí no es comentario); si no, se quita el comentario. Si info o version están repetidos, o info va en estilo de
# flujo, no devuelve nada, porque un lector de YAML podría leer otra versión: el contrato queda con versión
# inválida. En el awk, \047 es la comilla simple.
contract_version() { # contract_version ARCHIVO
  awk '{ sub(/\r$/, "") }
       /^["\047]?info["\047]?[ \t]*:/ { infos++; dentro = (infos == 1); next }
       /^[^ #]/ { dentro = 0 }
       dentro && /^  ["\047]?version["\047]?[ \t]*:/ { veces++; linea = $0 }
       END {
         if (infos != 1 || veces != 1) exit
         sub(/^  ["\047]?version["\047]?[ \t]*:[ \t]*/, "", linea)
         if (linea ~ /^"/) { sub(/^"/, "", linea); sub(/".*$/, "", linea) }
         else if (linea ~ /^\047/) { sub(/^\047/, "", linea); sub(/\047.*$/, "", linea) }
         else { sub(/[ \t]+#.*$/, "", linea); sub(/[ \t]+$/, "", linea) }
         print linea
       }' "$1"
}

# Deja en $trabajo la carpeta de trabajo, absoluta y vacía. Antes de borrarla comprueba, con las rutas reales
# (pwd -P, sin enlaces simbólicos), que no sea la raíz, que no contenga el proyecto ni la carpeta de los contratos,
# y que, si está dentro de alguno de ellos, esté en su target/: LIV_CONTRACTS_WORK=. o =src no borran nada.
prepare_workdir() { # prepare_workdir CARPETA CARPETA_DE_LOS_CONTRATOS
  mkdir -p "$1" || exit 2
  trabajo=$(cd "$1" && pwd -P) || exit 2
  inseguro=false
  [ "$trabajo" = / ] && inseguro=true
  for protegida in "$(cd "$proyecto" && pwd -P)" "$(cd "$2" && pwd -P)"; do
    case "$protegida/" in "$trabajo"/*) inseguro=true ;; esac
    case "$trabajo/" in
      "$protegida"/target/*) ;;
      "$protegida"/*) inseguro=true ;;
    esac
  done
  if [ "$inseguro" = true ]; then
    falla UNSAFE_WORKDIR "$trabajo" "la carpeta de trabajo debe estar en target/ o fuera del proyecto; no se borra."
    exit 1
  fi
  rm -rf "$trabajo"
  mkdir -p "$trabajo"
}

is_semver() { printf '%s' "$1" | grep -Eq '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'; }

major() { printf '%s' "${1%%.*}"; }

# Verdadero si la versión A es mayor que la B (las dos X.Y.Z). Compara número por número, no como texto, así que
# 1.10.0 es mayor que 1.9.0.
semver_gt() { # semver_gt A B
  a_resto=${1#*.}
  b_resto=${2#*.}
  for par in "${1%%.*} ${2%%.*}" "${a_resto%%.*} ${b_resto%%.*}" "${a_resto#*.} ${b_resto#*.}"; do
    set -- $par
    [ "$1" -gt "$2" ] && return 0
    [ "$1" -lt "$2" ] && return 1
  done
  return 1
}
