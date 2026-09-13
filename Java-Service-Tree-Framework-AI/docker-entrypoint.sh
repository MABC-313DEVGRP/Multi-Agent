#!/bin/sh

set -e

GC_OPTS=${GC_OPTS:="-XX:+UseNUMA -XX:+UseG1GC"}
MEM_OPTS=${MEM_OPTS:="-Xms1024m -Xmx1024m"}
NET_OPTS=${NET_OPTS:="-Dsun.net.inetaddr.ttl=0 -Dsun.net.inetaddr.negative.ttl=0 -Djava.net.preferIPv4Stack=true"}

# Spring 프로파일 환경 변수 설정
SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-multiagent-local}

JVM_OPTS=${JAVA_OPTS:="-server $MEM_OPTS $NET_OPTS"}

#spring boot start
exec java -Duser.timezone=Asia/Seoul -Djava.security.egd=file:/dev/./urandom $GC_OPTS -jar $JVM_OPTS -Dspring.profiles.active=$SPRING_PROFILES_ACTIVE javaServiceTreeFramework.jar $@
