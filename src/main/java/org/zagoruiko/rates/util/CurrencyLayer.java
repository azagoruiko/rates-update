package org.zagoruiko.rates.util;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class CurrencyLayer {
    public static Map<String, List<String>> raw2CSVMap(List<List<Object>> data) {
        List<String> rawList = data.stream().map(v ->
                        String.format("%s,%s,%s,%s",
                                v.get(0), v.get(1), v.get(2), v.get(3)))
                .collect(Collectors.toList());
        //.collect(Collectors.joining("\n"));

        Map<String, List<String>> output = new HashMap<>();
        for (String line : rawList) {
            output.putIfAbsent(line.substring(0,7), new ArrayList<>());
            output.get(line.substring(0,7)).add(line);
        }
        return output;
    }
}
