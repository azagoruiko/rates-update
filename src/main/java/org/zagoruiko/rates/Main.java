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
                new String[]{"USD", "UAH"},
                new String[]{"USD", "CZK"},
                new String[]{"USD", "BTC"},
                new String[]{"EUR", "USD"},
                new String[]{"EUR", "CZK"},
                new String[]{"EUR", "UAH"},
                new String[]{"EUR", "BTC"},
                new String[]{"CZK", "BTC"},
                new String[]{"CZK", "UAH"},
                new String[]{"UAH", "BTC"},

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
            Date currentMaxDate = (Date) format.parseObject("2018-01-01");
            try {
                currentMaxDate = this.sparkService.selectMaxDate("currencylayer", asset, firstQuote);
            } catch (Exception e) {
                System.out.format("FAILED TO SELECT MAX DATE %s - %s", currentMaxDate, startDate);
            }

            System.out.format("!!!! %s - %s", currentMaxDate, startDate);
            calendar.setTime(new Date(Math.max(
                    startDate.getTime(),
                    currentMaxDate.getTime()
            )));
            Date maxDate = calendar.getTime().before(new Date()) ? calendar.getTime() : new Date();
            do {
                Logger.getAnonymousLogger().log(Level.INFO, String.format("Querying $s %s-%s for %s",
                        asset, quotes, maxDate));
                data = this.currencyLayerRatesClient.loadContents(asset, quotes, maxDate, 150);

                for (String quote : mappedPairs.get(asset)) {
                    this.storageService.storeAsCsvFile("currency", "currencylayer", asset, quote,
                            data.stream().filter(ds -> ds.get(2).equals(quote)).collect(Collectors.toList()),
                            dt -> CurrencyLayer.raw2CSVMap(dt));
                }

                calendar.add(Calendar.DATE, 150);
                maxDate = calendar.getTime().before(new Date()) ? calendar.getTime() : new Date();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } while (data.size() > 0);
        }

        sparkService.selectRate().show(5000);
    }
}