#!/bin/bash

export JAVA_HOME=/opt/jdk1.8.0_66
export LANG=en_US.UTF-8
export APP_HOME=/opt/apusher

jmxBindIP=`/sbin/ifconfig -a|grep 10.79|grep -v 127.0.0.1|grep -v inet6|awk '{print $2}'|tr -d "addr:"|head -n 1`

JAVA_OPTS="$JAVA_OPTS -server -Dfile.encoding=utf-8 -Xmx256M -Xms256M"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC -XX:+PrintClassHistogram -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+PrintGCDetails"
JAVA_OPTS="$JAVA_OPTS -Xbootclasspath/p:/opt/apusher/lib/alpn-boot-8.1.6.v20151105.jar"
JAVA_OPTS="$JAVA_OPTS -Xloggc:$APP_HOME/logs/gc.log"
JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.authenticate=true -Dcom.sun.management.jmxremote.ssl=false"
JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.port=8875 -Dcom.sun.management.jmxremote.rmi.port=8875 -Djava.rmi.server.hostname=$jmxBindIP"
JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.password.file=$APP_HOME/bin/jmx.password -Dcom.sun.management.jmxremote.access.file=$APP_HOME/bin/jmx.access"
export JAVA_OPTS
