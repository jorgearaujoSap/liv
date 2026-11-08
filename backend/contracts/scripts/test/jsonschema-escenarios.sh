# Escenarios de jsonschema.sh (feature 004, paso 2; ADR 0032): la inmutabilidad de los esquemas (check) y su
# publicación como releases de GitHub (publish). run.sh los carga con `.` y comparte sus funciones (correr,
# verificar, valor, sha_de, caso_almacen, sin_crear, sin_publicar) y su contador fallas. Nunca se llama a GitHub:
# publish corre con el gh falso (fake-gh.sh), sobre repositorios temporales.
#
# check  cada escenario parte de un repositorio con un commit (la base: el esquema config/1.0.json y sus casos) y
#        cambia el árbol de trabajo o hace otro commit; después juzga el árbol contra la referencia dada;
# publish cada escenario tiene un repositorio con un commit (GITHUB_SHA) y un almacén vacío del gh falso.

echo "Esquemas JSON, check:"
# Repositorio temporal con el proyecto en una subcarpeta, como backend/ en el repositorio real. autocrlf apagado
# para que el commit guarde LF y la prueba de CRLF sea real. El script y las pruebas son archivos de relleno:
# check solo comprueba que existan.
esq_repo() { # esq_repo NOMBRE
  repo="$trabajo/esquemas/$1"
  proyecto="$repo/proyecto"
  esquemas="$proyecto/contracts/jsonschema/config"
  mkdir -p "$esquemas" "$proyecto/contracts/openapi" "$proyecto/contracts/scripts/test"
  git -C "$repo" init -q
  git -C "$repo" config core.autocrlf false
  git -C "$repo" config user.name prueba
  git -C "$repo" config user.email prueba@example.invalid
  printf '{"esquema":"1.0"}\n' > "$esquemas/1.0.json"
  printf '{"casos":[]}\n' > "$esquemas/1.0.casos.json"
  tr -d '\r' < "$pruebas/muestra.yaml" > "$proyecto/contracts/openapi/muestra.yaml"
  for relleno in jsonschema.sh test/run.sh test/jsonschema-escenarios.sh; do
    echo '# relleno' > "$proyecto/contracts/scripts/$relleno"
  done
  esq_confirmar base
}
esq_confirmar() { # esq_confirmar MENSAJE
  git -C "$repo" add -A
  git -C "$repo" commit -q --allow-empty -m "$1"
}
esq_check() { # esq_check NOMBRE ESPERADO MOTIVO SALIDA [REFERENCIA]
  resultado=$(correr "$repo.txt" env LIV_CONTRACTS_ROOT="$proyecto" LIV_CONTRACTS_WORK="$repo.trabajo" \
    sh "$backend/contracts/scripts/jsonschema.sh" check "${5:-HEAD}")
  verificar "check/$1" "$resultado" "$2" "$3" "$4" "$repo.txt"
}

esq_repo sin-cambios
esq_check sin-cambios pasa '' 'config/1.0.json'

esq_repo version-nueva
printf '{"esquema":"1.1"}\n' > "$esquemas/1.1.json"
printf '{"casos":[]}\n' > "$esquemas/1.1.casos.json"
esq_check version-nueva pasa '' 'config/1.0.json'

esq_repo esquema-nuevo-de-otro-nombre
mkdir -p "$proyecto/contracts/jsonschema/otro-esquema"
printf '{}\n' > "$proyecto/contracts/jsonschema/otro-esquema/2.3.json"
esq_check esquema-nuevo-de-otro-nombre pasa '' 'config/1.0.json'

esq_repo esquema-cambiado
printf '{"esquema":"otro"}\n' > "$esquemas/1.0.json"
esq_check esquema-cambiado falla VERSION_CHANGED ''

esq_repo casos-cambiados
printf '{"casos":[1]}\n' > "$esquemas/1.0.casos.json"
esq_check casos-cambiados falla VERSION_CHANGED ''

# Un espacio de más al final también es otro contenido.
esq_repo cambio-minimo
printf '{"esquema":"1.0"} \n' > "$esquemas/1.0.json"
esq_check cambio-minimo falla VERSION_CHANGED ''

