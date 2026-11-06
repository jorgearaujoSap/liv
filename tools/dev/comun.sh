# Funciones de iniciar.sh y detener.sh. No se ejecuta solo. Es sh de POSIX: corre con Git Bash, bash y dash.
#
# Cómo sabe detener.sh qué es suyo: iniciar.sh se niega a arrancar si los puertos 4200, 8080 u 8081 están
# ocupados, así que lo que escuche en ellos después lo levantó él. Al lanzar cada servicio deja una marca
# (registros/<puerto>.lanzado); detener.sh solo termina, en los puertos marcados, procesos java o node.

PUERTOS="8080 8081 4200"

paso() {
  printf '\n==> %s\n' "$1"
}

falla() {
  printf '\n✗ %s\n' "$1" >&2
  exit 1
}

en_windows() {
  case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*) return 0 ;;
    *) return 1 ;;
  esac
}

# Comprueba que haya cómo saber quién usa un puerto.
requiere_herramientas() {
  if ! en_windows && ! command -v lsof >/dev/null 2>&1 && ! command -v ss >/dev/null 2>&1; then
    falla "Hace falta lsof o ss para ver qué usa cada puerto (en Linux: sudo apt install lsof)."
  fi
}

# Imprime el PID del proceso que escucha en el puerto (el de Windows, en Git Bash), o nada.
pid_en_puerto() {
  if en_windows; then
    netstat -ano 2>/dev/null | awk -v p=":$1" '
      ($4 == "LISTENING" || $4 == "ESCUCHANDO") && substr($2, length($2) - length(p) + 1) == p { print $5; exit }'
  elif command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | head -n 1
  else
    ss -ltnpH "sport = :$1" 2>/dev/null | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -n 1
  fi
  return 0
}

# Nombre del programa de un PID, en minúsculas, sin ruta ni .exe.
programa_de() {
  if en_windows; then
    tasklist //FI "PID eq $1" //NH 2>/dev/null | awk 'NF { print tolower($1); exit }' | sed 's/\.exe$//'
  else
    ps -p "$1" -o comm= 2>/dev/null | awk '{ n = split($0, p, "/"); print tolower(p[n]) }'
  fi
}

# Termina el proceso, y en Windows también sus hijos.
terminar() {
  if en_windows; then
    taskkill //PID "$1" //T //F >/dev/null 2>&1 || true
  else
    kill "$1" 2>/dev/null || true
  fi
}

# Termina lo que escucha en un puerto que marcó iniciar.sh, si es java o node, y borra la marca. Dice qué hizo.
#   detener_servicio <puerto> <nombre>
detener_servicio() {
  marca="$REGISTROS/$1.lanzado"
  if [ ! -f "$marca" ]; then
    printf '   %s: no lo levantó iniciar.sh, no se toca.\n' "$2"
    return 0
  fi
  pid=$(pid_en_puerto "$1")
  lanzado=$(cat "$marca")
  if [ -n "$pid" ]; then
    case "$(programa_de "$pid")" in
      java | node)
        terminar "$pid"
        printf '   %s detenido.\n' "$2"
        ;;
      *) printf '   %s: en el puerto %s hay otro programa, no se toca.\n' "$2" "$1" ;;
    esac
  else
    printf '   %s ya no estaba escuchando.\n' "$2"
  fi
  # El lanzador (mvnw, npm), si sigue vivo porque el servicio nunca llegó a escuchar.
  if [ -n "$lanzado" ] && kill -0 "$lanzado" 2>/dev/null; then
    kill "$lanzado" 2>/dev/null || true
  fi
  rm -f "$marca"
}

# Espera a que un servicio escriba su línea de listo y escuche en su puerto. Si muere, si su registro trae un error
# o si no llega en el plazo, muestra el final del registro y falla.
#   esperar <nombre> <registro> <texto de listo> <puerto> <segundos> <pid del lanzador>
esperar() {
  nombre=$1 registro=$2 listo=$3 puerto=$4 plazo=$5 lanzador=$6
  segundos=0
  printf '   esperando (hasta %s s; registro en %s)' "$plazo" "${registro#"$RAIZ"/}"
  while [ "$segundos" -lt "$plazo" ]; do
    if grep -q "$listo" "$registro" 2>/dev/null && [ -n "$(pid_en_puerto "$puerto")" ]; then
      printf '\n   Listo: %s.\n' "$nombre"
      return 0
    fi
    if ! kill -0 "$lanzador" 2>/dev/null; then
      printf '\n   El proceso terminó antes de quedar listo.' >&2
      break
    fi
    if grep -qE 'APPLICATION FAILED TO START|\[ERROR\]|An unhandled exception occurred' "$registro" 2>/dev/null; then
      break
    fi
    sleep 2
    segundos=$((segundos + 2))
    printf '.'
  done
  printf '\n--- final de %s ---\n' "${registro#"$RAIZ"/}" >&2
  tail -n 25 "$registro" >&2 || true
  falla "No arrancó: $nombre. Arriba está el final de su registro."
}
