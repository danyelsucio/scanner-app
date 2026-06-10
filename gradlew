#!/bin/sh
#
# Gradle wrapper script
#

APP_HOME=$(cd "$(dirname "$0")" && pwd)
APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# Add default JVM options here.
DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD=maximum

warn() {
    echo "$*"
}

die() {
    echo
    echo "$*"
    echo
    exit 1
}

# OS specific support.
cygwin=false
msys=false
darwin=false
nonstop=false
case "$(uname)" in
    CYGWIN*)
        cygwin=true
        ;;
    Darwin*)
        darwin=true
        ;;
    MINGW*)
        msys=true
        ;;
    NONSTOP*)
        nonstop=true
        ;;
esac

# For Cygwin or MSYS, switch paths to Windows format before running java
if [ "$cygwin" = "true" -o "$msys" = "true" ]; then
    APP_HOME=$(cygpath --path --mixed "$APP_HOME")
    CLASSPATH=$(cygpath --path --mixed "$CLASSPATH")
fi

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ]; then
    if [ -x "$JAVA_HOME/jre/sh/java" ]; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ]; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME
Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Locate the Gradle user home directory
if [ -n "$GRADLE_USER_HOME" ]; then
    GRADLE_USER_HOME="$GRADLE_USER_HOME"
else
    GRADLE_USER_HOME="$APP_HOME/.gradle"
fi

# Location of the Gradle wrapper JAR file
if [ -n "$GRADLE_WRAPPER_JAR" ]; then
    GRADLE_WRAPPER_JAR="$GRADLE_WRAPPER_JAR"
else
    GRADLE_WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
fi

if [ ! -f "$GRADLE_WRAPPER_JAR" ]; then
    # If the wrapper JAR doesn't exist, download it
    echo "Downloading Gradle wrapper..."
    WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar"
    if command -v curl >/dev/null 2>&1; then
        curl -L -o "$GRADLE_WRAPPER_JAR" "$WRAPPER_URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$GRADLE_WRAPPER_JAR" "$WRAPPER_URL"
    else
        die "ERROR: need curl or wget to download gradle wrapper"
    fi
    chmod 644 "$GRADLE_WRAPPER_JAR"
fi

# Collect all arguments for the java command
CLASSPATH="$GRADLE_WRAPPER_JAR"
exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
