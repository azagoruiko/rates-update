package org.zagoruiko.rates.service;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.util.List;

@Service
public class SparkServiceImpl implements SparkService {
    private SparkSession spark;
    @Autowired
    public void setSparkSession(SparkSession sparkSession) {
        this.spark = sparkSession;
    }
    @Override
    public void initCurrenciesTables() {
        spark.sql("DROP TABLE IF EXISTS currencylayer" );

        spark.sql("CREATE EXTERNAL TABLE currencylayer " +
                "(date DATE, asst STRING, quote STRING, rate FLOAT) " +
                "ROW FORMAT DELIMITED FIELDS TERMINATED BY ',' " +
                "PARTITIONED BY (asset STRING) " +
                "LOCATION 's3a://currency/currencylayer/' " );

        repairCurrenciesTables();
    }

    @Override
    public void repairCurrenciesTables() {
        spark.sql("MSCK REPAIR TABLE currencylayer").select().show();
    }


    @Override
    public Dataset<Row> selectRate() {
        return spark.sql("SELECT asset, quote, date_trunc(to_date(date, 'yyyy-MM-dd'), 'YEAR') year, max(date) max_ts, min(date) min_ts " +
                "FROM currencylayer group by asset, quote, date_trunc(to_date(date, 'yyyy-MM-dd'), 'YEAR')").select(
                functions.col("asset"),
                functions.col("quote"),
                functions.col("year"),
                functions.col("min_ts"),
                functions.col("max_ts")
        );
    }


    @Override
    public Date selectMaxDate(String source, String asset, String quote) {
        List<Row> output = spark.sql(String.format("SELECT COALESCE(MAX(date), to_date('2018-01-01')) max_date FROM currencylayer WHERE" +
                " asset='%s' AND quote='%s'", asset, quote))
                .select(functions.col("max_date")).collectAsList();
        if (output.size() > 0 && output.get(0).size() > 0) {
            System.out.format("!!!! %s - %s", output.get(0), output.get(0).getAs(0));
            return output.get(0).getDate(0);
        } else {
            return new Date(0);
        }
    }


}