# Un checkout con autocrlf en Windows no cuenta como cambio.
esq_repo retornos-de-carro
sed 's/$/\r/' "$esquemas/1.0.json" > "$esquemas/crlf"
mv "$esquemas/crlf" "$esquemas/1.0.json"
esq_check retornos-de-carro pasa '' 'config/1.0.json'

esq_repo esquema-quitado
rm "$esquemas/1.0.json"
esq_check esquema-quitado falla VERSION_REMOVED ''

esq_repo casos-quitados
rm "$esquemas/1.0.casos.json"
esq_check casos-quitados falla VERSION_REMOVED ''

# Quitar todo no es una salida: la base tiene versiones, así que el script y las pruebas siguen siendo exigibles.
esq_repo carpeta-quitada
rm -r "$proyecto/contracts/jsonschema" "$proyecto/contracts/scripts/jsonschema.sh"
esq_check carpeta-quitada falla VERSION_REMOVED ''
faltas=$(grep -c 'SCRIPT_MISSING' "$repo.txt")
echo "Faltas de SCRIPT_MISSING: $faltas" > "$repo.cuenta.txt"
[ "$faltas" -gt 0 ] && estado=pasa || estado=falla
verificar check/carpeta-quitada-exige-el-script "$estado" pasa '' '' "$repo.cuenta.txt"

# Un cambio ya confirmado en un commit se juzga igual contra la referencia anterior.
esq_repo commit-que-cambia
printf '{"esquema":"otro"}\n' > "$esquemas/1.0.json"
esq_confirmar cambia
esq_check commit-que-cambia falla VERSION_CHANGED '' 'HEAD^1'

esq_repo commit-que-agrega
printf '{"esquema":"1.1"}\n' > "$esquemas/1.1.json"
printf '{"casos":[]}\n' > "$esquemas/1.1.casos.json"
esq_confirmar agrega
esq_check commit-que-agrega pasa '' 'config/1.0.json' 'HEAD^1'

# La forma de lo que va en contracts/jsonschema/: nombre/MAYOR.MENOR.json o nombre/MAYOR.MENOR.casos.json.
esq_repo archivo-suelto
echo notas > "$proyecto/contracts/jsonschema/NOTAS.md"
esq_check archivo-suelto falla UNEXPECTED_FILE ''

esq_repo archivo-suelto-con-forma-de-version
printf '{}\n' > "$proyecto/contracts/jsonschema/1.0.json"
esq_check archivo-suelto-con-forma-de-version falla UNEXPECTED_FILE ''

for nombre_malo in 1.json v1.0.json 1.0.0.json 01.0.json 1.0.yaml 2.0.JSON 1.0.casos.json.bak 1.0.json~ .1.0.json 1.0.casos.casos.json 1.0.1.casos.json; do
  esq_repo "nombre-$nombre_malo"
  printf '{}\n' > "$esquemas/$nombre_malo"
  esq_check "nombre-$nombre_malo" falla UNEXPECTED_FILE ''
done

for carpeta_mala in Otro config_2 2config .oculto; do
  esq_repo "carpeta-$carpeta_mala"
  mkdir -p "$proyecto/contracts/jsonschema/$carpeta_mala"
  printf '{}\n' > "$proyecto/contracts/jsonschema/$carpeta_mala/1.0.json"
  esq_check "carpeta-$carpeta_mala" falla UNEXPECTED_FILE ''
done

esq_repo subcarpeta
mkdir -p "$esquemas/viejo"
printf '{}\n' > "$esquemas/viejo/1.0.json"
esq_check subcarpeta falla UNEXPECTED_FILE ''

# Un enlace simbólico se lee distinto en el disco (su destino) que en Git (su ruta), así que no puede pasar.
# Windows sin permiso no los crea (MSYS=winsymlinks:nativestrict los pide nativos): ahí el escenario se omite a la
# vista, y corre en CI.
caso_enlace() { # caso_enlace NOMBRE DESTINO RUTA_DEL_ENLACE_EN_EL_PROYECTO
  esq_repo "$1"
  enlace="$proyecto/$3"
  if MSYS=winsymlinks:nativestrict ln -s "$2" "$enlace" 2> /dev/null && [ -L "$enlace" ]; then
    esq_check "$1" falla UNEXPECTED_FILE ''
  else
    printf '  omitida    check/%s: este sistema no crea enlaces simbólicos (corre en CI)
' "$1"
  fi
}
caso_enlace enlace-simbolico 1.0.json contracts/jsonschema/config/1.1.json
caso_enlace enlace-simbolico-roto no-existe contracts/jsonschema/config/1.1.json
caso_enlace enlace-simbolico-a-carpeta config contracts/jsonschema/otro

