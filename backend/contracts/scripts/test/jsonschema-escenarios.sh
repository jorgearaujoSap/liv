# Escenarios de jsonschema.sh (feature 004, paso 2; ADR 0032): la inmutabilidad de los esquemas (check) y su
# publicación como releases de GitHub (publish). run.sh los carga con `.` y comparte sus funciones (correr,
# verificar, sha_de, caso_almacen, sin_crear, sin_publicar) y su contador fallas. El sha_de de run.sh quita los
# retornos de carro antes de calcular; los archivos de estos escenarios no los tienen, así que da lo mismo que el
# de jsonschema.sh. Nunca se llama a GitHub: publish
# corre con el gh falso (fake-gh.sh), sobre repositorios temporales.
#
# check, como publish, juzga lo que está guardado en Git, así que cada escenario confirma sus cambios:
#   - lo que se puede escribir en el disco (editar o quitar un archivo) se confirma con esq_confirmar;
#   - lo que Windows no deja crear en el disco (enlaces simbólicos, submódulos, nombres con un salto de línea) se
#     pone directo en el índice con esq_en_indice y se confirma con esq_confirmar_indice, y corre igual en
#     Windows y en Linux.
# check parte de un repositorio con un commit (la base, con la etiqueta «base») y juzga el último commit contra ella;
# publish parte de un repositorio con un commit (GITHUB_SHA) y un almacén vacío del gh falso.

echo "Esquemas JSON, check:"
# Repositorio temporal con el proyecto en una subcarpeta, como backend/ en el repositorio real. autocrlf apagado
# para que el commit guarde lo escrito. El script, lib.sh y las pruebas son archivos de relleno: check solo
# comprueba que existan.
esq_repo() { # esq_repo NOMBRE
  repo="$trabajo/esquemas/$1"
  proyecto="$repo/proyecto"
  prefijo_del_proyecto=proyecto/
  esquemas="$proyecto/contracts/jsonschema/config"
  mkdir -p "$esquemas" "$proyecto/contracts/openapi" "$proyecto/contracts/scripts/test"
  git -C "$repo" init -q
  git -C "$repo" config core.autocrlf false
  git -C "$repo" config user.name prueba
  git -C "$repo" config user.email prueba@example.invalid
  printf '{"esquema":"1.0"}\n' > "$esquemas/1.0.json"
  printf '{"casos":[]}\n' > "$esquemas/1.0.casos.json"
  tr -d '\r' < "$pruebas/muestra.yaml" > "$proyecto/contracts/openapi/muestra.yaml"
  for relleno in jsonschema.sh lib.sh test/run.sh test/fake-gh.sh test/jsonschema-escenarios.sh; do
    echo '# relleno' > "$proyecto/contracts/scripts/$relleno"
  done
  # Git en Windows rechaza nombres con caracteres de control o con un espacio al final; en el índice de estos
  # repositorios temporales sí deben poder estar.
  git -C "$repo" config core.protectNTFS false
  esq_confirmar base
  git -C "$repo" tag base
}
esq_confirmar() { # esq_confirmar MENSAJE: confirma lo que hay en el disco
  git -C "$repo" add -A
  git -C "$repo" commit -q --allow-empty -m "$1"
}
esq_confirmar_indice() { # esq_confirmar_indice MENSAJE: confirma el índice tal como está
  git -C "$repo" commit -q --allow-empty -m "$1"
}
# Pone una entrada en el índice sin tocar el disco: MODO 100644 (archivo), 120000 (enlace simbólico) o 160000
# (submódulo). RUTA es relativa al proyecto; en un enlace, CONTENIDO es su destino.
esq_en_indice() { # esq_en_indice MODO RUTA CONTENIDO
  objeto=$(printf '%s\n' "$3" | git -C "$repo" hash-object -w --stdin)
  if [ "$1" = 160000 ]; then objeto=0123456789012345678901234567890123456789; fi
  git -C "$repo" update-index --add --cacheinfo "$1,$objeto,$prefijo_del_proyecto$2"
}
esq_nueva_base() { # esq_nueva_base MENSAJE: lo que hay en el índice pasa a ser la base
  esq_confirmar_indice "$1"
  git -C "$repo" tag -f base > /dev/null
}
# Juzga el último commit contra la base. SALIDA es un texto que debe aparecer en lo que escribe el script.
esq_check() { # esq_check NOMBRE ESPERADO MOTIVO SALIDA
  resultado=$(correr "$repo.txt" env LIV_CONTRACTS_ROOT="$proyecto" LIV_CONTRACTS_WORK="$repo.trabajo" \
    sh "$backend/contracts/scripts/jsonschema.sh" check base)
  verificar "check/$1" "$resultado" "$2" "$3" "$4" "$repo.txt"
}
esq_caso() { # esq_caso NOMBRE ESPERADO MOTIVO SALIDA: confirma lo del disco y juzga
  esq_confirmar cambio
  esq_check "$@"
}
esq_agregar_version() { # esq_agregar_version CARPETA VERSION: esquema y casos
  mkdir -p "$proyecto/contracts/jsonschema/$1"
  printf '{"esquema":"%s"}\n' "$2" > "$proyecto/contracts/jsonschema/$1/$2.json"
  printf '{"casos":[]}\n' > "$proyecto/contracts/jsonschema/$1/$2.casos.json"
}

