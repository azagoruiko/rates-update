package org.zagoruiko.rates.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface StorageService {

    void storeAsCsvFile(String bucket, String table, String asset, String quote, List<List<Object>> data,
                        Function<List<List<Object>>, Map<String, List<String>>> convertToCsv) throws IOException;

    void createPartition(String bucket, String table, String asset, String quote) throws IOException;

    void prepareTableFolder(String bucket, String table) throws IOException;
}
