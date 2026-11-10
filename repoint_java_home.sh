#!/bin/sh
JDK21="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"
if [ ! -x "$JDK21/bin/javac" ]; then
  echo "ERROR: javac not found at $JDK21/bin/javac"
  exit 1
fi
export JAVA_HOME="$JDK21"
export PATH="$JAVA_HOME/bin:$PATH"
echo "Re-pointed JAVA_HOME to: $JAVA_HOME"
echo "javac -version:"; "$JAVA_HOME/bin/javac" -version 2>&1