esq_repo sin-cambios
esq_caso sin-cambios pasa '' 'config/1.0.json'

esq_repo version-nueva
esq_agregar_version config 1.1
esq_caso version-nueva pasa '' 'config/1.0.json'

esq_repo esquema-nuevo-de-otro-nombre
esq_agregar_version otro-esquema 2.3
esq_caso esquema-nuevo-de-otro-nombre pasa '' 'config/1.0.json'

# La parte decimal del nombre es un número, no un orden: 1.10 y 1.9 son versiones distintas.
esq_repo versiones-1-9-y-1-10
esq_agregar_version config 1.9
esq_agregar_version config 1.10
esq_caso versiones-1-9-y-1-10 pasa '' 'config/1.0.json'

esq_repo esquema-cambiado
printf '{"esquema":"otro"}\n' > "$esquemas/1.0.json"
esq_caso esquema-cambiado falla VERSION_CHANGED ''

esq_repo casos-cambiados
printf '{"casos":[1]}\n' > "$esquemas/1.0.casos.json"
esq_caso casos-cambiados falla VERSION_CHANGED ''

# Un espacio de más al final también es otro contenido.
esq_repo cambio-minimo
printf '{"esquema":"1.0"} \n' > "$esquemas/1.0.json"
esq_caso cambio-minimo falla VERSION_CHANGED ''

# Lo que se publica es el blob de Git, así que cambiar los finales de línea es cambiar la versión.
esq_repo finales-de-linea-cambiados
sed 's/$/\r/' "$esquemas/1.0.json" > "$esquemas/crlf"
mv "$esquemas/crlf" "$esquemas/1.0.json"
esq_caso finales-de-linea-cambiados falla VERSION_CHANGED ''

# Un cambio que se revierte en un segundo commit deja HEAD igual que la base.
esq_repo cambio-revertido
printf '{"esquema":"otro"}\n' > "$esquemas/1.0.json"
esq_confirmar cambia
printf '{"esquema":"1.0"}\n' > "$esquemas/1.0.json"
esq_caso cambio-revertido pasa '' 'config/1.0.json'

# check juzga el commit: un cambio confirmado cuenta aunque el disco vuelva a ser el de la base.
esq_repo cambio-confirmado-disco-restaurado
printf '{"esquema":"otro"}\n' > "$esquemas/1.0.json"
esq_confirmar cambia
printf '{"esquema":"1.0"}\n' > "$esquemas/1.0.json"
esq_check cambio-confirmado-disco-restaurado falla VERSION_CHANGED ''

esq_repo esquema-quitado
rm "$esquemas/1.0.json"
esq_caso esquema-quitado falla VERSION_REMOVED ''

