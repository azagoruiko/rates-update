package org.zagoruiko.rates.util;

import org.apache.commons.lang.time.DateFormatUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;
import org.zagoruiko.rates.client.CurrencyLayerRatesClient;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

class BinanceTest {

    private static Calendar calendar = Calendar.getInstance();

    @org.junit.jupiter.api.Test
    void currencyLayer() throws IOException {
        RestTemplate rt = new RestTemplateBuilder().build();
        CurrencyLayerRatesClient client = new CurrencyLayerRatesClient(rt);
        calendar.add(Calendar.DATE, -365);
        List<List<Object>> czk = client.loadContents("CZK", "USD,EUR,UAH,BTC", calendar.getTime(), 365);

        czk.size();
    }

}