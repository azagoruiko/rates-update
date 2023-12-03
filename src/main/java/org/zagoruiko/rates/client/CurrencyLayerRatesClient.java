package org.zagoruiko.rates.client;

import org.apache.commons.lang.time.DateFormatUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class CurrencyLayerRatesClient implements RatesClient {

    private RestTemplate restTemplate;
    private static Calendar calendar = Calendar.getInstance();

    public CurrencyLayerRatesClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public List<List<Object>> loadContents(String asset, String quote, Date from, int limit) throws IOException {
        calendar.setTime(from);
        calendar.add(Calendar.DATE, Math.min(150, limit));

        List<List<Object>> data = new ArrayList<>();
        String dateString = DateFormatUtils.format(from, "yyyy-MM-dd");
        String dateToString = DateFormatUtils.format(calendar.getTime().before(new Date()) ? calendar.getTime() : new Date(), "yyyy-MM-dd");
        if (dateString.equals(dateToString)) {
            Logger.getAnonymousLogger().log(Level.INFO, String.format("Requesting equal dates %s - %s",
                    dateString, dateToString));
            return data;
        }
        String url = String.format("http://api.currencylayer.com/timeframe?access_key=52526fecd8a9faf623a947ba88d14fab&start_date=%s&end_date=%s&source=%s&currencies=%s",
                dateString, dateToString, asset, quote);
        Logger.getAnonymousLogger().log(Level.INFO, String.format("Querying %s-%s for %s - %s",
                asset, quote, dateString, dateToString));
        String response = restTemplate.getForObject(url, String.class);
        Map<String, Object> map = new HashMap<>();
        try {
            map = new ObjectMapper().readValue(response, HashMap.class);
        } catch (Exception e) {
            e.printStackTrace();
            Logger.getAnonymousLogger().log(Level.WARNING, "COULD NOT parse " + response);
        }
        Map<String, Map<String, Object>> ratesMap = (Map<String, Map<String, Object>>) map.get("quotes");
        if (ratesMap == null) {
            for (String key : map.keySet()) {
                Logger.getAnonymousLogger().log(Level.WARNING, key + "=" + map.get(key));
            }
            //throw new RuntimeException("Bad API response");
        }
        else for (String currentDate : ratesMap.keySet()) {
            Map<String, Object> quotes = ratesMap.get(currentDate);
            for (String qt : quotes.keySet()) {
                data.add(new ArrayList<Object>() {
                    {
                        add(currentDate);
                        add(asset);
                        add(qt.replace(asset, ""));
                        add(quotes.get(qt).toString());
                    }
                });
            }
        }


        return data;
    }


}