esq_repo casos-quitados
rm "$esquemas/1.0.casos.json"
esq_caso casos-quitados falla VERSION_REMOVED ''

# Quitar todo no es una salida: la base tiene versiones, así que el script y las pruebas siguen siendo exigibles.
# Además de VERSION_REMOVED, el mismo juicio tiene que reportar SCRIPT_MISSING (se cuenta en lo que escribió).
esq_repo carpeta-quitada
rm -r "$proyecto/contracts/jsonschema" "$proyecto/contracts/scripts/jsonschema.sh"
esq_caso carpeta-quitada falla VERSION_REMOVED ''
faltas=$(grep -c 'SCRIPT_MISSING' "$repo.txt")
echo "Faltas de SCRIPT_MISSING: $faltas" > "$repo.cuenta.txt"
[ "$faltas" -gt 0 ] && estado=pasa || estado=falla
verificar check/carpeta-quitada-exige-el-script "$estado" pasa '' '' "$repo.cuenta.txt"

# Cada versión lleva sus dos archivos: sin uno, publish no la puede publicar.
esq_repo version-sin-casos
printf '{}\n' > "$proyecto/contracts/jsonschema/config/1.1.json"
esq_caso version-sin-casos falla INCOMPLETE_VERSION ''

esq_repo casos-sin-esquema
printf '{}\n' > "$proyecto/contracts/jsonschema/config/1.1.casos.json"
esq_caso casos-sin-esquema falla INCOMPLETE_VERSION ''

# La forma de lo que va en contracts/jsonschema/: nombre/MAYOR.MENOR.json o nombre/MAYOR.MENOR.casos.json.
esq_repo archivo-suelto
echo notas > "$proyecto/contracts/jsonschema/NOTAS.md"
esq_caso archivo-suelto falla UNEXPECTED_FILE ''

esq_repo archivo-suelto-con-forma-de-version
printf '{}\n' > "$proyecto/contracts/jsonschema/1.0.json"
esq_caso archivo-suelto-con-forma-de-version falla UNEXPECTED_FILE ''

for nombre_malo in 1.json v1.0.json 1.0.0.json 01.0.json 1.0.yaml 2.0.JSON 1.0.casos.json.bak 1.0.json~ .1.0.json 1.0.casos.casos.json 1.0.1.casos.json; do
  esq_repo "nombre-$nombre_malo"
  printf '{}\n' > "$esquemas/$nombre_malo"
  esq_caso "nombre-$nombre_malo" falla UNEXPECTED_FILE ''
done

for carpeta_mala in Otro config_2 2config .oculto; do
  esq_repo "carpeta-$carpeta_mala"
  mkdir -p "$proyecto/contracts/jsonschema/$carpeta_mala"
  printf '{}\n' > "$proyecto/contracts/jsonschema/$carpeta_mala/1.0.json"
  esq_caso "carpeta-$carpeta_mala" falla UNEXPECTED_FILE ''
done

esq_repo subcarpeta
mkdir -p "$esquemas/viejo"
printf '{}\n' > "$esquemas/viejo/1.0.json"
esq_caso subcarpeta falla UNEXPECTED_FILE ''

# Nombres con caracteres que Git entrecomilla o que partirían una lista: un espacio al final, un retorno de carro,
# un tabulador, un salto de línea, una letra fuera de ASCII y un retorno de carro seguido de un comando de Actions
# (::warning::). Ninguno tiene la forma, y ninguno se cuela. Varios no se pueden crear en el disco de Windows; en el
# índice sí.
n_raro=0
for caracter_raro in ' ' "$(printf '\r')" "$(printf '\t')" "$(printf '\nx')" é "$(printf '\r::warning::x')"; do
  n_raro=$((n_raro + 1))
  esq_repo "nombre-raro-$n_raro"
  esq_en_indice 100644 "contracts/jsonschema/config/1.1.json$caracter_raro" '{}'
  esq_confirmar_indice raro
  esq_check "nombre-raro-$n_raro" falla UNEXPECTED_FILE ''