# Un nombre con salto de línea partiría las listas del script: se rechaza antes. Windows no admite ese nombre.
esq_repo nombre-con-salto-de-linea
if printf '{}
' > "$esquemas/1.1
.json" 2> /dev/null; then
  esq_check nombre-con-salto-de-linea falla UNEXPECTED_FILE ''
else
  printf '  omitida    check/nombre-con-salto-de-linea: este sistema no admite saltos de línea en un nombre (corre en CI)
'
fi

# El nombre nombra la release: no puede repetirse con un contrato OpenAPI.
esq_repo nombre-repetido-con-openapi
cp "$pruebas/muestra.yaml" "$proyecto/contracts/openapi/config.yaml"
esq_check nombre-repetido-con-openapi falla NAME_CLASH ''

esq_repo nombre-repetido-con-openapi-nuevo
mkdir -p "$proyecto/contracts/jsonschema/muestra"
printf '{}\n' > "$proyecto/contracts/jsonschema/muestra/1.0.json"
esq_check nombre-repetido-con-openapi-nuevo falla NAME_CLASH ''

# Si el árbol del PR ya no tiene el script o sus pruebas, falla: nunca pasa por omisión.
esq_repo script-quitado
rm "$proyecto/contracts/scripts/jsonschema.sh"
esq_check script-quitado falla SCRIPT_MISSING ''

esq_repo escenarios-quitados
rm "$proyecto/contracts/scripts/test/jsonschema-escenarios.sh"
esq_check escenarios-quitados falla SCRIPT_MISSING ''

esq_repo run-quitado
rm "$proyecto/contracts/scripts/test/run.sh"
esq_check run-quitado falla SCRIPT_MISSING ''

# Sin carpeta de esquemas en la base ni en el PR no hay nada que proteger, y el script puede no estar.
esq_repo sin-carpeta-en-ninguno
rm -r "$proyecto/contracts/jsonschema" "$proyecto/contracts/scripts/jsonschema.sh"
esq_confirmar sin-esquemas
esq_check sin-carpeta-en-ninguno pasa '' 'nada que proteger'

# La base sin carpeta y el PR que la agrega: es la primera vez, sin nada que cambiar.
esq_repo el-pr-agrega-la-carpeta
rm -r "$proyecto/contracts/jsonschema"
esq_confirmar sin-esquemas
mkdir -p "$esquemas"
printf '{"esquema":"1.0"}\n' > "$esquemas/1.0.json"
printf '{"casos":[]}\n' > "$esquemas/1.0.casos.json"
esq_check el-pr-agrega-la-carpeta pasa '' 'nada que proteger'

# Lo que ya estaba mal en la base no bloquea al PR que lo quita; lo que sigue mal en el PR sí falla.
esq_repo suelto-en-la-base-quitado
echo notas > "$proyecto/contracts/jsonschema/NOTAS.md"
esq_confirmar suelto
rm "$proyecto/contracts/jsonschema/NOTAS.md"
esq_check suelto-en-la-base-quitado pasa '' 'config/1.0.json'

esq_repo suelto-en-la-base-queda
echo notas > "$proyecto/contracts/jsonschema/NOTAS.md"
esq_confirmar suelto
esq_check suelto-en-la-base-queda falla UNEXPECTED_FILE ''

esq_repo referencia-inexistente
esq_check referencia-inexistente falla GIT_ERROR '' no-existe

