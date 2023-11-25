package org.zagoruiko.rates.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class ObjectStorageService implements StorageService {

    private static Format format = new SimpleDateFormat("yyyy-MM-dd");

    private AmazonS3 s3Client;

    private String convertTime(long time){
        Date date = new Date(time);
        return format.format(date);
    }

    public ObjectStorageService(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public void storeAsCsvFile(String bucket, String table, String asset, String quote, List<List<Object>> data,
                               Function<List<List<Object>>, Map<String, List<String>>> convertToCsv) throws IOException {
        Map<String, List<String>> output = convertToCsv.apply(data);
        for (Map.Entry<String, List<String>> entry : output.entrySet()) {
            File file = new File("/tmp" + File.separator + entry.getKey() + ".csv");
            Logger.getAnonymousLogger().log(Level.INFO, "Saving " + file.getAbsolutePath());
            FileWriter fileWriter = new FileWriter(file, false);
            PrintWriter printWriter = new PrintWriter(fileWriter);
            printWriter.println(entry.getValue().stream().collect(Collectors.joining("\n")));

            printWriter.flush();
            printWriter.close();

            try {
                PutObjectRequest putObjectRequest = new PutObjectRequest(bucket,
                        String.format("%s/asset=%s/quote=%s/%s.csv", table, asset, quote, entry.getKey()),
                        file);

                this.s3Client.putObject(putObjectRequest);
                file.delete();
            } catch (Exception e) {
                Logger.getAnonymousLogger().log(Level.SEVERE, e.getMessage());
                System.exit(1);
            }
        }
    }

    @Override
    public void createPartition(String bucket, String table, String asset, String quote) throws IOException {
        File file = new File("/tmp" + File.separator + "__PARTITION__");
        file.createNewFile();
        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucket,
                    String.format("%s/asset=%s/quote=%s/__PARTITION__", table, asset, quote),
                    file);

            this.s3Client.putObject(putObjectRequest);
            file.delete();
        } catch (Exception e) {
            Logger.getAnonymousLogger().log(Level.SEVERE, "Error saving dummy file for partition " + e.getMessage());
            System.exit(1);
        }
    }

    @Override
    public void prepareTableFolder(String bucket, String table) throws IOException {
        File file = new File("/tmp" + File.separator + "__PARTITION__");
        file.createNewFile();
        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucket,
                    String.format("%s/__PARTITION__", table),
                    file);

            this.s3Client.putObject(putObjectRequest);
            file.delete();
        } catch (Exception e) {
            Logger.getAnonymousLogger().log(Level.SEVERE, "Error saving dummy file " + e.getMessage());
            System.exit(1);
        }
    }
}
