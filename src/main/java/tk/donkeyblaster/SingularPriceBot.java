package tk.donkeyblaster;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.object.presence.Status;
import io.github.cdimascio.dotenv.Dotenv;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Timer;
import java.util.TimerTask;

public class SingularPriceBot {
    private static final long GUILD_ID = 696082479752413274L;
    private double lastPrice = 0;
    private Guild guild;
    private GatewayDiscordClient client;
    private double usdCadConversion = 0;

    public SingularPriceBot(String ticker) {
        Timer t = new Timer();
        t.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                setUsdCadConversion();
            }
        }, 0, 300000);

        String token = Dotenv.load().get(ticker);
        DiscordClient discordClient = DiscordClient.create(token);
        Mono<Void> login = discordClient.withGateway(client ->
                client.on(ReadyEvent.class, event -> {
                    System.out.println("Logged in as " + event.getSelf().getUsername());
                    this.client = client;
                    guild = client.getGuildById(Snowflake.of(GUILD_ID)).block();
                    return Mono.empty();
                }));

        WebSocketClient webSocketClient = new WebSocketClient(URI.create("wss://stream.binance.com:9443/ws/" + ticker.toLowerCase() + "usdt@kline_1s")) {
            @Override
            public void onOpen(ServerHandshake handshakeData) {
                System.out.println("Connected to Binance WebSocket " + this.getURI());
            }

            @Override
            public void onMessage(String message) {
                if (guild == null) return;

                JsonObject data = JsonParser.parseString(message).getAsJsonObject();
                double price = data.get("k").getAsJsonObject().get("c").getAsDouble();

                double diff = Math.abs(price - lastPrice);
                if (diff / price < 0.001) return;
                lastPrice = price;

                guild.changeSelfNickname(ticker + " - $" + String.format("%.2f", price)).block();
                client.updatePresence(ClientPresence.of(Status.ONLINE, ClientActivity.custom("CA$" + String.format("%.2f", price*usdCadConversion)))).block();
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("Disconnected from Binance WebSocket");
                System.out.println("Code: " + code + " Reason: " + reason + " Remote: " + remote);
            }

            @Override
            public void onError(Exception ex) {
                System.out.println("Error in Binance WebSocket");
                ex.printStackTrace();
            }
        };
        webSocketClient.connect();
        login.block();
    }

    public void setUsdCadConversion() {
        // Parse JSON response and return 'data'.'rates'.'CAD'
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.coinbase.com/v2/exchange-rates?currency=USD"))
                .GET()
                .build();
        HttpClient client = HttpClient.newHttpClient();
        try {
            String response = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            JsonObject data = JsonParser.parseString(response).getAsJsonObject().get("data").getAsJsonObject();
            JsonObject rates = data.get("rates").getAsJsonObject();
            usdCadConversion = rates.get("CAD").getAsDouble();
            return;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        usdCadConversion = -1;
    }
}
