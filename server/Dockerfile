FROM openjdk:17-jdk-slim

ARG TARGETARCH
ENV TINI_VERSION v0.19.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini-${TARGETARCH} /tini
RUN chmod +x /tini
ENTRYPOINT ["/tini", "--"]

WORKDIR /
COPY *.jar gradlegrader.jar
CMD [ "java", "-jar", "gradlegrader.jar" ]

# vim: tw=0
