#!/usr/bin/env bash
# Pruebas de .github/workflows/ui-release.yml (feature 002, criterio 002-08, y los controles de 002-07 que se ven en el
# archivo).
#
# `publicar` tiene `id-token: write` y `etiquetar` tiene `contents: write`: no pueden ejecutar nada que haya pasado por
# un trabajo que instaló las dependencias del proyecto. Esta prueba lee el workflow y falla si `publicar` tiene
# `checkout`, `npm ci`, caché, un script que no venga del artefacto de `decidir` con su huella comprobada en el mismo
# paso, o le falta `--ignore-scripts`; y las mismas reglas para `etiquetar`. No ejecuta el workflow: lo lee, y para
# comprobar que cada regla muerde, arma copias del workflow con un defecto y exige que la regla las rechace.
#
# Uso: bash .github/scripts/test/ui-release.test.sh
# Sale con 0 si todo pasa y con 1 si algo falla.
set -u

aqui=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
workflow="$aqui/../../workflows/ui-release.yml"
if [ ! -f "$workflow" ]; then
  echo "FALTA $workflow: no hay nada que probar." >&2
  exit 1
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# Lee el archivo sin los retornos de carro (en Windows, Git puede dejar CRLF).
leer() { tr -d '\r' < "$1"; }

# Líneas del trabajo $2 (entre su encabezado de dos espacios y el siguiente trabajo), sin los comentarios.
bloque() {
  leer "$1" | awk -v job="  $2:" '
    $0 == job { dentro = 1; next }
    dentro && /^  [A-Za-z_-]+:/ { exit }
    dentro && !/^ *#/ { print }'
}

# Pasos (que empiezan con «      - ») del bloque de la entrada estándar donde alguna línea cumple la expresión $1.
pasos_con() {
  awk -v re="$1" '
    /^      - / { if (hit) printf "%s", buf; buf = ""; hit = 0 }
    { buf = buf $0 "\n"; if ($0 ~ re) hit = 1 }
    END { if (hit) printf "%s", buf }'
}

# Nombres de los artefactos que descarga el bloque de la entrada estándar.
descargas() {
  awk '
    /download-artifact@/ { activo = 1; next }
    activo && /^ +name: / { sub(/^ +name: /, ""); print; activo = 0 }
    /^      - / { activo = 0 }'
}

# Cuántas veces aparece la expresión $1 en la entrada estándar.
cuenta() { grep -Ec -- "$1" || true; }

# Dice si, en el paso de la entrada estándar, la comprobación de la huella va antes de ejecutar el script $1.
huella_antes() {
  awk -v re="node .*$1" '
    /sha256sum --check --strict/ && !visto { visto = NR }
    $0 ~ re && !ejecuta { ejecuta = NR }
    END { exit !(visto && ejecuta && visto < ejecuta) }'
}

# Condición de `etiquetar` según el diseño técnico (spec.md): con la salida `etiquetar`, o tras un `publicar` en verde.
SI_ETIQUETAR="    if: "'${{ !cancelled() && github.ref == '"'refs/heads/main'"' && needs.decidir.result == '"'success'"' && (needs.decidir.outputs.salida == '"'etiquetar'"' || (needs.decidir.outputs.salida == '"'publicar'"' && needs.publicar.result == '"'success'"')) }}'

# Dice si ninguna comprobación de sha256sum viene después de la línea que ejecuta el script $1 (el paso la cierra antes).
huella_despues_de_todo() {
  awk -v re="node .*$1" '
    $0 ~ re && !ejecuta { ejecuta = NR }
    /sha256sum --check/ { ultimo = NR }
    END { exit !(ejecuta && ultimo < ejecuta) }'
}

