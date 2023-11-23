package org.zagoruiko.rates;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import org.zagoruiko.rates.client.BinanceRatesClient;
import org.zagoruiko.rates.client.CurrencyLayerRatesClient;
import org.zagoruiko.rates.service.SparkService;
import org.zagoruiko.rates.service.StorageService;
import org.zagoruiko.rates.util.CurrencyLayer;

import java.io.IOException;
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

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.scan(Main.class.getPackage().getName());
        context.refresh();
        context.getBean(Main.class).run(args);
    }

    public void run(String[] args) throws IOException, ParseException {

        storageService.prepareTableFolder("currency", "currencylayer");
        sparkService.initCurrenciesTables();

        Date startDate = (Date) format.parseObject("2018-01-01");
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
    }
}