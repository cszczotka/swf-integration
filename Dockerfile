FROM openjdk:8-jdk-slim

RUN mkdir /code
ADD . /code

WORKDIR /code

RUN sh ./gradlew build

EXPOSE 8080
CMD ["sh", "./gradlew", "bootRun"]
