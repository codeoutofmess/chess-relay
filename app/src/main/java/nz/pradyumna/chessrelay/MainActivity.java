package nz.pradyumna.chessrelay;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** One screen: where to send, the password, a switch-on shortcut, a test button, and what the reader last saw. */
public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status, found, result, seen;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        int pad = dp(20);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad * 2, pad, pad * 2);

        col.addView(heading("Chess Relay"));
        col.addView(text("Sends the moves of your Chess.com games to your website, so visitors can watch live."));

        status = text("");
        status.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(space(), lp());
        col.addView(status);
        Button turnOn = button("Open Accessibility settings");
        turnOn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        col.addView(turnOn);

        col.addView(space());
        col.addView(label("Website address"));
        EditText url = new EditText(this);
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(Relay.url(this));
        col.addView(url);
        col.addView(label("Password (the same as CHESS_STATUS_SECRET on Vercel)"));
        EditText secret = new EditText(this);
        secret.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        secret.setText(Relay.secret(this));
        col.addView(secret);
        Button save = button("Save");
        save.setOnClickListener(v -> {
            Relay.prefs(this).edit()
                    .putString("url", url.getText().toString().trim())
                    .putString("secret", secret.getText().toString().trim())
                    .apply();
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        });
        col.addView(save);

        Button test = button("Send a test move (shows e4 on the website for 2 minutes)");
        test.setOnClickListener(v -> {
            Relay.post(this, "{\"board\":\"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR\",\"color\":\"white\"}", null);
            Relay.post(this, "{\"board\":\"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR\",\"color\":\"white\"}", null);
            Relay.post(this, "{\"status\":\"done\"}", null);
        });
        col.addView(test);

        col.addView(space());
        col.addView(label("What the website last said"));
        result = mono();
        col.addView(result);
        col.addView(label("Board found on the Chess.com screen"));
        found = mono();
        col.addView(found);
        col.addView(label("Everything it last read in Chess.com"));
        seen = mono();
        col.addView(seen);
        Button copy = button("Copy what it read");
        copy.setOnClickListener(v -> {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            cm.setPrimaryClip(ClipData.newPlainText("Chess Relay", Relay.lastSeen + "\n\nBoard: " + Relay.lastFound));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        });
        col.addView(copy);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(col);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
    }

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            String on = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            boolean enabled = on != null && on.contains(getPackageName() + "/");
            status.setText(enabled ? "✓ Reading is on." : "Reading is off. Turn on Chess Relay in Accessibility settings.");
            result.setText(Relay.lastResult);
            found.setText(Relay.lastFound);
            seen.setText(Relay.lastSeen);
            handler.postDelayed(this, 1000);
        }
    };

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private LinearLayout.LayoutParams lp() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private TextView heading(String s) {
        TextView t = text(s);
        t.setTextSize(26);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView text(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s);
        t.setTextSize(13);
        t.setPadding(0, dp(14), 0, dp(2));
        return t;
    }

    private TextView mono() {
        TextView t = text("");
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextSize(12);
        t.setTextIsSelectable(true);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    private View space() {
        View v = new View(this);
        v.setMinimumHeight(dp(16));
        return v;
    }
}