echo "Esquemas JSON, publish:"
esq_publicacion() { # esq_publicacion NOMBRE
  dir="$trabajo/esquemas-publicar/$1"
  repo="$dir/repo"
  almacen="$dir/almacen"
  esquemas="$repo/contracts/jsonschema/config"
  mkdir -p "$esquemas" "$almacen"
  : > "$almacen/llamadas"
  git -C "$repo" init -q
  git -C "$repo" config core.autocrlf false
  git -C "$repo" config user.name prueba
  git -C "$repo" config user.email prueba@example.invalid
  printf '{"esquema":"1.0"}\n' > "$esquemas/1.0.json"
  printf '{"casos":[]}\n' > "$esquemas/1.0.casos.json"
  esq_confirmar esquema
}
esq_publicar() { # esq_publicar NOMBRE ESPERADO MOTIVO SALIDA [ARGUMENTOS DE publish]
  nombre=$1
  esperado=$2
  motivo=$3
  salida=$4
  shift 4
  resultado=$(correr "$dir/salida.txt" env LIV_CONTRACTS_ROOT="$repo" LIV_CONTRACTS_WORK="$dir/trabajo" \
    LIV_GH="sh $pruebas/fake-gh.sh" FAKE_GH_STORE="$almacen" GH_REPO=liv/prueba \
    GITHUB_SHA="$(git -C "$repo" rev-parse HEAD)" sh "$backend/contracts/scripts/jsonschema.sh" publish "$@")
  verificar "publish/$nombre" "$resultado" "$esperado" "$motivo" "$salida" "$dir/salida.txt"
}
# Contenido de una versión del esquema config en el commit (SUFIJO: vacío para el esquema, .casos para sus casos).
esq_en_el_commit() { # esq_en_el_commit VERSION SUFIJO
  (cd "$repo" && MSYS_NO_PATHCONV=1 git show "HEAD:contracts/jsonschema/config/$1$2.json")
}
# Siembra la release con los cuatro archivos de la versión que está en el commit.
esq_sembrar() { # esq_sembrar ETIQUETA ESTADO VERSION
  mkdir -p "$almacen/releases/$1/assets"
  echo "$2" > "$almacen/releases/$1/estado"
  for sufijo in '' .casos; do
    archivo="$almacen/releases/$1/assets/config-$3$sufijo.json"
    esq_en_el_commit "$3" "$sufijo" > "$archivo"
    printf '%s  config-%s%s.json\n' "$(sha_de "$archivo")" "$3" "$sufijo" > "$archivo.sha256"
  done
  if [ "$2" = published ]; then mkdir -p "$almacen/tags" && touch "$almacen/tags/$1"; fi
}
# La release está publicada y sus cuatro archivos son los del commit, cada .sha256 con su archivo.
esq_completa() { # esq_completa ETIQUETA VERSION
  [ "$(cat "$almacen/releases/$1/estado")" = published ] || return 1
  for sufijo in '' .casos; do
    esperado=$(esq_en_el_commit "$2" "$sufijo" | sha256sum | cut -d ' ' -f 1)
    asset="$almacen/releases/$1/assets/config-$2$sufijo.json"
    [ "$(sha256sum < "$asset" | cut -d ' ' -f 1)" = "$esperado" ] || return 1
    [ "$(cat "$asset.sha256")" = "$esperado  config-$2$sufijo.json" ] || return 1
  done
}
sin_llamadas() { [ ! -s "$almacen/llamadas" ]; }

etiqueta_1_0=jsonschema-config-v1.0
etiqueta_1_1=jsonschema-config-v1.1

esq_publicacion nueva
esq_publicar nueva pasa '' "publicada $etiqueta_1_0"
caso_almacen esquema-nueva-queda-completa esq_completa "$etiqueta_1_0" 1.0

# Se publica el contenido del commit, no el del árbol de trabajo, y la etiqueta lleva el nombre del esquema.
esq_publicacion arbol-con-cambios
printf '{"esquema":"sin confirmar"}\n' > "$esquemas/1.0.json"
esq_publicar arbol-con-cambios pasa '' "publicada $etiqueta_1_0"
caso_almacen esquema-arbol-con-cambios-publica-el-commit esq_completa "$etiqueta_1_0" 1.0

esq_publicacion dos-versiones
printf '{"esquema":"1.1"}\n' > "$esquemas/1.1.json"
printf '{"casos":[1]}\n' > "$esquemas/1.1.casos.json"
esq_confirmar v1.1
esq_publicar dos-versiones pasa '' "publicada $etiqueta_1_1"
caso_almacen esquema-dos-versiones-1.0 esq_completa "$etiqueta_1_0" 1.0
caso_almacen esquema-dos-versiones-1.1 esq_completa "$etiqueta_1_1" 1.1

