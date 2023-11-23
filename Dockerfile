FROM 10.8.0.5:5000/spark-s3:0.0.3

ENV PATH="${PATH}:/opt/spark/bin"

WORKDIR /app
COPY target/sparkjob-jar-with-dependencies.jar /app/sparkjob.jar

#COPY statements-ingest-spark.json  /app/statements-ingest-spark.json
COPY bin/run.sh  /app/run.sh
COPY bin/run_local.sh  /app/run_local.sh
