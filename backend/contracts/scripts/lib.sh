# Funciones comunes de compare.sh y publish.sh. Se carga con `. contracts/scripts/lib.sh` desde backend/.
#
# Un contrato es cada YAML de estas carpetas y se identifica por el nombre del archivo: debe ser único entre
# las dos, porque nombra su release (contract-<nombre>-vX.Y.Z). bff/contracts/openapi/ es la del BFF (T19);
# mientras no exista, no aporta contratos.
CONTRACT_DIRS="contracts/openapi bff/contracts/openapi"

# Reporte de cada contrato. En GitHub Actions, las fallas salen además como anotaciones del PR.
ok() { printf '  ok         %s\n' "$1"; }
falla() { # falla CODIGO CONTRATO MENSAJE
  printf '  FALLA      [%s] %s: %s\n' "$1" "$2" "$3"
  [ "${GITHUB_ACTIONS:-}" = true ] && printf '::error title=Contrato %s [%s]::%s\n' "$2" "$1" "$3"
  fallas=$((fallas + 1))
}

# Copia un contrato a la carpeta de trabajo sin retornos de carro, para que un checkout con autocrlf en
# Windows no cuente como cambio. Falla si el nombre no sirve para una etiqueta o si ya hay uno igual.
add_contract() { # add_contract NOMBRE_DE_ARCHIVO CARPETA_DESTINO < contenido
  if ! printf '%s' "$1" | grep -Eq '^[a-z][a-z0-9-]*\.yaml$'; then
    echo "El contrato $1 no tiene un nombre válido: minúsculas, dígitos y guiones, con extensión .yaml." >&2
    return 1
  fi
  if [ -e "$2/$1" ]; then
    echo "Hay dos contratos llamados $1 en $CONTRACT_DIRS: el nombre debe ser único." >&2
    return 1
  fi
  tr -d '\r' > "$2/$1"
}

# Copia los contratos del árbol de trabajo.
collect_worktree() { # collect_worktree CARPETA_DESTINO
  for carpeta in $CONTRACT_DIRS; do
    for archivo in "$carpeta"/*.yaml; do
      [ -f "$archivo" ] || continue
      add_contract "$(basename "$archivo")" "$1" < "$archivo" || return 1
    done
  done
}

# Copia los contratos tal como están en una referencia de Git (rama, etiqueta o commit).
collect_ref() { # collect_ref REFERENCIA CARPETA_DESTINO
  if ! git rev-parse -q --verify "$1^{commit}" > /dev/null; then
    echo "No existe la referencia de Git $1." >&2
    return 1
  fi
  for carpeta in $CONTRACT_DIRS; do
    # Sin -r: solo los archivos de la carpeta, no los de sus subcarpetas, igual que collect_worktree.
    for ruta in $(git ls-tree --name-only "$1" -- "$carpeta/"); do
      case "$ruta" in *.yaml) ;; *) continue ;; esac
      git show "$1:./$ruta" | add_contract "$(basename "$ruta")" "$2" || return 1
    done
  done
}

# info.version del contrato: la clave version dentro del bloque info del primer nivel.
contract_version() { # contract_version ARCHIVO
  awk '/^info:/ { dentro = 1; next }
       dentro && /^[^ #]/ { exit }
       dentro && /^  version:/ { sub(/^  version:[ ]*/, ""); gsub(/["'\'' \r]/, ""); print; exit }' "$1"
}

is_semver() { printf '%s' "$1" | grep -Eq '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'; }

major() { printf '%s' "${1%%.*}"; }

# Verdadero si la versión A es mayor que la B; las dos deben ser X.Y.Z.
semver_gt() { # semver_gt A B
  printf '%s\n%s\n' "$1" "$2" | awk -F. 'NR == 1 { split($0, a, ".") } NR == 2 { split($0, b, ".") }
    END { for (i = 1; i <= 3; i++) { if (a[i] + 0 > b[i] + 0) exit 0; if (a[i] + 0 < b[i] + 0) exit 1 } exit 1 }'
}