esq_publicacion dos-esquemas
mkdir -p "$repo/contracts/jsonschema/otro"
printf '{}\n' > "$repo/contracts/jsonschema/otro/2.0.json"
printf '{"casos":[]}\n' > "$repo/contracts/jsonschema/otro/2.0.casos.json"
esq_confirmar otro
esq_publicar dos-esquemas pasa '' 'publicada jsonschema-otro-v2.0'
caso_almacen esquema-dos-esquemas-la-primera esq_completa "$etiqueta_1_0" 1.0

# Volver a correr: lo ya publicado con el mismo contenido no se toca.
esq_publicacion ya-publicada
esq_sembrar "$etiqueta_1_0" published 1.0
esq_publicar ya-publicada pasa '' "$etiqueta_1_0 ya está publicada"
caso_almacen esquema-ya-publicada-sin-crear sin_crear
caso_almacen esquema-ya-publicada-sin-publicar sin_publicar

# Si ya existe con otro contenido, falla: lo publicado no cambia, un cambio es una versión nueva.
esq_publicacion otro-contenido
esq_sembrar "$etiqueta_1_0" published 1.0
printf '{"esquema":"otro"}\n' > "$almacen/releases/$etiqueta_1_0/assets/config-1.0.json"
esq_publicar otro-contenido falla PUBLISHED_CHANGED ''

esq_publicacion casos-con-otro-contenido
esq_sembrar "$etiqueta_1_0" published 1.0
printf '{"casos":[9]}\n' > "$almacen/releases/$etiqueta_1_0/assets/config-1.0.casos.json"
esq_publicar casos-con-otro-contenido falla PUBLISHED_CHANGED ''

# El .sha256 publicado tiene que coincidir con el commit, no solo con su propio archivo.
esq_publicacion sha256-reemplazado
esq_sembrar "$etiqueta_1_0" published 1.0
printf '%s  config-1.0.json\n' "$(printf otro | sha256sum | cut -d ' ' -f 1)" > "$almacen/releases/$etiqueta_1_0/assets/config-1.0.json.sha256"
esq_publicar sha256-reemplazado falla PUBLISHED_CHANGED ''

esq_publicacion publicada-incompleta
esq_sembrar "$etiqueta_1_0" published 1.0
rm "$almacen/releases/$etiqueta_1_0/assets/config-1.0.casos.json"
esq_publicar publicada-incompleta falla PUBLISHED_CHANGED ''

# Un borrador que quedó de una corrida cortada se completa y se publica.
esq_publicacion borrador-a-medias
esq_sembrar "$etiqueta_1_0" draft 1.0
rm "$almacen/releases/$etiqueta_1_0/assets/config-1.0.casos.json" "$almacen/releases/$etiqueta_1_0/assets/config-1.0.casos.json.sha256"
esq_publicar borrador-a-medias pasa '' "publicada $etiqueta_1_0"
caso_almacen esquema-borrador-a-medias-queda-completo esq_completa "$etiqueta_1_0" 1.0

# El borrador con otro contenido se corrige con el del commit antes de publicarlo.
esq_publicacion borrador-con-otro-contenido
esq_sembrar "$etiqueta_1_0" draft 1.0
printf '{"esquema":"otro"}\n' > "$almacen/releases/$etiqueta_1_0/assets/config-1.0.json"
esq_publicar borrador-con-otro-contenido pasa '' "publicada $etiqueta_1_0"
caso_almacen esquema-borrador-con-otro-contenido-queda-completo esq_completa "$etiqueta_1_0" 1.0

# Se verifica lo descargado, no lo subido.
esq_publicacion borrador-no-verifica
touch "$almacen/download-altera"
esq_publicar borrador-no-verifica falla PUBLISH_ERROR ''
caso_almacen esquema-borrador-no-verifica-sin-publicar sin_publicar

esq_publicacion subida-falla
esq_sembrar "$etiqueta_1_0" draft 1.0
touch "$almacen/upload-falla"
esq_publicar subida-falla falla PUBLISH_ERROR ''
caso_almacen esquema-subida-falla-sin-publicar sin_publicar

esq_publicacion etiqueta-sin-release
mkdir -p "$almacen/tags" && touch "$almacen/tags/$etiqueta_1_0"
esq_publicar etiqueta-sin-release falla PUBLISH_ERROR ''
caso_almacen esquema-etiqueta-sin-release-sin-crear sin_crear

