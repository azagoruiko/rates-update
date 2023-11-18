#!/usr/bin/env bash

VER=$1

export NOMAD_ADDR=http://10.8.0.1:4646
export CONSUL_HTTP_ADDR=10.8.0.1:8500

echo "Hive metastore URL = ${POSTGRES_METASTORE_JDBC_URL}"

/opt/spark/bin/spark-submit \
  --class org.zagoruiko.rates.Main \
  --master local[*] \
  --conf spark.sql.catalogImplementation=hive \
  --conf spark.hadoop.fs.s3a.path.style.access=true \
  --conf spark.hadoop.datanucleus.autoCreateSchema=true \
  --conf spark.hadoop.datanucleus.autoCreateTables=true \
  --conf spark.hadoop.javax.jdo.option.ConnectionURL=${POSTGRES_METASTORE_JDBC_URL} \
  --conf spark.hadoop.javax.jdo.option.ConnectionDriverName=${POSTGRES_JDBC_DRIVER} \
  --conf spark.hadoop.javax.jdo.option.ConnectionUserName=${POSTGRES_JDBC_USER} \
  --conf spark.hadoop.javax.jdo.option.ConnectionPassword=${POSTGRES_JDBC_PASSWORD} \
  --conf spark.executor.userClassPathFirst=true \
  --conf spark.driver.userClassPathFirst=true \
  --jars local:/opt/spark/jars/gson-2.8.5.jar,/opt/apache-hive-3.1.3-bin/lib/hive*.jar,/opt/apache-hive-3.1.3-bin/lib/*sql*.jar,/opt/apache-hive-3.1.3-bin/lib/datanucleus*.jar,/opt/apache-hive-3.1.3-bin/lib/Hikari*.jar,/opt/apache-hive-3.1.3-bin/lib/javax*.jar \
  local:/app/sparkjob.jar
