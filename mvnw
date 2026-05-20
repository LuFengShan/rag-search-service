#!/bin/bash
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Maven Wrapper startup batch script, version 3.2.0
#
# Required ENV vars:
# ------------------
#   JAVA_HOME - location of a JDK home dir
#
# Optional ENV vars
# -----------------
#   MAVEN_OPTS - parameters passed to the Java VM when running Maven
#     e.g. to debug Maven itself, use
#       set MAVEN_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000
#   MAVEN_SKIP_RC - flag to disable loading of mavenrc files
# ----------------------------------------------------------------------------

if [ -z "$MAVEN_SKIP_RC" ] ; then

  if [ -f /usr/local/etc/mavenrc ] ; then
    . /usr/local/etc/mavenrc
  fi

  if [ -f /etc/mavenrc ] ; then
    . /etc/mavenrc
  fi

  if [ -f "$HOME/.mavenrc" ] ; then
    . "$HOME/.mavenrc"
  fi

fi

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false
msys=false
darwin=false
nonstop=false
case "`uname`" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MINGW* )
    msys=true
    ;;
  NONSTOP* )
    nonstop=true
    ;;
esac

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin ; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
  [ -n "$CLASSPATH" ] && CLASSPATH=`cygpath --unix "$CLASSPATH"`
  [ -n "$MAVEN_HOME" ] && MAVEN_HOME=`cygpath --unix "$MAVEN_HOME"`
fi

# For Mingw, ensure paths are in UNIX format before anything is touched
if $msys ; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
  [ -n "$CLASSPATH" ] && CLASSPATH=`cygpath --unix "$CLASSPATH"`
  [ -n "$MAVEN_HOME" ] && MAVEN_HOME=`cygpath --unix "$MAVEN_HOME"`
fi

if [ -z "$JAVA_HOME" ] ; then
  if [ -r /etc/gentoo-release ] ; then
    JAVA_HOME=`java-config --jre-home`
  fi
fi

if [ -z "$JAVA_HOME" ]; then
  javaExecutable="`which javac`"
  if [ -n "$javaExecutable" ] && ! [ "`expr \"$javaExecutable\" : '\([^ ]*\)'`" = "no" ]; then
    # readlink(1) is not available as standard on Solaris 10.
    readLink=`which readlink`
    if [ ! `expr "$readLink" : '\([^ ]*\)'` = "no" ]; then
      if $darwin ; then
        javaExecutable="`readlink -f \"$javaExecutable\"`"
      else
        javaExecutable="`readlink -f \"$javaExecutable\"`"
      fi
      javaHome="`dirname \"$javaExecutable\"`"
      JAVA_HOME="`dirname \"$javaHome\"`"
    fi
  fi
fi

if [ -z "$JAVA_HOME" ]; then
  printf "Error: JAVA_HOME not found in your environment.\\n"
  printf "Please set the JAVA_HOME variable in your environment to match the\\n"
  printf "location of your Java installation.\\n"
  exit 1
fi

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/jre/sh/java" ] ; then
  # IBM's JDK on AIX uses strange locations for the executables
  JAVACMD="$JAVA_HOME/jre/sh/java"
else
  JAVACMD="$JAVA_HOME/bin/java"
fi

if [ ! -x "$JAVACMD" ] ; then
  printf "Error: JAVA_HOME is set to an invalid directory.\\n"
  printf "JAVA_HOME = \"$JAVA_HOME\"\\n"
  printf "Please set the JAVA_HOME variable in your environment to match the\\n"
  printf "location of your Java installation.\\n"
  exit 1
fi

if [ -z "$MAVEN_HOME" ] ; then
  MAVEN_HOME=`dirname "$0"`/..
fi

if [ -z "$MAVEN_CONFIG" ] ; then
  MAVEN_CONFIG="$HOME/.m2"
fi

# For Cygwin, switch paths to Windows format before running java
if $cygwin ; then
  JAVA_HOME=`cygpath --absolute --windows "$JAVA_HOME"`
  MAVEN_HOME=`cygpath --absolute --windows "$MAVEN_HOME"`
  MAVEN_CONFIG=`cygpath --absolute --windows "$MAVEN_CONFIG"`
  CLASSPATH=`cygpath --path --windows "$CLASSPATH"`
fi

# For Mingw, switch paths to Windows format before running java
if $msys ; then
  JAVA_HOME=`cygpath --absolute --windows "$JAVA_HOME"`
  MAVEN_HOME=`cygpath --absolute --windows "$MAVEN_HOME"`
  MAVEN_CONFIG=`cygpath --absolute --windows "$MAVEN_CONFIG"`
  CLASSPATH=`cygpath --path --windows "$CLASSPATH"`
fi

# Provide a "standardized" way to retrieve the CLI args that will
# work with both Windows and non-Windows executions.
MAVEN_CMD_LINE_ARGS="$@"
export MAVEN_CMD_LINE_ARGS

WRAPPER_JAR="$MAVEN_HOME/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

exec "$JAVACMD" \
  $MAVEN_OPTS \
  $MAVEN_DEBUG_OPTS \
  -classpath "$WRAPPER_JAR" \
  "-Dmaven.home=$MAVEN_HOME" \
  "-Dmaven.config=$MAVEN_CONFIG" \
  "-Dmaven.wrapper.path=$WRAPPER_JAR" \
  ${WRAPPER_LAUNCHER} \
  "$@"