# Revisa el workflow $1 y escribe una línea por cada regla que incumple. Sin líneas, todo en orden.
revisar() {
  local f=$1 decidir verificar publicar etiquetar paso descargado job
  decidir=$(bloque "$f" decidir)
  verificar=$(bloque "$f" verificar)
  publicar=$(bloque "$f" publicar)
  etiquetar=$(bloque "$f" etiquetar)

  for job in decidir verificar publicar etiquetar; do
    local contenido
    contenido=$(bloque "$f" "$job")
    [ -n "$contenido" ] || echo "falta el trabajo $job"
    # 002-07, control 3: cada trabajo exige la rama main.
    grep -Eq "^    if: .*github\.ref == 'refs/heads/main'" <<<"$contenido" \
      || echo "$job: su condición «if» no exige github.ref == 'refs/heads/main'"
  done

  # --- decidir: no instala nada ---
  grep -Eq 'npm (ci|install)' <<<"$decidir" && echo "decidir: instala dependencias"

  # --- los dos trabajos con permisos: ni checkout, ni instalar, ni caché ---
  for job in publicar etiquetar; do
    contenido=$(bloque "$f" "$job")
    grep -q 'actions/checkout' <<<"$contenido" && echo "$job: hace checkout"
    # Solo se permite instalar npm mismo, con --global, en `publicar`.
    if grep -E 'npm (ci|install|i |add)' <<<"$contenido" | grep -v -- '--global' | grep -q .; then
      echo "$job: instala dependencias del proyecto"
    fi
    grep -Eq '^ +(cache|cache-dependency-path):|package-manager-cache: *[^f ]|actions/cache@' <<<"$contenido" \
      && echo "$job: usa caché"
    [ "$(cuenta 'actions/setup-node@' <<<"$contenido")" -eq "$(cuenta 'package-manager-cache: false' <<<"$contenido")" ] \
      || echo "$job: algún setup-node no desactiva la caché (package-manager-cache: false)"
    grep -q 'dry-run' <<<"$contenido" && echo "$job: aún tiene --dry-run"
    # Todo script se ejecuta desde el artefacto de decidir, bajado a $RUNNER_TEMP/scripts.
    while IFS= read -r linea; do
      case "$linea" in
        'node "$RUNNER_TEMP/scripts/release-publish.mjs"'* | 'node "$RUNNER_TEMP/scripts/release-tag.mjs"'*) ;;
        *) echo "$job: ejecuta un script que no es del artefacto de decidir: $linea" ;;
      esac
    done < <(grep -E '^ +node ' <<<"$contenido" | sed -E 's/^ +//')
    # La comprobación de la huella o del SHA-256 no puede quedar anulada con `|| true` o parecido.
    if grep -E 'sha256sum --check.*([|][|]|;)' <<<"$contenido" | grep -q .; then
      echo "$job: la comprobación de sha256sum puede quedar anulada (|| o ;)"
    fi
  done

  # Permisos explícitos y mínimos, y sin `env:` a nivel del trabajo (ahí viajaría un token a todos los pasos).
  # Los valores de otros trabajos llegan por `env:` o `with:`, nunca dentro de un `run:`. Los cuatro trabajos.
  for job in decidir verificar publicar etiquetar; do
    contenido=$(bloque "$f" "$job")
    grep -Eq '^    permissions:$' <<<"$contenido" || echo "$job: no declara sus permisos"
    grep -Eq 'write-all|read-all|permissions: *(write|read)' <<<"$contenido" && echo "$job: permisos demasiado amplios"
    grep -Eq '^    env:' <<<"$contenido" && echo "$job: tiene env a nivel del trabajo"
    if grep -E '\$\{\{' <<<"$contenido" | grep -Ev '^ +(- )?[A-Za-z0-9_-]+: ' | grep -q .; then
      echo "$job: interpola una expresión \${{ }} dentro de un run (o en un valor de varias líneas)"
    fi
    if grep -E '\$\{\{' <<<"$contenido" | grep -Eq '^ +(- )?run: '; then
      echo "$job: interpola una expresión \${{ }} en la línea de un run"
    fi
  done

  # --- nada de lo que corre con permisos puede seguir tras un fallo, ni cambiar la shell ---
  for job in verificar publicar etiquetar; do
    contenido=$(bloque "$f" "$job")
    grep -Eq 'continue-on-error|set \+e|set \+o errexit|trap ' <<<"$contenido" \
      && echo "$job: puede seguir tras un fallo (continue-on-error, set +e o trap)"
    grep -E 'shell:' <<<"$contenido" | grep -Ev 'shell: bash$' | grep -q . && echo "$job: cambia la shell de bash (pierde -e y pipefail)"
  done

  # --- todo el archivo: el token, los secretos y los env de primer nivel ---
  local archivo
  archivo=$(leer "$f" | grep -v '^ *#')
  grep -Eq '^env:' <<<"$archivo" && echo "el workflow tiene env a nivel del archivo (llegaría a todos los trabajos)"
  [ "$(cuenta 'github[.]token' <<<"$archivo")" -eq 1 ] || echo "github.token debe aparecer una sola vez en el archivo (en el paso de etiquetar)"
  grep -Eq 'secrets[.]|toJSON[(]github' <<<"$archivo" && echo "el workflow usa secrets. o toJSON(github), que llevan el token o una clave"
  grep -q 'GH_TOKEN: ${{ github.token }}$' <<<"$archivo" || echo "el token de etiquetar no es github.token"

  # --- publicar ---
  grep -Eq '^    environment: npm$' <<<"$publicar" || echo "publicar: no está en el entorno npm"
  grep -q 'id-token: write' <<<"$publicar" || echo "publicar: no tiene id-token: write"
  grep -q 'contents: write' <<<"$publicar" && echo "publicar: tiene contents: write"
  grep -q -- '--ignore-scripts' <<<"$publicar" || echo "publicar: falta --ignore-scripts"
  grep -Eq 'GH_TOKEN|github\.token|secrets\.' <<<"$publicar" && echo "publicar: usa un token o un secreto"
  while IFS= read -r descargado; do
    case "$descargado" in
      release-scripts | paquete) ;;
      *) echo "publicar: descarga un artefacto que no es release-scripts ni paquete: $descargado" ;;
    esac
  done < <(descargas <<<"$publicar")
  grep -q 'needs\.verificar\.outputs\.sha256' <<<"$publicar" || echo "publicar: no comprueba el SHA-256 de verificar"
  [ "$(grep -o 'needs\.verificar\.outputs\.[a-z0-9_]*' <<<"$publicar" | sort -u | grep -vc 'outputs\.sha256$')" -eq 0 ] \
    || echo "publicar: toma de verificar algo que no es el SHA-256 del paquete"
  paso=$(pasos_con 'node .*release-publish[.]mjs' <<<"$publicar")
  [ -n "$paso" ] || echo "publicar: no ejecuta release-publish.mjs"
  grep -q 'huella_publicar' <<<"$paso" || echo "publicar: no usa la huella de decidir para release-publish.mjs"
  huella_antes 'release-publish[.]mjs' <<<"$paso" \
    || echo "publicar: no comprueba la huella (sha256sum --check --strict) antes de ejecutar release-publish.mjs en el mismo paso"
  grep -Eq '"[$]HUELLA" release-publish[.]mjs [|] sha256sum --check --strict$' <<<"$paso" \
    || echo "publicar: la huella comprobada no es la que entregó decidir (HUELLA con release-publish.mjs)"
  grep -Eq '"[$]SHA256" "[$]NOMBRE-[$]VERSION[.]tgz" [|] sha256sum --check --strict$' <<<"$paso" \
    || echo "publicar: no comprueba el SHA-256 del .tgz en el mismo paso que lo publica"
  grep -q "NPM_CONFIG_PROVENANCE: 'true'" <<<"$paso" || echo "publicar: no exige la procedencia (NPM_CONFIG_PROVENANCE)"
  [ "$(grep -ic 'provenance' <<<"$publicar")" -eq 1 ] || echo "publicar: la procedencia se define más de una vez"
  grep -q '"[$]RUNNER_TEMP/paquete/[$]NOMBRE-[$]VERSION[.]tgz" "[$]NOMBRE" "[$]VERSION"' <<<"$paso" \
    || echo "publicar: no publica el .tgz que se comprobó (RUNNER_TEMP/paquete/NOMBRE-VERSION.tgz)"
  huella_despues_de_todo 'release-publish[.]mjs' <<<"$paso" || echo "publicar: hay una comprobación de sha256sum después de ejecutar el script"

  # --- etiquetar ---
  grep -q 'contents: write' <<<"$etiquetar" || echo "etiquetar: no tiene contents: write"
  grep -q 'id-token' <<<"$etiquetar" && echo "etiquetar: tiene id-token"
  grep -Eq '^    environment:' <<<"$etiquetar" && echo "etiquetar: está en un entorno"
  grep -q "needs\.publicar\.result == 'success'" <<<"$etiquetar" \
    || echo "etiquetar: corre sin exigir que publicar haya terminado bien"
  grep -q "needs\.decidir\.outputs\.salida == 'etiquetar'" <<<"$etiquetar" \
    || echo "etiquetar: no corre cuando decidir da etiquetar"
  grep -E '^    if: ' <<<"$etiquetar" | grep -q 'always()' && echo "etiquetar: usa always(), que corre aunque se cancele la ejecución"
  grep -E '^    if: ' <<<"$etiquetar" | grep -q '!cancelled()' || echo "etiquetar: sin !cancelled() no corre cuando publicar se omite"
  grep -q "needs[.]decidir[.]result == 'success'" <<<"$etiquetar" || echo "etiquetar: no exige que decidir haya terminado bien"
  [ "$(grep -E '^    if: ' <<<"$etiquetar")" = "$SI_ETIQUETAR" ] || echo "etiquetar: su condición «if» no es la del diseño"
  grep -q 'actions/setup-node' <<<"$etiquetar" && echo "etiquetar: usa setup-node, que recibe github.token por omisión"
  grep -q 'needs\.verificar' <<<"$etiquetar" && echo "etiquetar: usa algo de verificar"
  [ "$(descargas <<<"$etiquetar" | sort -u | tr '\n' ' ')" = "release-scripts " ] \
    || echo "etiquetar: debe descargar solo el artefacto release-scripts"
  [ "$(cuenta 'GH_TOKEN' <<<"$etiquetar")" -eq 1 ] || echo "etiquetar: el token debe aparecer en un solo lugar"
  paso=$(pasos_con 'GH_TOKEN' <<<"$etiquetar")
  [ "$(grep -Ec 'github[.]token|secrets[.]' <<<"$etiquetar")" -eq "$(grep -Ec 'github[.]token|secrets[.]' <<<"$paso")" ] \
    || echo "etiquetar: github.token o un secreto fuera del paso que ejecuta release-tag.mjs"
  grep -q 'node "[$]RUNNER_TEMP/scripts/release-tag[.]mjs"' <<<"$paso" || echo "etiquetar: el token no está solo en el paso que ejecuta release-tag.mjs"
  grep -q 'huella_etiquetar' <<<"$paso" && grep -q 'huella_notas' <<<"$paso" \
    || echo "etiquetar: no usa las huellas de decidir para release-tag.mjs y las notas"
  grep -Eq '"[$]HUELLA_ETIQUETAR" release-tag[.]mjs "[$]HUELLA_NOTAS" notas[.]md [|] sha256sum --check --strict$' <<<"$paso" \
    || echo "etiquetar: las huellas comprobadas no son las que entregó decidir"
  grep -q 'GITHUB_SHA' <<<"$paso" || echo "etiquetar: no etiqueta el commit de esta ejecución (GITHUB_SHA)"
  grep -q '"[$]COMMIT" != "[$]GITHUB_SHA"' <<<"$paso" || echo "etiquetar: no comprueba que el commit decidido sea el de esta ejecución"
  grep -q 'release-tag[.]mjs" "[$]NOMBRE" "[$]VERSION" "[$]GITHUB_SHA"' <<<"$paso" \
    || echo "etiquetar: no pasa GITHUB_SHA a release-tag.mjs como commit"
  huella_despues_de_todo 'release-tag[.]mjs' <<<"$paso" || echo "etiquetar: hay una comprobación de sha256sum después de ejecutar el script"
  huella_antes 'release-tag[.]mjs' <<<"$paso" \
    || echo "etiquetar: no comprueba las huellas antes de ejecutar release-tag.mjs en el mismo paso"

  # --- permisos de todo el archivo ---
  [ "$(cuenta 'id-token: write' < <(leer "$f" | grep -v '^ *#'))" -eq 1 ] || echo "id-token: write debe aparecer solo en publicar"
  [ "$(cuenta 'contents: write' < <(leer "$f" | grep -v '^ *#'))" -eq 1 ] || echo "contents: write debe aparecer solo en etiquetar"
}

