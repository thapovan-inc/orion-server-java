FROM openjdk:latest

ADD build/distributions/orion-server-1.0-SNAPSHOT.tgz /opt/

RUN mv /opt/orion-server-1.0-SNAPSHOT /opt/orion

CMD ["/opt/orion/bin/orion-server"]