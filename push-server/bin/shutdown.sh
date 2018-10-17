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
  echo "the Java app not alive"
else
  echo "Shutting down current running Java app(pid=$proc) : $APP_HOME"
  kill $proc
fi