done

# Enlaces simbólicos y submódulos: en el índice, sin necesidad de crearlos en el disco.
esq_repo enlace-simbolico
esq_en_indice 120000 contracts/jsonschema/config/1.1.json 1.0.json
esq_confirmar_indice enlace
esq_check enlace-simbolico falla UNEXPECTED_FILE ''

esq_repo enlace-simbolico-roto
esq_en_indice 120000 contracts/jsonschema/config/1.1.json no-existe
esq_confirmar_indice enlace
esq_check enlace-simbolico-roto falla UNEXPECTED_FILE ''

esq_repo enlace-simbolico-a-carpeta
esq_en_indice 120000 contracts/jsonschema/otro config
esq_confirmar_indice enlace
esq_check enlace-simbolico-a-carpeta falla UNEXPECTED_FILE ''

esq_repo submodulo
esq_en_indice 160000 contracts/jsonschema/otro ''
esq_confirmar_indice submodulo
esq_check submodulo falla UNEXPECTED_FILE ''

# contracts/jsonschema que deja de ser una carpeta: todo lo de la base se quita.
esq_repo raiz-es-un-enlace
git -C "$repo" rm -r -q --cached proyecto/contracts/jsonschema
esq_en_indice 120000 contracts/jsonschema config-jurisdiccion
esq_confirmar_indice enlace
esq_check raiz-es-un-enlace falla UNEXPECTED_FILE ''
esq_check raiz-es-un-enlace-quita-las-versiones falla VERSION_REMOVED ''

esq_repo raiz-es-un-archivo-sin-base
git -C "$repo" rm -r -q --cached proyecto/contracts/jsonschema
esq_nueva_base sin-esquemas
esq_en_indice 100644 contracts/jsonschema 'no es una carpeta'
esq_confirmar_indice archivo
esq_check raiz-es-un-archivo-sin-base falla UNEXPECTED_FILE ''

# El nombre nombra la release: no puede repetirse con un contrato OpenAPI.
esq_repo nombre-repetido-con-openapi
cp "$pruebas/muestra.yaml" "$proyecto/contracts/openapi/config.yaml"
esq_caso nombre-repetido-con-openapi falla NAME_CLASH ''

esq_repo nombre-repetido-con-openapi-nuevo
esq_agregar_version muestra 1.0
esq_caso nombre-repetido-con-openapi-nuevo falla NAME_CLASH ''

# Lo que decide es lo que hay guardado en Git: un contrato solo en el índice choca, y uno solo en el disco, no.
esq_repo contrato-solo-en-el-indice
esq_en_indice 100644 contracts/openapi/config.yaml 'openapi: 3.0.3'
esq_confirmar_indice contrato
esq_check contrato-solo-en-el-indice falla NAME_CLASH ''

esq_repo contrato-solo-en-el-disco
cp "$pruebas/muestra.yaml" "$proyecto/contracts/openapi/config.yaml"
esq_check contrato-solo-en-el-disco pasa '' 'config/1.0.json'

# Un choque que ya estaba en la base no bloquea a los demás PR: solo se avisa. El que lo introduce sí falla.
esq_repo nombre-repetido-ya-en-la-base
cp "$pruebas/muestra.yaml" "$proyecto/contracts/openapi/config.yaml"
esq_confirmar choque
git -C "$repo" tag -f base > /dev/null
esq_agregar_version config 1.1
esq_caso nombre-repetido-ya-en-la-base pasa '' 'ya compartía nombre'

# Si HEAD ya no tiene el script, sus dependencias o sus pruebas, falla: nunca pasa por omisión.
for guarda in jsonschema.sh lib.sh test/run.sh test/fake-gh.sh test/jsonschema-escenarios.sh; do
  esq_repo "guarda-$(basename "$guarda")-quitada"
  rm "$proyecto/contracts/scripts/$guarda"
  esq_caso "guarda-$(basename "$guarda")-quitada" falla SCRIPT_MISSING ''