esq_publicacion error-de-red
touch "$almacen/view-falla"
esq_publicar error-de-red falla PUBLISH_ERROR ''
caso_almacen esquema-error-de-red-sin-crear sin_crear

# Un error al consultar la etiqueta no se toma como «no existe».
esq_publicacion error-api
touch "$almacen/api-falla"
esq_publicar error-api falla PUBLISH_ERROR ''
caso_almacen esquema-error-api-sin-crear sin_crear

# Cada versión necesita sus dos archivos: sin los casos o sin el esquema no se publica nada.
esq_publicacion sin-casos
# git rm quita el archivo del disco y del índice: el commit ya no lo tiene.
git -C "$repo" rm -q contracts/jsonschema/config/1.0.casos.json
esq_confirmar sin-casos
esq_publicar sin-casos falla INCOMPLETE_VERSION ''
caso_almacen esquema-sin-casos-sin-crear sin_crear

esq_publicacion casos-sin-esquema
git -C "$repo" rm -q contracts/jsonschema/config/1.0.json
esq_confirmar casos-sin-esquema
esq_publicar casos-sin-esquema falla INCOMPLETE_VERSION ''
caso_almacen esquema-casos-sin-esquema-sin-crear sin_crear

esq_publicacion fuera-de-forma
printf '{}\n' > "$esquemas/v1.1.json"
esq_confirmar fuera-de-forma
esq_publicar fuera-de-forma falla UNEXPECTED_FILE ''
caso_almacen esquema-fuera-de-forma-sin-crear sin_crear

# Un enlace simbólico en el commit (modo 120000) se rechaza; se crea con el índice, sin tocar el disco.
esq_publicacion enlace-simbolico
blob_del_enlace=$(printf '1.0.json' | git -C "$repo" hash-object -w --stdin)
git -C "$repo" update-index --add --cacheinfo "120000,$blob_del_enlace,contracts/jsonschema/config/1.1.json"
git -C "$repo" commit -q -m enlace
esq_publicar enlace-simbolico falla UNEXPECTED_FILE ''
caso_almacen esquema-enlace-simbolico-sin-crear sin_crear

esq_publicacion sin-esquemas
git -C "$repo" rm -q -r contracts/jsonschema
esq_confirmar sin-esquemas
esq_publicar sin-esquemas falla NO_SCHEMAS ''

# --dry-run solo dice qué se publicaría, sin llamar a GitHub y sin necesidad de GH_REPO ni GITHUB_SHA.
esq_publicacion simulacion
resultado=$(correr "$dir/salida.txt" env LIV_CONTRACTS_ROOT="$repo" LIV_CONTRACTS_WORK="$dir/trabajo" \
  LIV_GH="sh $pruebas/fake-gh.sh" FAKE_GH_STORE="$almacen" sh "$backend/contracts/scripts/jsonschema.sh" publish --dry-run)
verificar publish/simulacion "$resultado" pasa '' "jsonschema-config-v1.0" "$dir/salida.txt"
caso_almacen esquema-simulacion-sin-llamar-a-gh sin_llamadas

esq_publicacion sin-repositorio
resultado=$(correr "$dir/salida.txt" env -u GH_REPO -u GITHUB_SHA LIV_CONTRACTS_ROOT="$repo" LIV_CONTRACTS_WORK="$dir/trabajo" \
  LIV_GH="sh $pruebas/fake-gh.sh" FAKE_GH_STORE="$almacen" sh "$backend/contracts/scripts/jsonschema.sh" publish)
verificar publish/sin-repositorio "$resultado" falla '' 'Faltan GH_REPO o GITHUB_SHA' "$dir/salida.txt"
caso_almacen esquema-sin-repositorio-sin-llamar-a-gh sin_llamadas

echo "Esquemas JSON, uso:"
esq_publicacion uso
for orden in '' desconocida 'check' 'publish --otra' 'check HEAD sobrante'; do
  # shellcheck disable=SC2086
  resultado=$(correr "$dir/salida.txt" env LIV_CONTRACTS_ROOT="$repo" LIV_CONTRACTS_WORK="$dir/trabajo" \
    sh "$backend/contracts/scripts/jsonschema.sh" $orden)
  verificar "uso/${orden:-sin-orden}" "$resultado" falla '' 'Uso:' "$dir/salida.txt"
done
