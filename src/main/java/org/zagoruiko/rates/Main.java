package org.zagoruiko.rates;

import com.amazonaws.services.dynamodbv2.xspec.S;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import org.zagoruiko.rates.client.BinanceRatesClient;
import org.zagoruiko.rates.client.CurrencyLayerRatesClient;
import org.zagoruiko.rates.client.S3Client;
import org.zagoruiko.rates.service.SparkService;
import org.zagoruiko.rates.service.StorageService;
import org.zagoruiko.rates.util.Binance;
import org.zagoruiko.rates.util.CurrencyLayer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Component
@PropertySource(value = "classpath:application.properties")
public class Main {

    private static Format format = new SimpleDateFormat("yyyy-MM-dd");
    private static Calendar calendar = Calendar.getInstance();
    private BinanceRatesClient binanceRatesClient;

    private StorageService storageService;

    private SparkService sparkService;
    private CurrencyLayerRatesClient currencyLayerRatesClient;

    @Autowired
    public void setBinanceRatesClient(BinanceRatesClient binanceRatesClient) {
        this.binanceRatesClient = binanceRatesClient;
    }

    @Autowired
    public void setBinanceRatesClient(CurrencyLayerRatesClient currencyLayerRatesClient) {
        this.currencyLayerRatesClient = currencyLayerRatesClient;
    }

    @Autowired
    public void setStorageService(StorageService storageService) {
        this.storageService = storageService;
    }
    @Autowired
    public void setSparkService(SparkService sparkService) {
        this.sparkService = sparkService;
    }

    public static void main(String[] args) throws IOException, ParseException {
        System.out.println(String.join(",", args));

        ClassLoader cl = ClassLoader.getSystemClassLoader();

        URL[] urls = ((URLClassLoader)cl).getURLs();

        for(URL url: urls){
            System.out.println(url.getFile());
        }

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.scan(Main.class.getPackage().getName());
        context.refresh();
        context.getBean(Main.class).run(args);
    }

    public void run(String[] args) throws IOException, ParseException {

        Date startDate = (Date) format.parseObject("2015-01-01");
        storageService.prepareTableFolder("currency", "binance");
        storageService.prepareTableFolder("investing.com.rates", "main");
        storageService.prepareTableFolder("currency", "currencylayer");
        sparkService.initCurrenciesTables();
        sparkService.initInvestingTables();
        sparkService.selectInvestingRateAll().show(5000);

        Date today = new Date();
        for (String[] pair : new String[][]{
                new String[]{"BTC", "USDT"},
                new String[]{"USDT", "UAH"},
                new String[]{"ETH", "USDT"},
                new String[]{"ETH", "BTC"}
        }) {
            List<List<Object>> data = null;
            this.storageService.createPartition("currency", "binance", pair[0], pair[1]);
            sparkService.repairCurrenciesTables();
            Date currentMaxDate = this.sparkService.selectMaxDate("binance", pair[0], pair[1]);

            System.out.format("!!!! %s - %s", currentMaxDate, startDate);
            calendar.setTime(new Date(Math.max(
                    startDate.getTime(),
                    currentMaxDate.getTime()
            )));
            Date maxDate = calendar.getTime();
            do {
                Logger.getAnonymousLogger().log(Level.INFO, String.format("Querying $s %s-%s for %s",
                        pair[0], pair[1], maxDate));
                data = this.binanceRatesClient.loadContents(pair[0], pair[1], maxDate, 1000);

                this.storageService.storeAsCsvFile("currency", "binance", pair[0], pair[1], data,
                        d -> Binance.klines2CSVMap(d));

                calendar.add(Calendar.DATE, 1000);
                maxDate = calendar.getTime();
            } while (data.size() > 0);
        }


        startDate = (Date) format.parseObject("2018-01-01");
        String[][] pairs = new String[][]{
                new String[]{"BTC", "USD"},
                new String[]{"USD", "UAH"},
                new String[]{"USD", "CZK"},
                new String[]{"EUR", "USD"},
                new String[]{"EUR", "CZK"},
                new String[]{"EUR", "UAH"},
        };
        Map<String, Set<String>> mappedPairs = new HashMap<>();
        for (String[] pair : pairs) {
            mappedPairs.computeIfAbsent(pair[0], k -> new HashSet<>());
            mappedPairs.get(pair[0]).add(pair[1]);
        }
        for (String asset : mappedPairs.keySet()) {
            String quotes = String.join(",", mappedPairs.get(asset));
            String firstQuote = mappedPairs.get(asset).iterator().next();
            List<List<Object>> data = null;
            this.storageService.createPartition("currency", "currencylayer", asset, firstQuote);
            sparkService.repairCurrenciesTables();
            Date currentMaxDate = this.sparkService.selectMaxDate("currencylayer", asset, firstQuote);

            System.out.format("!!!! %s - %s", currentMaxDate, startDate);
            calendar.setTime(new Date(Math.max(
                    startDate.getTime(),
                    currentMaxDate.getTime()
            )));
            Date maxDate = calendar.getTime();
            do {
                Logger.getAnonymousLogger().log(Level.INFO, String.format("Querying $s %s-%s for %s",
                        asset, quotes, maxDate));
                data = this.currencyLayerRatesClient.loadContents(asset, quotes, maxDate, 1000);

                for (String quote : mappedPairs.get(asset)) {
                    this.storageService.storeAsCsvFile("currency", "currencylayer", asset, quote,
                            data.stream().filter(ds -> ds.get(1).equals(quote)).collect(Collectors.toList()),
                            dt -> CurrencyLayer.raw2CSVMap(dt));
                }

                calendar.add(Calendar.DATE, 1000);
                maxDate = calendar.getTime();
            } while (data.size() > 0);
        }

        sparkService.selectRate().show(5000);
        sparkService.selectInvestingRate().show(5000);
    }
}