# Funciones de iniciar.sh y detener.sh. No se ejecuta solo.

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

# El PID del proceso que escucha en el puerto (el de Windows, en Git Bash); falla si no hay ninguno.
pid_en_puerto() {
  if en_windows; then
    pid=$(netstat -ano -p tcp 2>/dev/null | awk -v p=":$1" '($4 == "LISTENING" || $4 == "ESCUCHANDO") && substr($2, length($2) - length(p) + 1) == p { print $5; exit }')
    if [ -z "$pid" ]; then
      pid=$(netstat -ano 2>/dev/null | awk -v p=":$1" '($4 == "LISTENING" || $4 == "ESCUCHANDO") && substr($2, length($2) - length(p) + 1) == p { print $5; exit }')
    fi
  else
    pid=$(lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | head -n 1)
  fi
  [ -n "$pid" ] && printf '%s\n' "$pid"
}

# Espera a que un servicio escriba su línea de listo y escuche en su puerto. Si no llega en el plazo, muestra el
# final de su registro y falla. Al quedar listo, guarda el PID que escucha, el único que detener.sh terminará.
#   esperar <nombre> <registro> <texto de listo> <puerto> <segundos>
esperar() {
  nombre=$1 registro=$2 listo=$3 puerto=$4 plazo=$5
  segundos=0
  printf '   esperando (hasta %s s; registro en %s)' "$plazo" "${registro#"$RAIZ"/}"
  while [ "$segundos" -lt "$plazo" ]; do
    if grep -q "$listo" "$registro" 2>/dev/null && pid_en_puerto "$puerto" >/dev/null; then
      pid_en_puerto "$puerto" > "$REGISTROS/$puerto.pid"
      printf '\n   Listo: %s.\n' "$nombre"
      return 0
    fi
    if grep -qE 'APPLICATION FAILED TO START|BUILD FAILURE|An unhandled exception occurred' "$registro" 2>/dev/null; then
      break
    fi
    sleep 2
    segundos=$((segundos + 2))
    printf '.'
  done
  printf '\n--- final de %s ---\n' "${registro#"$RAIZ"/}" >&2
  tail -n 25 "$registro" >&2 || true
  falla "No arrancó: $nombre. Mira el registro de arriba; para limpiar lo que quedó: sh tools/dev/detener.sh"
}

# Termina el proceso, y en Windows también sus hijos.
terminar() {
  if en_windows; then
    taskkill //PID "$1" //T //F >/dev/null 2>&1 || true
  else
    kill "$1" 2>/dev/null || true
  fi
}