# ---------------------------------------------------------------------------------------------------------------
total=0
fallos=0
ok() { total=$((total + 1)); echo "ok    - $1"; }
falla() { total=$((total + 1)); fallos=$((fallos + 1)); echo "FALLA - $1"; echo "        $2"; }

# Reemplaza en el trabajo $2 de la copia $1 el texto $3 por $4 (texto literal; todas las apariciones).
muta() {
  local copia=$1 job=$2 desde=$3 hasta=$4 destino="$tmp/mutado.yml"
  leer "$copia" | awk -v job="  $job:" -v desde="$desde" -v hasta="$hasta" '
    $0 == job { dentro = 1; print; next }
    dentro && /^  [A-Za-z_-]+:/ { dentro = 0 }
    {
      linea = $0
      if (dentro) {
        while ((i = index(linea, desde)) > 0) {
          linea = substr(linea, 1, i - 1) hasta substr(linea, i + length(desde))
          cambios++
          if (index(hasta, desde) > 0) break
        }
      }
      print linea
    }
    END { if (!cambios) exit 3 }' > "$destino" || { echo "ERROR del arnés: no se encontró «$desde» en $job" >&2; exit 2; }
  cp "$destino" "$copia"
}

# Agrega el texto $3 (líneas completas) justo después de «    steps:» del trabajo $2 de la copia $1.
agrega_paso() {
  local copia=$1 job=$2 texto=$3 destino="$tmp/mutado.yml"
  leer "$copia" | awk -v job="  $job:" -v texto="$texto" '
    $0 == job { dentro = 1 }
    dentro && /^    steps:$/ { print; print texto; dentro = 0; hecho = 1; next }
    { print }
    END { if (!hecho) exit 3 }' > "$destino" || { echo "ERROR del arnés: no hay «steps:» en $job" >&2; exit 2; }
  cp "$destino" "$copia"
}

