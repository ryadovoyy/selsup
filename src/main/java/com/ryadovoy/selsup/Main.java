package com.ryadovoy.selsup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    private static final int THREAD_COUNT = 5;
    private static final int REQUEST_LIMIT = 1;
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    public static void main(String[] args) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        HttpClient httpClient = HttpClient.newHttpClient();
        var httpJsonHelper = new CrptApi.HttpJsonHelper(objectMapper);

        CrptApi crptApi = new CrptApi(httpClient, httpJsonHelper, REQUEST_LIMIT, TIME_UNIT);
        var document = new CrptApi.LpIntroduceGoodsDocumentDto();

        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            threads.add(new Thread(() -> {
                try {
                    crptApi.createDocument(document, "").join();
                } catch (Exception e) {
                    logger.log(Level.WARNING, e.getMessage());
                }
            }));
        }

        for (Thread thread : threads) {
            thread.start();
        }
    }
}
