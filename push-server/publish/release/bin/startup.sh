#!/bin/bash

PRG="$0"
PRGDIR=`dirname "$PRG"`

if [ -r "$PRGDIR/setenv.sh" ]; then
  . "$PRGDIR/setenv.sh"
fi

if [ -z "$APP_HOME" ]; then
    echo "please export \$APP_HOME at $PRGDIR/setenv.sh"
    exit 1
fi

proc=`sh $PRGDIR/showpid.sh`
if [ "$proc" = "" ]
then
    cd $APP_HOME
    APP_OUT="$APP_HOME"/logs/server.out
    touch $APP_OUT

    jar_file(){
        echo `ls -t $APP_HOME/*.jar 2>/dev/null | head -1`
    }
    JAR_FILE=$(jar_file)

    if [ -z $JAR_FILE ]; then
        echo 'No jar file in APP_HOME: $APP_HOME' >> "$APP_OUT"
    else
        echo "Using APP_HOME:   $APP_HOME" >> "$APP_OUT"
        echo "Using JAVA_HOME:  $JAVA_HOME" >> "$APP_OUT"
        echo "Using JAR_FILE:   $JAR_FILE" >> "$APP_OUT"
        JAVA="$JAVA_HOME/bin/java"
        nohup $JAVA $JAVA_OPTS -jar $JAR_FILE "$@" >> "$APP_OUT" 2>&1 &
        msg="Java process started at $APP_HOME,(pid=$!)"
        echo $msg
    fi
else
  msg="skipped start action, the Java app already running"
  echo $msg
fi