# El workflow real cumple todas las reglas.
salida=$(revisar "$workflow")
if [ -z "$salida" ]; then
  ok "ui-release.yml cumple las reglas de publicar y etiquetar (002-08, 002-07)"
else
  falla "ui-release.yml cumple las reglas de publicar y etiquetar (002-08, 002-07)" "$salida"
fi

# Cada defecto, aplicado a una copia, tiene que ser rechazado por su regla.
# $1: nombre; $2: fragmento que debe aparecer entre los reportes; el resto: la mutación (función y argumentos).
rechaza() {
  local nombre=$1 esperado=$2
  shift 2
  local copia="$tmp/copia.yml"
  leer "$workflow" > "$copia"
  "$@" "$copia" || exit 2
  local salida
  salida=$(revisar "$copia")
  if grep -qF -- "$esperado" <<<"$salida"; then
    ok "rechaza: $nombre"
  else
    falla "rechaza: $nombre" "esperaba «$esperado» y revisar() dijo: ${salida:-nada}"
  fi
}

# Las mutaciones reciben la copia como último argumento (rechaza() la agrega).
reemplaza_en() { muta "${@: -1}" "$1" "$2" "$3"; }
agrega_paso_en() { agrega_paso "${@: -1}" "$1" "$2"; }

rechaza "publicar con checkout" "publicar: hace checkout" \
  agrega_paso_en publicar '      - uses: actions/checkout@0000000000000000000000000000000000000000'
