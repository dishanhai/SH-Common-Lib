// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.performance;

import net.minecraft.network.Connection;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Only accepts recent samples from the current connection and dimension. */
public final class ClientWeatherMonitor {
    public static final ClientWeatherMonitor INSTANCE = new ClientWeatherMonitor();
    private Connection connection;
    private String dimension;
    private WeatherForecast forecast;
    private long lastRequest, receivedAt;

    private ClientWeatherMonitor() {}

    public void tick(Connection active, String currentDimension, Consumer<Connection> request) {
        long now = System.nanoTime();
        boolean changed = connection != active || !java.util.Objects.equals(dimension, currentDimension);
        if (changed) {
            connection = active;
            dimension = currentDimension;
            forecast = null;
        }
        if (active != null && (changed || now - lastRequest >= TimeUnit.SECONDS.toNanos(5))) {
            lastRequest = now;
            request.accept(active);
        }
    }

    public void accept(Connection source, WeatherForecast value) {
        if (source != null && source == connection && value.dimension().equals(dimension)
                && value.event() >= WeatherForecast.UNKNOWN && value.event() <= WeatherForecast.THUNDER_END
                && value.ticks() >= 0) {
            forecast = value;
            receivedAt = System.nanoTime();
        }
    }

    public WeatherForecast current() {
        return forecast != null && System.nanoTime() - receivedAt < TimeUnit.SECONDS.toNanos(12) ? forecast : null;
    }
}
