package com.termdash.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class EnvironmentService {

    private String cachedWeather = "Scanning atmosphere...";
    private long lastWeatherUpdate = 0;
    private static final long WEATHER_UPDATE_INTERVAL = TimeUnit.MINUTES.toMillis(15);

    public String getGitBranch() {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD");
            pb.directory(new File(System.getProperty("user.dir")));
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String branch = reader.readLine();
                if (process.waitFor() == 0 && branch != null) {
                    return branch.trim();
                }
            }
        } catch (Exception e) {
        }
        return "DETACHED / NO GIT";
    }

    public String getWeather() {
        long now = System.currentTimeMillis();
        if (now - lastWeatherUpdate > WEATHER_UPDATE_INTERVAL) {
            updateWeatherAsync();
            lastWeatherUpdate = now;
        }
        return cachedWeather;
    }

    private void updateWeatherAsync() {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        String url = "https://api.open-meteo.com/v1/forecast?latitude=31.326&longitude=75.576&current_weather=true";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "TermDash/1.0")
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("HTTP " + response.statusCode());
                    }
                    return response.body();
                })
                .thenAccept(body -> {
                    if (body != null && !body.isBlank()) {
                        try {
                            Gson gson = new Gson();
                            JsonObject json = gson.fromJson(body, JsonObject.class);
                            JsonObject current = json.getAsJsonObject("current_weather");
                            double temp = current.get("temperature").getAsDouble();
                            int code = current.get("weathercode").getAsInt();
                            String condition = decodeWeatherCode(code);
                            this.cachedWeather = String.format("Jalandhar: %s %.1f°C", condition, temp);
                        } catch (Exception e) {
                            this.cachedWeather = "Parse Error";
                        }
                    }
                })
                .exceptionally(e -> {
                    String msg = e.getMessage();
                    if (e.getCause() != null) msg = e.getCause().getMessage();
                    if (msg != null && msg.length() > 20) msg = msg.substring(0, 20) + "..";
                    this.cachedWeather = "ERR: " + msg;
                    return null;
                });
    }

    private String decodeWeatherCode(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mainly Clear";
            case 2: return "Partly Cloudy";
            case 3: return "Overcast";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 61: case 63: case 65: return "Rain";
            case 71: case 73: case 75: return "Snow";
            case 95: case 96: case 99: return "Thunderstorm";
            default: return "";
        }
    }
}
