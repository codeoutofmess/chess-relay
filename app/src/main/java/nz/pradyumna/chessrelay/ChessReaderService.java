package nz.pradyumna.chessrelay;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the board in the Chess.com app and sends the position to the website, which works out the moves.
 *
 * Chess.com labels every square for screen readers ("White Rook on h1", "g1, empty"), which gives the exact position
 * and, from the order the squares are listed in, which way round the board is (so which colour I'm playing). Only
 * screens with a game clock count, so puzzles and old games aren't sent.
 *
 * Android tells this service whenever a window changes in any app, but it only looks inside Chess.com; for other apps
 * it just notes that Chess.com is no longer in front (and tells the website "done"). Nothing else is read or sent.
 */
public class ChessReaderService extends AccessibilityService {
    private static final String CHESS = "com.chess";
    private static final long SCAN_DELAY_MS = 600; // the clock ticks every second; read at most this often

    private static final Pattern SQUARE = Pattern.compile(
            "(White|Black) (King|Queen|Rook|Bishop|Knight|Pawn) on ([a-h][1-8])|\\b([a-h][1-8]),? empty");
    private static final Pattern CLOCK = Pattern.compile("(^|\\s)\\d{1,2}:\\d{2}(\\.\\d)?(\\s|$)");
    // What Chess.com shows once a game has finished (resigned, abandoned, timed out, mated...). The board and clocks stay
    // on screen, so without this the game would look live until I left the app. Mid-game buttons like "Offer Draw" or
    // "Abort" don't match.
    private static final Pattern OVER = Pattern.compile(
            "\\b(Rematch|Game Review|Game Over|Aborted|Abandoned|You Won|You Lost)\\b"
                    + "|\\bby (resignation|abandonment|checkmate|timeout|time|agreement|stalemate|repetition|insufficient material)"
                    + "|(^|\\s)(1-0|0-1|½-½)(\\s|$)",
            Pattern.CASE_INSENSITIVE);
    private static final long GONE_MS = 5_000; // no board for this long (back to Chess.com's home screen): the game's over

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean scanQueued;
    private boolean inChess;
    private String seenOnce; // a position must show on two reads in a row (not mid-animation) before it's sent
    private String sent;
    private boolean ended; // "done" already sent for the game on screen
    private long goneSince; // when the board went off screen, 0 while it's showing

    /** A position read off the screen: the piece part of a FEN, and the colour at the bottom of the board. */
    static final class Board {
        final String fen, color;
        Board(String fen, String color) { this.fen = fen; this.color = color; }
        @Override public String toString() { return fen + " (you're " + color + ")"; }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        CharSequence pkg = e.getPackageName();
        if (pkg == null) return;
        if (!CHESS.contentEquals(pkg)) {
            // Another app came to the front: the game is over or paused. Keyboards and the status bar don't count.
            if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && inChess && !overlay(pkg.toString())) {
                inChess = false;
                if (sent != null) Relay.post(this, "{\"status\":\"done\"}", null);
                sent = seenOnce = null;
                ended = false;
                goneSince = 0;
            }
            return;
        }
        inChess = true;
        if (!scanQueued) {
            scanQueued = true;
            handler.postDelayed(this::scan, SCAN_DELAY_MS);
        }
    }

    private static boolean overlay(String pkg) {
        return pkg.equals("com.android.systemui") || pkg.contains("inputmethod") || pkg.contains("keyboard")
                || pkg.contains("honeyboard");
    }

    @Override
    public void onInterrupt() {}

    private void scan() {
        scanQueued = false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null || !CHESS.contentEquals(root.getPackageName())) return;
        List<String> texts = new ArrayList<>();
        collect(root, texts, 0);
        String screen = String.join(" | ", texts);
        Relay.lastSeen = screen.length() > 4000 ? screen.substring(0, 4000) + "…" : screen;

        Board board = read(screen);
        boolean over = board != null && over(screen);
        if (board == null || over) {
            Relay.lastFound = over ? "Game over: " + board : "No game board with a clock on this screen.";
            seenOnce = null;
            long now = System.currentTimeMillis();
            if (!over && goneSince == 0) {
                goneSince = now;
                if (sent != null && !ended) handler.postDelayed(this::scan, GONE_MS); // look again even if the screen stays still
            }
            // The final position stays as `sent`, so it isn't sent again (which would bring the board back for 45 minutes).
            if (sent != null && !ended && (over || now - goneSince >= GONE_MS)) {
                ended = true;
                Relay.post(this, "{\"status\":\"done\"}", null);
            }
            return;
        }
        goneSince = 0;
        Relay.lastFound = board.toString();
        String key = board.fen + board.color;
        if (key.equals(sent)) return;
        if (!key.equals(seenOnce)) {
            seenOnce = key;
            handler.postDelayed(this::scan, SCAN_DELAY_MS); // check it again shortly, even if nothing else changes
            return;
        }
        sent = key;
        ended = false;
        Relay.post(this, "{\"board\":\"" + board.fen + "\",\"color\":\"" + board.color + "\"}", null);
    }

    /** Every piece of text and every accessibility label on screen, in reading order. */
    private static void collect(AccessibilityNodeInfo node, List<String> texts, int depth) {
        if (node == null || depth > 80) return;
        for (CharSequence s : new CharSequence[] {node.getText(), node.getContentDescription()}) {
            if (s != null && s.length() > 0) texts.add(s.toString().trim());
        }
        for (int i = 0; i < node.getChildCount(); i++) collect(node.getChild(i), texts, depth + 1);
    }

    /** True once the screen shows the game has finished. */
    static boolean over(String screen) {
        return OVER.matcher(screen).find();
    }

    /** The position, if the screen shows a whole board (all 64 squares) and a game clock; otherwise null. */
    static Board read(String screen) {
        if (!CLOCK.matcher(screen).find()) return null;
        Map<String, Character> squares = new LinkedHashMap<>();
        Matcher m = SQUARE.matcher(screen);
        while (m.find()) {
            if (m.group(3) != null) {
                char p = "King".equals(m.group(2)) ? 'k' : "Knight".equals(m.group(2)) ? 'n' : Character.toLowerCase(m.group(2).charAt(0));
                squares.putIfAbsent(m.group(3), "White".equals(m.group(1)) ? Character.toUpperCase(p) : p);
            } else {
                squares.putIfAbsent(m.group(4), '.');
            }
        }
        if (squares.size() != 64) return null;
        // The squares are listed from the top left: a8 there means white is at the bottom, h1 means black is.
        String first = squares.keySet().iterator().next();
        String color = first.equals("h1") ? "black" : "white";
        StringBuilder fen = new StringBuilder();
        for (int rank = 8; rank >= 1; rank--) {
            int gap = 0;
            for (char file = 'a'; file <= 'h'; file++) {
                char p = squares.get("" + file + rank);
                if (p == '.') { gap++; continue; }
                if (gap > 0) { fen.append(gap); gap = 0; }
                fen.append(p);
            }
            if (gap > 0) fen.append(gap);
            if (rank > 1) fen.append('/');
        }
        return new Board(fen.toString(), color);
    }
}