rechaza "etiquetar con checkout" "etiquetar: hace checkout" \
  agrega_paso_en etiquetar '      - uses: actions/checkout@0000000000000000000000000000000000000000'
rechaza "publicar con npm ci" "publicar: instala dependencias del proyecto" \
  agrega_paso_en publicar '      - run: npm ci'
rechaza "etiquetar con npm install" "etiquetar: instala dependencias del proyecto" \
  agrega_paso_en etiquetar '      - run: npm install left-pad'
rechaza "publicar con caché de setup-node" "publicar: algún setup-node no desactiva la caché" \
  reemplaza_en publicar 'package-manager-cache: false' 'package-manager-cache: true'
rechaza "publicar con cache: npm" "publicar: usa caché" \
  agrega_paso_en publicar $'      - uses: actions/setup-node@0\n        with:\n          cache: npm'
rechaza "publicar sin --ignore-scripts" "publicar: falta --ignore-scripts" \
  reemplaza_en publicar '--ignore-scripts' '--foreground-scripts'
rechaza "publicar con --dry-run" "publicar: aún tiene --dry-run" \
  reemplaza_en publicar '"$NOMBRE" "$VERSION"' '"$NOMBRE" "$VERSION" --dry-run'
rechaza "publicar baja otro artefacto" "publicar: descarga un artefacto que no es release-scripts ni paquete" \
  reemplaza_en publicar 'name: paquete' 'name: dependencias'
