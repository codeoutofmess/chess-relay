package nz.pradyumna.chessrelay;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Settings, the last things the app saw and did (for the screen in MainActivity), and sending to the website. */
final class Relay {
    static final String DEFAULT_URL = "https://www.pradyumna-design.com/api/chess-status";

    /** What the reader last saw inside Chess.com, the moves it picked out, and what the website last said. */
    static volatile String lastSeen = "Nothing yet. Open a game in the Chess.com app.";
    static volatile String lastFound = "None yet.";
    static volatile String lastResult = "Nothing sent yet.";

    /** One request at a time, in order, off the main thread. */
    private static final ExecutorService NET = Executors.newSingleThreadExecutor();

    interface Reply { void done(int code, String body); }

    private Relay() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("relay", Context.MODE_PRIVATE);
    }

    static String url(Context c) { return prefs(c).getString("url", DEFAULT_URL); }
    static String secret(Context c) { return prefs(c).getString("secret", ""); }

    /** POSTs a JSON body with the password. `reply` (may be null) runs on the network thread. */
    static void post(Context c, String json, Reply reply) {
        final String url = url(c), secret = secret(c);
        NET.execute(() -> {
            int code = -1;
            String body;
            try {
                HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
                http.setConnectTimeout(8000);
                http.setReadTimeout(8000);
                http.setRequestMethod("POST");
                http.setDoOutput(true);
                http.setRequestProperty("Content-Type", "application/json");
                http.setRequestProperty("Authorization", "Bearer " + secret);
                try (OutputStream out = http.getOutputStream()) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
                code = http.getResponseCode();
                InputStream in = code < 400 ? http.getInputStream() : http.getErrorStream();
                body = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
                http.disconnect();
            } catch (Exception e) {
                body = "Couldn't reach the website: " + e.getMessage();
            }
            lastResult = DateFormat.getTimeInstance().format(new Date()) + "  sent " + json + "\n→ " + (code > 0 ? code + " " : "") + body;
            if (reply != null) reply.done(code, body);
        });
    }
}