done

# Un enlace simbólico en lugar de la guarda tampoco vale.
esq_repo guarda-es-un-enlace
esq_en_indice 120000 contracts/scripts/jsonschema.sh lib.sh
esq_confirmar_indice enlace
esq_check guarda-es-un-enlace falla SCRIPT_MISSING ''

# La base sin carpeta y un PR que la agrega sin el script: es justo lo que la guarda debe parar.
esq_repo el-pr-agrega-la-carpeta-sin-el-script
git -C "$repo" rm -r -q proyecto/contracts/jsonschema
esq_nueva_base sin-esquemas
esq_agregar_version config 1.0
rm "$proyecto/contracts/scripts/jsonschema.sh"
esq_caso el-pr-agrega-la-carpeta-sin-el-script falla SCRIPT_MISSING ''

# Sin carpeta de esquemas en la base ni en HEAD no hay nada que proteger, y el script puede no estar.
esq_repo sin-carpeta-en-ninguno
git -C "$repo" rm -r -q proyecto/contracts/jsonschema proyecto/contracts/scripts/jsonschema.sh
esq_nueva_base sin-esquemas
esq_caso sin-carpeta-en-ninguno pasa '' 'nada que proteger'

# La base sin carpeta y el PR que la agrega, con todo en su sitio: la primera vez, sin nada que cambiar.
esq_repo el-pr-agrega-la-carpeta
git -C "$repo" rm -r -q proyecto/contracts/jsonschema
esq_nueva_base sin-esquemas
esq_agregar_version config 1.0
esq_caso el-pr-agrega-la-carpeta pasa '' 'nada que proteger'

# Lo que ya estaba mal en la base (un archivo suelto y un nombre con un espacio al final) no bloquea al PR que lo
# quita; lo que sigue mal en HEAD sí falla.
esq_repo suelto-en-la-base-quitado
echo notas > "$proyecto/contracts/jsonschema/NOTAS.md"
esq_confirmar suelto
esq_en_indice 100644 'contracts/jsonschema/config/1.0.json ' '{}'
esq_nueva_base suelto-con-espacio
git -C "$repo" rm -q --cached 'proyecto/contracts/jsonschema/config/1.0.json ' proyecto/contracts/jsonschema/NOTAS.md
esq_confirmar_indice limpia
esq_check suelto-en-la-base-quitado pasa '' 'config/1.0.json'

esq_repo suelto-en-la-base-queda
echo notas > "$proyecto/contracts/jsonschema/NOTAS.md"
esq_confirmar suelto
git -C "$repo" tag -f base > /dev/null
esq_check suelto-en-la-base-queda falla UNEXPECTED_FILE ''

# Una versión que ya estaba incompleta en la base nunca se pudo publicar: quitar lo que quedó es la salida.
esq_repo incompleta-en-la-base-quitada
printf '{}\n' > "$esquemas/9.9.casos.json"
esq_confirmar incompleta
git -C "$repo" tag -f base > /dev/null
git -C "$repo" rm -q proyecto/contracts/jsonschema/config/9.9.casos.json
esq_confirmar_indice quitada
esq_check incompleta-en-la-base-quitada pasa '' 'estaba incompleta'

esq_repo incompleta-en-la-base-queda
printf '{}\n' > "$esquemas/9.9.casos.json"
esq_confirmar incompleta
git -C "$repo" tag -f base > /dev/null
esq_check incompleta-en-la-base-queda falla INCOMPLETE_VERSION ''

esq_repo referencia-inexistente
resultado=$(correr "$repo.txt" env LIV_CONTRACTS_ROOT="$proyecto" LIV_CONTRACTS_WORK="$repo.trabajo" \
  sh "$backend/contracts/scripts/jsonschema.sh" check no-existe)
verificar check/referencia-inexistente "$resultado" falla GIT_ERROR '' "$repo.txt"