rechaza "publicar ejecuta un script de otra carpeta" "publicar: ejecuta un script que no es del artefacto de decidir" \
  reemplaza_en publicar 'node "$RUNNER_TEMP/scripts/release-publish.mjs"' 'node "$RUNNER_TEMP/paquete/release-publish.mjs"'
rechaza "publicar sin comprobar la huella" "publicar: no comprueba la huella" \
  reemplaza_en publicar 'sha256sum --check --strict' 'sha256sum --check'
rechaza "publicar sin la huella de decidir" "publicar: no usa la huella de decidir" \
  reemplaza_en publicar 'huella_publicar' 'huella_otra'
rechaza "publicar toma algo de verificar que no es el SHA-256" "publicar: toma de verificar algo que no es el SHA-256" \
  reemplaza_en publicar 'HUELLA: ${{ needs.decidir.outputs.huella_publicar }}' 'HUELLA: ${{ needs.verificar.outputs.huella }}'
rechaza "publicar interpola una expresión en el cuerpo de un run" "publicar: interpola una expresión" \
  agrega_paso_en publicar '      - run: echo ${{ needs.decidir.outputs.version }}'
rechaza "publicar con || true en la comprobación de la huella" "publicar: la comprobación de sha256sum puede quedar anulada" \
  reemplaza_en publicar 'release-publish.mjs | sha256sum --check --strict' 'release-publish.mjs | sha256sum --check --strict || true'
rechaza "etiquetar con || true en la comprobación de las huellas" "etiquetar: la comprobación de sha256sum puede quedar anulada" \
  reemplaza_en etiquetar 'notas.md | sha256sum --check --strict' 'notas.md | sha256sum --check --strict || true'
rechaza "publicar comprueba la huella de su propio archivo" "publicar: la huella comprobada no es la que entregó decidir" \
  reemplaza_en publicar '"$HUELLA" release-publish.mjs |' '"$(sha256sum release-publish.mjs)" release-publish.mjs |'
