package org.zagoruiko.rates.service;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.sql.Date;

public interface SparkService {
    void initCurrenciesTables();

    void repairCurrenciesTables();

    Dataset<Row> selectRate();

    Date selectMaxDate(String source, String asset, String quote);
}
