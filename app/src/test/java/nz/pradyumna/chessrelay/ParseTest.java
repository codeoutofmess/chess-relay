package nz.pradyumna.chessrelay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ParseTest {
    /** What the app read from a real Chess.com game (I was black, after 4. b4 Bb6), copied from the phone. */
    private static final String REAL = "Back Chess.com logo Live connection level indicator 2. nf3 Nc6 3. bc4 Bc5 4. b4 Bb6 "
            + "user avatar divine_padhant (1226) 3:00 White Rook on h1 g1, empty f1, empty White King on e1 White Queen on d1 "
            + "White Bishop on c1 White Knight on b1 White Rook on a1 White Pawn on h2 White Pawn on g2 White Pawn on f2 "
            + "e2, empty White Pawn on d2 White Pawn on c2 b2, empty White Pawn on a2 h3, empty g3, empty White Knight on f3 "
            + "e3, empty d3, empty c3, empty b3, empty a3, empty h4, empty g4, empty f4, empty White Pawn on e4 d4, empty "
            + "White Bishop on c4 White Pawn on b4 a4, empty h5, empty g5, empty f5, empty Black Pawn on e5 d5, empty "
            + "c5, empty b5, empty a5, empty h6, empty g6, empty f6, empty e6, empty d6, empty Black Knight on c6 "
            + "Black Bishop on b6 a6, empty Black Pawn on h7 Black Pawn on g7 Black Pawn on f7 e7, empty Black Pawn on d7 "
            + "Black Pawn on c7 Black Pawn on b7 Black Pawn on a7 Black Rook on h8 Black Knight on g8 f8, empty "
            + "Black King on e8 Black Queen on d8 Black Bishop on c8 b8, empty Black Rook on a8 user avatar pradyumna162636 (1141)";

    @Test public void readsTheRealScreen() {
        ChessReaderService.Board b = ChessReaderService.read(REAL);
        assertEquals("r1bqk1nr/pppp1ppp/1bn5/4p3/1PB1P3/5N2/P1PP1PPP/RNBQK2R", b.fen);
        assertEquals("black", b.color);
    }

    @Test public void whiteAtTheBottomWhenListedFromA8() {
        StringBuilder s = new StringBuilder("5:00 | ");
        String back = "RNBQKBNR";
        String[] names = {"Rook", "Knight", "Bishop", "Queen", "King", "Bishop", "Knight", "Rook"};
        for (int rank = 8; rank >= 1; rank--)
            for (char f = 'a'; f <= 'h'; f++) {
                String sq = "" + f + rank;
                String side = rank >= 7 ? "Black" : "White";
                if (rank == 8 || rank == 1) s.append(side).append(' ').append(names[f - 'a']).append(" on ").append(sq);
                else if (rank == 7 || rank == 2) s.append(side).append(" Pawn on ").append(sq);
                else s.append(sq).append(", empty");
                s.append(" | ");
            }
        ChessReaderService.Board b = ChessReaderService.read(s.toString());
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/" + back, b.fen);
        assertEquals("white", b.color);
    }

    @Test public void noClockMeansNotALiveGame() {
        assertNull(ChessReaderService.read(REAL.replace("3:00", "")));
    }

    @Test public void halfABoardIsIgnored() {
        assertNull(ChessReaderService.read(REAL.substring(0, REAL.indexOf("White Pawn on e4"))));
    }

    @Test public void aGameInProgressIsNotOver() {
        assertFalse(ChessReaderService.over(REAL + " | Options | Offer Draw | Abort | Resign | Chat"));
    }

    @Test public void theGameOverScreenIsSeen() {
        assertTrue(ChessReaderService.over(REAL + " | You Won! | by abandonment | Rematch"));
        assertTrue(ChessReaderService.over(REAL + " | White won by resignation"));
        assertTrue(ChessReaderService.over(REAL + " | 30. qf7+ | 0-1 | Game Review"));
    }
}