rechaza "publicar sin exigir la procedencia" "publicar: no exige la procedencia" \
  reemplaza_en publicar "NPM_CONFIG_PROVENANCE: 'true'" "NPM_CONFIG_PROVENANCE: 'false'"
rechaza "publicar sin comprobar el SHA-256 en el mismo paso" "publicar: no comprueba el SHA-256 del .tgz en el mismo paso" \
  reemplaza_en publicar '"$SHA256" "$NOMBRE-$VERSION.tgz"' '"$SHA256" "otro.tgz"'
rechaza "verificar con permisos write-all" "verificar: permisos demasiado amplios" \
  reemplaza_en verificar 'contents: read' 'write-all'
rechaza "etiquetar con el token en el env del trabajo" "etiquetar: tiene env a nivel del trabajo" \
  reemplaza_en etiquetar '    permissions:' '    env:\n      TOKEN: x\n    permissions:'
rechaza "etiquetar con setup-node" "etiquetar: usa setup-node" \
  agrega_paso_en etiquetar '      - uses: actions/setup-node@0\n        with:\n          package-manager-cache: false'
rechaza "etiquetar interpola en un run con name" "etiquetar: interpola una expresión" \
  agrega_paso_en etiquetar '      - name: x\n        run: echo ${{ needs.decidir.outputs.version }}'
rechaza "decidir interpola en un run" "decidir: interpola una expresión" \
  agrega_paso_en decidir '      - name: x\n        run: echo ${{ steps.decide.outputs.version }}'
rechaza "etiquetar ejecuta release-tag.mjs por una ruta relativa" "etiquetar: ejecuta un script que no es del artefacto de decidir" \
  reemplaza_en etiquetar 'node "$RUNNER_TEMP/scripts/release-tag.mjs"' 'node release-tag.mjs'
rechaza "etiquetar cambia && por || en el if" "etiquetar: su condición «if» no es la del diseño" \
  reemplaza_en etiquetar "(needs.decidir.outputs.salida == 'publicar' && needs.publicar.result == 'success')" "(needs.decidir.outputs.salida == 'publicar' || needs.publicar.result == 'success')"
rechaza "etiquetar sin exigir decidir en verde" "etiquetar: no exige que decidir haya terminado bien" \
  reemplaza_en etiquetar " && needs.decidir.result == 'success'" ''
rechaza "etiquetar con github.token en otro paso" "etiquetar: github.token o un secreto fuera del paso" \
  agrega_paso_en etiquetar '      - run: echo ok\n        env:\n          X: ${{ github.token }}'
rechaza "publicar con set +e antes de comprobar" "publicar: puede seguir tras un fallo" \
  reemplaza_en publicar 'cd "$RUNNER_TEMP/scripts"' 'set +e; cd "$RUNNER_TEMP/scripts"'
rechaza "publicar con continue-on-error" "publicar: puede seguir tras un fallo" \
  reemplaza_en publicar '    timeout-minutes: 15' '    timeout-minutes: 15\n    continue-on-error: true'
rechaza "etiquetar con otra shell" "etiquetar: cambia la shell de bash" \
  agrega_paso_en etiquetar '      - name: x\n        shell: bash {0}\n        run: echo ok'
rechaza "etiquetar con un PAT" "el workflow usa secrets." \
  reemplaza_en etiquetar 'GH_TOKEN: ${{ github.token }}' 'GH_TOKEN: ${{ secrets.RELEASE_PAT }}'
rechaza "publicar ejecuta un script con .." "publicar: ejecuta un script que no es del artefacto de decidir" \
  agrega_paso_en publicar '      - run: |\n          node "$RUNNER_TEMP/scripts/../paquete/x.mjs"'
rechaza "etiquetar no pasa GITHUB_SHA como commit" "etiquetar: no pasa GITHUB_SHA a release-tag.mjs como commit" \
  reemplaza_en etiquetar '"$VERSION" "$GITHUB_SHA"' '"$VERSION" "$COMMIT"'
