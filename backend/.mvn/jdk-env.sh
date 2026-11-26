# ----------------------------------------------------------------------------
# Resolve a usable JDK for the Maven wrapper. Companion of jdk-env.cmd, which
# does the same for mvnw.cmd on Windows.
#
# mvnw only looks at JAVA_HOME, or at java/javac on PATH, and dies when neither
# is available (e.g. the shell was started before JAVA_HOME was created). This
# file is sourced by mvnw and exports JAVA_HOME when it can find a JDK.
#
# Resolution order:
#   1. a JDK 17 install, the target of this project
#   2. the persisted user / machine JAVA_HOME in the Windows registry
#   3. any other JDK found in the well-known install locations
# ----------------------------------------------------------------------------

# mvnw runs with `set -euf`, i.e. pathname expansion disabled. The candidate
# patterns below only resolve when globbing is on, so re-enable it while
# probing and restore the caller's setting afterwards.
_mvnw_restore_noglob=0
case $- in
*f*)
  _mvnw_restore_noglob=1
  set +f
  ;;
esac

mvnw_usable_jdk() {
  [ -n "${1-}" ] && [ -x "$1/bin/java" ] && [ -x "$1/bin/javac" ]
}

mvnw_accept() {
  mvnw_usable_jdk "${1-}" || return 1
  JAVA_HOME="$1"
  export JAVA_HOME
  return 0
}

mvnw_registry_java_home() {
  command -v reg >/dev/null 2>&1 || return 1
  # `//v` rather than `/v`: Git Bash rewrites the leading-slash argument as a path.
  _mvnw_value="$(reg query "$1" //v JAVA_HOME 2>/dev/null | tr -d '\r' | sed -n 's/.*REG_SZ[[:space:]]*//p')" || :
  [ -n "${_mvnw_value-}" ] || return 1
  if command -v cygpath >/dev/null 2>&1; then
    _mvnw_value="$(cygpath -u "$_mvnw_value" 2>/dev/null)" || :
  fi
  mvnw_accept "$_mvnw_value"
}

if ! mvnw_usable_jdk "${JAVA_HOME-}"; then
  for _mvnw_home in \
    "/c/Program Files/Java/jdk-17"*                  \
    "/c/Program Files/Eclipse Adoptium/jdk-17"*     \
    "/c/Program Files/Microsoft/jdk-17"*            \
    "/c/Program Files/Amazon Corretto/jdk17"*       \
    "/c/Program Files/Zulu/zulu-17"*                \
    "/c/Program Files/BellSoft/LibericaJDK-17"*     \
    "/c/Program Files/Semeru/jdk-17"*               \
    "/c/Program Files (x86)/Android/openjdk/jdk-17"* \
    "$HOME/.jdks/"*17*                              \
    /usr/lib/jvm/*17*                               \
    /Library/Java/JavaVirtualMachines/*17*          \
    /opt/java/*17*; do
    if mvnw_accept "$_mvnw_home"; then break; fi
  done
fi

if ! mvnw_usable_jdk "${JAVA_HOME-}"; then
  mvnw_registry_java_home 'HKCU\Environment' || :
fi

if ! mvnw_usable_jdk "${JAVA_HOME-}"; then
  mvnw_registry_java_home 'HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment' || :
fi

if ! mvnw_usable_jdk "${JAVA_HOME-}"; then
  for _mvnw_home in \
    "/c/Program Files/Java/jdk"*                     \
    "/c/Program Files/Eclipse Adoptium/jdk"*         \
    "/c/Program Files/Microsoft/jdk"*                \
    "/c/Program Files/Amazon Corretto/jdk"*          \
    "/c/Program Files/Zulu/zulu"*                    \
    "/c/Program Files (x86)/Android/openjdk/jdk"*    \
    "$HOME/.jdks/"*; do
    if mvnw_accept "$_mvnw_home"; then break; fi
  done
fi

unset -f mvnw_usable_jdk mvnw_accept mvnw_registry_java_home 2>/dev/null || :
unset _mvnw_home _mvnw_value 2>/dev/null || :

if [ "$_mvnw_restore_noglob" = 1 ]; then
  set -f
fi
unset _mvnw_restore_noglob

if [ -z "${JAVA_HOME-}" ] && ! command -v java >/dev/null 2>&1; then
  echo "warning: no JDK found; install a JDK 17 or set JAVA_HOME" >&2
fi