# La carpeta del proyecto no puede pasar por un enlace simbólico: Git buscaría otra ruta y la carpeta de la base
# quedaría escondida. Windows sin permiso no los crea en el disco: ahí el escenario se omite a la vista.
esq_repo raiz-con-enlace
if MSYS=winsymlinks:nativestrict ln -s "$proyecto" "$repo/enlace" 2> /dev/null && [ -L "$repo/enlace" ]; then
  resultado=$(correr "$repo.txt" env LIV_CONTRACTS_ROOT="$repo/enlace" LIV_CONTRACTS_WORK="$repo.trabajo" \
    sh "$backend/contracts/scripts/jsonschema.sh" check base)
  verificar check/raiz-con-enlace "$resultado" falla UNSAFE_ROOT '' "$repo.txt"
else
  printf '  omitida    check/raiz-con-enlace: este sistema no crea enlaces simbólicos (corre en CI)\n'
fi

# Que el repositorio esté bajo un enlace simbólico no importa: solo cuenta la ruta dentro del repositorio.
esq_repo padre-enlazado
if MSYS=winsymlinks:nativestrict ln -s "$repo" "$repo.enlace" 2> /dev/null && [ -L "$repo.enlace" ]; then
  resultado=$(correr "$repo.txt" env LIV_CONTRACTS_ROOT="$repo.enlace/proyecto" LIV_CONTRACTS_WORK="$repo.trabajo" \
    sh "$backend/contracts/scripts/jsonschema.sh" check base)
  verificar check/padre-enlazado "$resultado" pasa '' 'config/1.0.json' "$repo.txt"
else
  printf '  omitida    check/padre-enlazado: este sistema no crea enlaces simbólicos (corre en CI)\n'
fi

esq_repo proyecto-inexistente
resultado=$(correr "$repo.txt" env LIV_CONTRACTS_ROOT="$repo/no-existe" LIV_CONTRACTS_WORK="$repo.trabajo" \
    sh "$backend/contracts/scripts/jsonschema.sh" check base)
verificar check/proyecto-inexistente "$resultado" falla PROJECT_MISSING '' "$repo.txt"

# Como lo usa el CI: un clon superficial con el commit del PR y su padre, y HEAD^1 como base. Con un solo commit no
# hay padre, y la falta de base es una falla, nunca un «nada que proteger».
esq_repo clon-superficial
printf '{"esquema":"otro"}\n' > "$esquemas/1.0.json"
esq_confirmar cambia
for profundidad in 2 1; do
  clon="$repo.clon$profundidad"
  rm -rf "$clon"
  git clone -q --depth "$profundidad" "file://$repo" "$clon"
  resultado=$(correr "$repo.txt" env LIV_CONTRACTS_ROOT="$clon/proyecto" LIV_CONTRACTS_WORK="$clon.trabajo" \
    sh "$backend/contracts/scripts/jsonschema.sh" check 'HEAD^1')
  if [ "$profundidad" = 2 ]; then
    verificar "check/clon-superficial-profundidad-2" "$resultado" falla VERSION_CHANGED '' "$repo.txt"
  else
    verificar "check/clon-superficial-profundidad-1" "$resultado" falla GIT_ERROR '' "$repo.txt"
  fi
done

