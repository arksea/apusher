#!/bin/bash

PRG="$0"
PRGDIR=`dirname "$PRG"`

if [ -r "$PRGDIR/setenv.sh" ]; then
  . "$PRGDIR/setenv.sh"
fi

if [ -z "$APP_HOME" ]; then
    echo ""
else
    proc=`ps ax | grep $APP_HOME | grep java | grep -v grep | awk '{print $1}'`
    echo $proc
fi