rechaza "etiquetar sin comparar el commit decidido" "etiquetar: no comprueba que el commit decidido sea el de esta ejecución" \
  reemplaza_en etiquetar '[ "$COMMIT" != "$GITHUB_SHA" ]' '[ "$COMMIT" != "$COMMIT" ]'
rechaza "publicar con la procedencia definida otra vez" "publicar: la procedencia se define más de una vez" \
  reemplaza_en publicar "NPM_CONFIG_PROVENANCE: 'true'" "NPM_CONFIG_PROVENANCE: 'true'\n          npm_config_provenance: x"
rechaza "publicar publica otro .tgz" "publicar: no publica el .tgz que se comprobó" \
  reemplaza_en publicar '"$RUNNER_TEMP/paquete/$NOMBRE-$VERSION.tgz" "$NOMBRE"' '"$RUNNER_TEMP/paquete/otro.tgz" "$NOMBRE"'
rechaza "publicar con contents: write" "publicar: tiene contents: write" \
  reemplaza_en publicar 'contents: read' 'contents: write'
rechaza "publicar sin el entorno npm" "publicar: no está en el entorno npm" \
  reemplaza_en publicar 'environment: npm' 'environment: otro'
rechaza "publicar sin exigir la rama main" "publicar: su condición «if» no exige" \
  reemplaza_en publicar "github.ref == 'refs/heads/main' && " ''
rechaza "etiquetar sin exigir que publicar termine bien" "etiquetar: corre sin exigir que publicar haya terminado bien" \
  reemplaza_en etiquetar "needs.publicar.result == 'success'" "needs.publicar.result != 'cancelled'"
rechaza "etiquetar con always()" "etiquetar: usa always()" \
  reemplaza_en etiquetar '!cancelled() && ' 'always() && '
rechaza "etiquetar sin !cancelled()" "etiquetar: sin !cancelled()" \
  reemplaza_en etiquetar '!cancelled() && ' ''
rechaza "etiquetar con id-token" "etiquetar: tiene id-token" \
  reemplaza_en etiquetar 'contents: write' $'contents: write\n      id-token: write'
rechaza "etiquetar usa algo de verificar" "etiquetar: usa algo de verificar" \
  reemplaza_en etiquetar 'HUELLA_NOTAS: ${{ needs.decidir.outputs.huella_notas }}' 'HUELLA_NOTAS: ${{ needs.verificar.outputs.sha256 }}'
rechaza "etiquetar descarga el paquete de verificar" "etiquetar: debe descargar solo el artefacto release-scripts" \
  reemplaza_en etiquetar 'name: release-scripts' 'name: paquete'
rechaza "etiquetar sin comprobar las huellas" "etiquetar: no comprueba las huellas" \
  reemplaza_en etiquetar 'sha256sum --check --strict' 'sha256sum --check'
rechaza "etiquetar no etiqueta el commit de esta ejecución" "etiquetar: no etiqueta el commit de esta ejecución" \
  reemplaza_en etiquetar 'GITHUB_SHA' 'COMMIT_OTRO'
rechaza "etiquetar con el token en otro paso" "etiquetar: el token debe aparecer en un solo lugar" \
  agrega_paso_en etiquetar $'      - run: echo ok\n        env:\n          GH_TOKEN: x'
rechaza "decidir instala dependencias" "decidir: instala dependencias" \
  agrega_paso_en decidir '      - run: npm ci'
rechaza "otro trabajo con id-token" "id-token: write debe aparecer solo en publicar" \
  reemplaza_en verificar 'contents: read' $'contents: read\n      id-token: write'
rechaza "verificar sin exigir la rama main" "verificar: su condición «if» no exige" \
  reemplaza_en verificar "github.ref == 'refs/heads/main' && " ''

# ---------------------------------------------------------------------------------------------------------------
echo
if [ "$fallos" -ne 0 ]; then
  echo "$fallos de $total pruebas fallaron."
  exit 1
fi
echo "$total pruebas en verde."