echo "Esquemas JSON, publish:"
# Como esq_repo, deja en las mismas variables globales (repo, proyecto, esquemas, prefijo_del_proyecto) el repositorio
# del escenario, para que esq_confirmar y esq_en_indice sirvan en los dos grupos. Aquí el proyecto es la raíz.
esq_publicacion() { # esq_publicacion NOMBRE
  dir="$trabajo/esquemas-publicar/$1"
  repo="$dir/repo"
  proyecto="$repo"
  prefijo_del_proyecto=
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
# Publica el commit HEAD, o el de SHA_A_PUBLICAR si se da.
esq_publicar() { # esq_publicar NOMBRE ESPERADO MOTIVO SALIDA [SHA_A_PUBLICAR]
  resultado=$(correr "$dir/salida.txt" env LIV_CONTRACTS_ROOT="$repo" LIV_CONTRACTS_WORK="$dir/trabajo" \
    LIV_GH="sh $pruebas/fake-gh.sh" FAKE_GH_STORE="$almacen" GH_REPO=liv/prueba \
    GITHUB_SHA="${5:-$(git -C "$repo" rev-parse HEAD)}" sh "$backend/contracts/scripts/jsonschema.sh" publish)
  verificar "publish/$1" "$resultado" "$2" "$3" "$4" "$dir/salida.txt"
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
# La release se publicó con el commit HEAD como objetivo.
publicada_en_head() { grep -q -- "^release edit .* --target $(git -C "$repo" rev-parse HEAD) " "$almacen/llamadas"; }
sin_llamadas() { [ ! -s "$almacen/llamadas" ]; }

etiqueta_1_0=jsonschema-config-v1.0
etiqueta_1_1=jsonschema-config-v1.1

esq_publicacion nueva
esq_publicar nueva pasa '' "publicada $etiqueta_1_0"
caso_almacen esquema-nueva-queda-completa esq_completa "$etiqueta_1_0" 1.0
caso_almacen esquema-nueva-apunta-al-commit publicada_en_head

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

# Se publica el commit GITHUB_SHA aunque HEAD tenga más: la versión 1.1 de un commit posterior no se publica.
esq_publicacion commit-anterior-a-head
primer_commit=$(git -C "$repo" rev-parse HEAD)
printf '{"esquema":"1.1"}\n' > "$esquemas/1.1.json"
printf '{"casos":[1]}\n' > "$esquemas/1.1.casos.json"
esq_confirmar v1.1
esq_publicar commit-anterior-a-head pasa '' "publicada $etiqueta_1_0" "$primer_commit"
sin_la_1_1() { [ ! -d "$almacen/releases/$etiqueta_1_1" ]; }
caso_almacen esquema-commit-anterior-sin-la-1.1 sin_la_1_1
publicada_en_el_primero() { grep -q -- "^release edit .* --target $primer_commit " "$almacen/llamadas"; }
caso_almacen esquema-commit-anterior-apunta-al-primero publicada_en_el_primero

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

# Se verifica lo descargado de una release que ya estaba publicada.
esq_publicacion publicada-descarga-alterada
esq_sembrar "$etiqueta_1_0" published 1.0
touch "$almacen/download-altera"
esq_publicar publicada-descarga-alterada falla PUBLISHED_CHANGED ''

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

# Cada versión necesita sus dos archivos: sin los casos o sin el esquema no se llama a GitHub.
esq_publicacion sin-casos
git -C "$repo" rm -q contracts/jsonschema/config/1.0.casos.json
esq_confirmar_indice sin-casos
esq_publicar sin-casos falla INCOMPLETE_VERSION ''
caso_almacen esquema-sin-casos-sin-llamar-a-gh sin_llamadas

esq_publicacion casos-sin-esquema
git -C "$repo" rm -q contracts/jsonschema/config/1.0.json
esq_confirmar_indice casos-sin-esquema
esq_publicar casos-sin-esquema falla INCOMPLETE_VERSION ''
caso_almacen esquema-casos-sin-esquema-sin-llamar-a-gh sin_llamadas

esq_publicacion fuera-de-forma
printf '{}\n' > "$esquemas/v1.1.json"
esq_confirmar fuera-de-forma
esq_publicar fuera-de-forma falla UNEXPECTED_FILE ''
caso_almacen esquema-fuera-de-forma-sin-llamar-a-gh sin_llamadas

# Un enlace simbólico (modo 120000) o un submódulo en el commit se rechazan; se ponen en el índice, sin tocar el disco.
esq_publicacion enlace-simbolico
esq_en_indice 120000 contracts/jsonschema/config/1.1.json 1.0.json
esq_confirmar_indice enlace
esq_publicar enlace-simbolico falla UNEXPECTED_FILE ''
caso_almacen esquema-enlace-simbolico-sin-llamar-a-gh sin_llamadas

esq_publicacion submodulo
esq_en_indice 160000 contracts/jsonschema/otro ''
esq_confirmar_indice submodulo
esq_publicar submodulo falla UNEXPECTED_FILE ''
caso_almacen esquema-submodulo-sin-llamar-a-gh sin_llamadas

esq_publicacion sin-esquemas
git -C "$repo" rm -q -r contracts/jsonschema
esq_confirmar_indice sin-esquemas
esq_publicar sin-esquemas falla NO_SCHEMAS ''

# GITHUB_SHA tiene que ser un commit completo: una rama o un valor corto no sirven.
esq_publicacion github-sha-no-es-un-commit
resultado=$(correr "$dir/salida.txt" env LIV_CONTRACTS_ROOT="$repo" LIV_CONTRACTS_WORK="$dir/trabajo" \
  LIV_GH="sh $pruebas/fake-gh.sh" FAKE_GH_STORE="$almacen" GH_REPO=liv/prueba GITHUB_SHA=HEAD \
  sh "$backend/contracts/scripts/jsonschema.sh" publish)
verificar publish/github-sha-no-es-un-commit "$resultado" falla '' 'GITHUB_SHA debe ser un commit completo' "$dir/salida.txt"
caso_almacen esquema-github-sha-no-es-un-commit-sin-llamar-a-gh sin_llamadas

# Si no se puede calcular el SHA-256, no se verifica nada ni se publica (un hash vacío no coincide con otro vacío).
esq_publicacion sin-sha256sum
mkdir -p "$dir/bin"
printf '#!/bin/sh\nexit 0\n' > "$dir/bin/sha256sum"
chmod +x "$dir/bin/sha256sum"
resultado=$(correr "$dir/salida.txt" env PATH="$dir/bin:$PATH" LIV_CONTRACTS_ROOT="$repo" LIV_CONTRACTS_WORK="$dir/trabajo" \
  LIV_GH="sh $pruebas/fake-gh.sh" FAKE_GH_STORE="$almacen" GH_REPO=liv/prueba \
  GITHUB_SHA="$(git -C "$repo" rev-parse HEAD)" sh "$backend/contracts/scripts/jsonschema.sh" publish)
verificar publish/sin-sha256sum "$resultado" falla PUBLISH_ERROR '' "$dir/salida.txt"
caso_almacen esquema-sin-sha256sum-sin-crear sin_crear

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

# El ataque que el enlace permite: la raíz del repositorio alcanzada por un enlace dentro del mismo repositorio
# (backend -> .). Git vería la carpeta de la base en otra ruta y no encontraría nada que proteger.
esq_publicacion raiz-enlazada-a-si-misma
git -C "$repo" tag base
if MSYS=winsymlinks:nativestrict ln -s . "$repo/backend" 2> /dev/null && [ -L "$repo/backend" ]; then
  resultado=$(correr "$dir/salida.txt" env LIV_CONTRACTS_ROOT="$repo/backend" LIV_CONTRACTS_WORK="$dir/trabajo" \
    sh "$backend/contracts/scripts/jsonschema.sh" check base)
  verificar check/raiz-enlazada-a-si-misma "$resultado" falla UNSAFE_ROOT '' "$dir/salida.txt"
else
  printf '  omitida    check/raiz-enlazada-a-si-misma: este sistema no crea enlaces simbólicos (corre en CI)\n'
fi

echo "Esquemas JSON, uso:"
esq_publicacion uso
for orden in '' desconocida 'check' 'check -x' 'publish --otra' 'check HEAD sobrante'; do
  # $orden se parte en palabras a propósito: cada valor de la lista es una línea de órdenes.
  # shellcheck disable=SC2086
  resultado=$(correr "$dir/salida.txt" env LIV_CONTRACTS_ROOT="$repo" LIV_CONTRACTS_WORK="$dir/trabajo" \
    sh "$backend/contracts/scripts/jsonschema.sh" $orden)
  verificar "uso/${orden:-sin-orden}" "$resultado" falla '' 'Uso:' "$dir/salida.txt"
done
