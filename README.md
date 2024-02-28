# Data Engineering

## First steps

Init python virtual environment

```bash
python -m venv .env/data-engineering
```

Activate python virtual environment

```bash
source .env/data-engineering/bin/activate
```


Some of the projects are using different versions of java and scala, so it is recommended to use [SDKMAN](https://sdkman.io/) to manage the versions of java and scala.

Install SDKMAN

```bash
curl -s "https://get.sdkman.io" | bash
``` 

Then you can run the following command in proper project directory to install the latest version of java and scala

```bash
sdk env install 
```

The custom partitioner for the Kafka Connect S3 Sink connector requires a JDK 11. You can install it using SDKMAN
The scala projects of Apache Flink job require JDK 17 and Scala 2.12.19

Please check the .sdkmanrc file in the root of the project to see the versions of java and scala required for each project.


The docker compose file is located in the `data-engineering-tools` of the project. You can start the environment using the following command:

```bash
docker compose up -d zookeeper kafka schema-registry minio kafkactl redpanda-console kconnect-s3sink awscli minio-client
```

The command above will start the the environment for the testing Kafka Connect S3 Sink connector and other goodies like minio, redpanda, kafkactl, etc.

To start the environment for the Apache Flink project, you can run similar but excuding the `kconnect-s3sink` service.

The important part is the configure a connector for the Kafka Connect S3 Sink. 
You can use the Insonmia or Postman to send a POST request to the Kafka Connect REST API to create a connector. The connector configuration is located in the `data-engineering-tools/kafka-connect` directory of the project, the filename is `Insomnia_*.json`.