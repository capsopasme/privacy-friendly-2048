/*
 This file is part of Privacy Friendly 2048. This app implements the functions of the
 game 2048 in a privacy friendly version.

 Privacy Friendly 2048 is free software:
 you can redistribute it and/or modify it under the terms of the
 GNU General Public License as published by the Free Software Foundation,
 either version 3 of the License, or any later version.

 Privacy Friendly App Example is distributed in the hope
 that it will be useful, but WITHOUT ANY WARRANTY; without even
 the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Privacy Friendly App Example. If not, see <http://www.gnu.org/licenses/>.
 */

package org.secuso.privacyfriendly2048.activities;

import static org.secuso.privacyfriendly2048.helpers.ThemeResolverKt.GetColorInt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.secuso.privacyfriendly2048.R;
import org.secuso.privacyfriendly2048.activities.helper.BaseActivityWithoutNavBar;
import org.secuso.privacyfriendly2048.activities.helper.GameState;
import org.secuso.privacyfriendly2048.activities.helper.GameStatistics;
import org.secuso.privacyfriendly2048.activities.helper.Gestures;
import org.secuso.privacyfriendly2048.helpers.SpringInterpolator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.Calendar;

/**
 * This activity contains the entire game and draws the game field depending on the selected mode and the screen size.
 * Also the Intent influences, when a new game is generated or a previous game status is loaded.
 * Therefore a two dimensional field named elements is filled with instances of the class Element depending on the selected mode (4x4, 5x5, 6x6, 7x7).
 * The function of the two buttons restart and undo are also implemented.
 * With the help of the class Gesture the user inputs are recognized and the corresponding method is retrieved.
 * This method contains the important game logic for 2048.
 * With loops the values of the elements will be added and the whole elements will be moved in the selected direction.
 * After each move (only if something has been moved) a new element appears on the game field with the help of the method addNumber().
 * This new element is placed randomly on a free spot of the game field.
 * If the activity is paused or stopped, the game status is saved in the GameState and the GameStatistics will be updated.
 *
 * @author Julian Wadephul and Saskia Jacob
 * @version 20180910
 */

@SuppressWarnings("StringConcatenationInLoop")
public class GameActivity extends BaseActivityWithoutNavBar {
    public static int n = 4;
    public TextView textFieldPoints;
    public TextView textFieldRecord;
    public int numberFieldSize = 0;
    // views belong to this activity instance; static fields would leak the activity on rotation
    Element[][] elements = null;
    /** Boards before the last moves, oldest first, for undo. Slots past undoCount are spare buffers. */
    private int[][][] undoBoards = new int[GameState.UNDO_LIMIT][][];
    private final int[] undoPointsStack = new int[GameState.UNDO_LIMIT];
    private int undoCount = 0;
    /** Scratch buffer the board is copied into before a move; becomes an undo step if tiles moved. */
    private int[][] scratchNumbers = null;
    private Element[] emptyFields = null;
    static GameState gameState = null;

    RelativeLayout number_field;
    RelativeLayout number_field_background;
    RelativeLayout touch_field;
    ImageButton restartButton;
    ImageButton undoButton;
    public static int points = 0;
    public static int last_points = 0;
    public static long record = 0;

    // tile motion lives in TileAnimations; new tiles appear right after the slide has landed
    public static long MOVINGSPEED = TileAnimations.SLIDE_MS;
    public static long SPAWNDELAY = TileAnimations.SLIDE_MS + 16;

    private static final int LEFT = 0;
    private static final int RIGHT = 1;
    private static final int UP = 2;
    private static final int DOWN = 3;

    // UI micro-interactions (score counter, undo button, button press)
    private static final TimeInterpolator EASE_OUT = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final TimeInterpolator EASE_IN = new PathInterpolator(0.4f, 0f, 1f, 1f);
    private static final TimeInterpolator PRESS_RELEASE = new SpringInterpolator(0.55, 0.32, 420);
    private static final TimeInterpolator SCORE_POP = new SpringInterpolator.Pulse(0.5, 0.28, 300);
    private long lastMergeHaptic = 0;
    private boolean undoShown = false;

    public static boolean moved = false;
    public static boolean firstTime = true;
    public static boolean newGame;
    public boolean won2048 = false;
    public static boolean gameOver = false;
    public static boolean createNewGame = true;
    public static boolean undo = false;
    public static boolean animationActivated = true;
    public static boolean saveState = true;

    public final int WINTHRESHOLD = 2048;
    public final double PROPABILITYFORTWO = 0.9;
    Gestures swipeListener;


    static String filename;

    SharedPreferences sharedPref;

    GameStatistics gameStatistics = new GameStatistics(n);
    public static long startingTime;
    public int highestNumber;

    /** The board is built once, when the window first has its size, not on every focus change. */
    private boolean boardBuilt = false;
    private Counter pointsCounter;
    private Counter recordCounter;


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_settings);
        item.setVisible(false);
        super.onPrepareOptionsMenu(menu);
        return true;
    }

    @Override
    public void onPause() {
        Log.i("lifecycle", "pause");
        save();

        super.onPause();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        saveState = true;

        if (savedInstanceState == null) {
            Intent intent = getIntent();
            if (firstTime && intent.getBooleanExtra("new", true)) {
                createNewGame = true;
                firstTime = false;
            }
        }

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        animationActivated = sharedPref.getBoolean("pref_animationActivated", true);

        // the settings switch is stored under this key (it used to be read as "settings_display",
        // which never exists, so the screen was always kept on whatever the switch said)
        keepScreenOn = sharedPref.getBoolean("switch_preference_1", true);
        if (animationActivated)
            highModeId = findHighestRefreshMode();
        setContentView(R.layout.activity_game);

        initResources();


    }

    @Override
    public void onResume() {
        super.onResume();
        onUserInteraction();
    }

    @Override
    protected void onStop() {
        idleHandler.removeCallbacks(dropRefreshRate);
        idleHandler.removeCallbacks(releaseScreen);
        dropRefreshRate.run();
        releaseScreen.run();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        idleHandler.removeCallbacksAndMessages(null);
        settleAll();
        super.onDestroy();
    }

    // ---- power: high refresh rate and keep-screen-on only while the game is actually played ----

    /** How long the high refresh rate is held after the last touch / move (covers every animation). */
    private static final long HIGH_REFRESH_HOLD_MS = 1500;
    /** The screen is kept on only this long after the last touch; then the system timeout applies. */
    private static final long KEEP_SCREEN_ON_IDLE_MS = 5 * 60 * 1000;

    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private boolean keepScreenOn = true;
    private boolean screenHeld = false;
    /** Display mode with the highest refresh rate at the current resolution, 0 if not worth asking. */
    private int highModeId = 0;
    private boolean refreshBoosted = false;

    private final Runnable dropRefreshRate = new Runnable() {
        @Override
        public void run() {
            setPreferredDisplayMode(0);
        }
    };

    private final Runnable releaseScreen = new Runnable() {
        @Override
        public void run() {
            if (!screenHeld) return;
            screenHeld = false;
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    };

    /** Any touch or key press anywhere in the activity, called before it is dispatched. */
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (keepScreenOn) {
            if (!screenHeld) {
                screenHeld = true;
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            idleHandler.removeCallbacks(releaseScreen);
            idleHandler.postDelayed(releaseScreen, KEEP_SCREEN_ON_IDLE_MS);
        }
        holdHighRefreshRate();
    }

    /**
     * Switches to the panel's fastest mode (e.g. 120 Hz) when a touch begins, so the switch has
     * happened before the swipe is recognised and the springs get every frame; some ROMs cap
     * unlisted apps at 60 Hz unless the window asks. Once the board is still again the request is
     * withdrawn and the system may drop to a lower rate. Pinning 120 Hz while the player is only
     * looking at the board was the largest avoidable drain of the game screen.
     */
    private void holdHighRefreshRate() {
        if (highModeId == 0) return;
        if (!refreshBoosted) setPreferredDisplayMode(highModeId);
        idleHandler.removeCallbacks(dropRefreshRate);
        idleHandler.postDelayed(dropRefreshRate, HIGH_REFRESH_HOLD_MS);
    }

    private void setPreferredDisplayMode(int modeId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        boolean boost = modeId != 0;
        if (boost == refreshBoosted) return;
        try {
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.preferredDisplayModeId = modeId;
            getWindow().setAttributes(params);
            refreshBoosted = boost;
        } catch (RuntimeException e) {
            Log.w("display", "could not change refresh rate", e);
            highModeId = 0;
        }
    }

    private int findHighestRefreshMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return 0;
        try {
            Display display = getWindowManager().getDefaultDisplay();
            Display.Mode current = display.getMode();
            Display.Mode best = current;
            for (Display.Mode mode : display.getSupportedModes()) {
                if (mode.getPhysicalWidth() == current.getPhysicalWidth()
                        && mode.getPhysicalHeight() == current.getPhysicalHeight()
                        && mode.getRefreshRate() > best.getRefreshRate()) {
                    best = mode;
                }
            }
            return best.getRefreshRate() > 61f ? best.getModeId() : 0;
        } catch (RuntimeException e) {
            Log.w("display", "could not read display modes", e);
            return 0;
        }
    }

    private static Method setRequestedFrameRate;
    private static float frameRateHigh;
    private static boolean frameRateResolved = false;

    /**
     * Android 15+ lowers the refresh rate for small views that animate unless they ask for more.
     * Tiles vote for the high category while they move; an idle board votes for nothing.
     * Looked up reflectively so the app still builds against SDK 34.
     */
    private static void requestHighFrameRate(View v) {
        if (Build.VERSION.SDK_INT < 35) return;
        if (!frameRateResolved) {
            frameRateResolved = true;
            try {
                frameRateHigh = View.class.getField("REQUESTED_FRAME_RATE_CATEGORY_HIGH").getFloat(null);
                setRequestedFrameRate = View.class.getMethod("setRequestedFrameRate", float.class);
            } catch (Throwable t) {
                setRequestedFrameRate = null;
            }
        }
        if (setRequestedFrameRate == null) return;
        try {
            setRequestedFrameRate.invoke(v, frameRateHigh);
        } catch (Throwable t) {
            setRequestedFrameRate = null;
        }
    }

    /** Remembers the board before a move; the oldest step is dropped once UNDO_LIMIT are stored. */
    private void pushUndo(int[][] board, int pts) {
        if (undoCount == GameState.UNDO_LIMIT) {
            int[][] oldest = undoBoards[0];
            System.arraycopy(undoBoards, 1, undoBoards, 0, GameState.UNDO_LIMIT - 1);
            System.arraycopy(undoPointsStack, 1, undoPointsStack, 0, GameState.UNDO_LIMIT - 1);
            undoCount--;
            undoBoards[undoCount] = oldest; // spare, handed back as scratch below
        }
        int[][] spare = undoBoards[undoCount];
        undoBoards[undoCount] = board;
        undoPointsStack[undoCount] = pts;
        undoCount++;
        scratchNumbers = spare != null && spare.length == n ? spare : new int[n][n];
        last_points = pts;
        undo = true;
    }

    private void handleUndo() {
        if (undoCount > 0 && elements != null) {
            undoCount--;
            int[][] board = undoBoards[undoCount]; // stays in its slot as a spare buffer
            gameStatistics.undo();
            points = undoPointsStack[undoCount];
            pointsCounter.set(points, false);
            for (int i = 0; i < elements.length; i++) {
                for (int j = 0; j < elements[i].length; j++) {
                    Element e = elements[i][j];
                    TileAnimations.settle(e);
                    e.setNumber(board[i][j]);
                    e.setX(e.dPosX);
                    e.setY(e.dPosY);
                    e.drawItem();
                }
            }
            undo = undoCount > 0;
            last_points = undo ? undoPointsStack[undoCount - 1] : points;
            gameOver = false;
            updateGameState();
        }
        undo = undoCount > 0;
        setUndoVisible(undo);
    }

    public void initResources() {
        number_field = (RelativeLayout) findViewById(R.id.number_field);
        number_field_background = (RelativeLayout) findViewById(R.id.number_field_background);
        touch_field = (RelativeLayout) findViewById(R.id.touch_field);
        textFieldPoints = (TextView) findViewById(R.id.points);
        textFieldRecord = (TextView) findViewById(R.id.record);
        pointsCounter = new Counter(textFieldPoints);
        recordCounter = new Counter(textFieldRecord);
        // tiles are positioned by absolute x/y, never mirrored
        number_field.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        restartButton = (ImageButton) findViewById(R.id.restartButton);
        restartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveStatisticsToFile(gameStatistics);
                createNewGame();
            }
        });
        undoButton = (ImageButton) findViewById(R.id.undoButton);

        undoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleUndo();
            }
        });
        restartButton.setOnTouchListener(pressFeedback);
        undoButton.setOnTouchListener(pressFeedback);

        startingTime = Calendar.getInstance().getTimeInMillis();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        createNewGame = false;
        super.onConfigurationChanged(newConfig);
    }

    public void save() {
        Log.i("saving", "save");
        if (!createNewGame)
            saveStateToFile(gameState);
        gameStatistics.addTimePlayed(Calendar.getInstance().getTimeInMillis() - startingTime);
        startingTime = Calendar.getInstance().getTimeInMillis();
        saveStatisticsToFile(gameStatistics);
        firstTime = true;
    }

    @Override
    public void onBackPressed() {
        save();

        super.onBackPressed();

    }

    public void createNewGame() {
        createNewGame = true;
        getIntent().putExtra("new", true);
        initialize();

    }

    protected void start() {
        Log.i("activity", "start");
        saveState = true;
        android.view.ViewGroup.LayoutParams lp = number_field.getLayoutParams();

        //setting squared Number Field
        if (number_field.getHeight() > number_field.getWidth())
            lp.height = number_field.getWidth();
        else
            lp.width = number_field.getHeight();
        number_field.setLayoutParams(lp);
        number_field_background.setLayoutParams(lp);

        initialize();
        setListener();
        if (newGame) {
            moved = true;
            addNumber();
        }
        newGame = false;

    }

    public void initializeState() {
        points = 0;
        Intent intent = getIntent();
        n = intent.getIntExtra("n", 4);
        newGame = intent.getBooleanExtra("new", true);
        filename = intent.getStringExtra("filename");
        undo = intent.getBooleanExtra("undo", false);
        if (!newGame) {
            gameState = readStateFromFile();
            points = gameState.points;
            last_points = gameState.last_points;
        } else {
            gameState = new GameState(n);
            newGame = true;
        }
        saveState = true;


    }

    public void drawAllElements(Element[][] e) {
        for (Element[] i : e) {
            for (Element j : i) {
                j.drawItem();
            }
        }
    }

    /** Copies the board into the saved state in place; runs after every move. */
    public void updateGameState() {
        GameState gs = gameState;
        int size = n * n;
        if (gs == null || gs.numbers == null || gs.numbers.length != size)
            gs = new GameState(n);
        if (gs.history == null || gs.history.length != GameState.UNDO_LIMIT)
            gs.history = new int[GameState.UNDO_LIMIT][];
        if (gs.historyPoints == null || gs.historyPoints.length != GameState.UNDO_LIMIT)
            gs.historyPoints = new int[GameState.UNDO_LIMIT];
        int c = 0;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                gs.numbers[c++] = elements[i][j].number;
        // copied in place: the arrays are reused from move to move
        for (int k = 0; k < undoCount; k++) {
            int[] flat = gs.history[k];
            if (flat == null || flat.length != size)
                gs.history[k] = flat = new int[size];
            int[][] board = undoBoards[k];
            c = 0;
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    flat[c++] = board[i][j];
            gs.historyPoints[k] = undoPointsStack[k];
        }
        gs.historyCount = undoCount;
        // single step for older versions reading this save
        if (undoCount > 0) {
            gs.last_numbers = gs.history[undoCount - 1];
            gs.last_points = undoPointsStack[undoCount - 1];
        } else {
            gs.last_numbers = null;
            gs.last_points = points;
        }
        gs.n = n;
        gs.points = points;
        gs.undo = undoCount > 0;
        gameState = gs;
        updateHighestNumber();
        check2048();

    }

    public void initialize() {
        Log.i("activity", "initialize");
        if (getIntent().getIntExtra("n", 4) != n || createNewGame) {
            initializeState();

        }
        gameStatistics = readStatisticsFromFile();
        record = gameStatistics.getRecord();
        last_points = gameState.last_points;
        createNewGame = false;
        won2048 = false;
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int abstand = (10 * metrics.densityDpi) / DisplayMetrics.DENSITY_DEFAULT;
        numberFieldSize = number_field.getWidth();
        if (numberFieldSize > number_field.getHeight())
            numberFieldSize = number_field.getHeight();
        int number_size = (numberFieldSize - abstand) / n - abstand;
        // continuous-looking corners that scale with the tile, like iOS app icons
        float tileRadius = number_size * 0.16f;

        recordCounter.set(record, false);
        pointsCounter.set(points, false);
        restoreUndoHistory();
        undoButton.animate().cancel();
        undoButton.setAlpha(1f);
        undoButton.setScaleX(1f);
        undoButton.setScaleY(1f);
        undoShown = undo;
        if (undo)
            undoButton.setVisibility(View.VISIBLE);
        else
            undoButton.setVisibility(View.INVISIBLE);

        TileStyle style = new TileStyle(this, number_size, tileRadius);
        style.prewarm();
        number_field_background.setBackground(new BoardBackground(
                GetColorInt(this, R.attr.boardBackground), tileRadius + abstand * 0.6f,
                style.emptyColor(), n, abstand, number_size, tileRadius));

        settleAll();
        number_field_background.removeAllViews();
        number_field.removeAllViews();
        elements = new Element[n][n];
        scratchNumbers = new int[n][n];
        emptyFields = new Element[n * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                Element e = new Element(this, style);
                e.onMerge = mergeFeedback;
                e.setNumber(gameState.getNumber(i, j));
                int x = abstand + j * (number_size + abstand);
                int y = abstand + i * (number_size + abstand);
                e.setDPosition(x, y);
                e.setLayoutParams(new RelativeLayout.LayoutParams(number_size, number_size));
                e.setX(x);
                e.setY(y);
                e.drawItem();
                if (e.getNumber() >= WINTHRESHOLD)
                    won2048 = true;
                requestHighFrameRate(e);
                elements[i][j] = e;
                number_field.addView(e);
            }
        }
        if (newGame) {
            moved = true;
            addNumber();
            moved = true;
            addNumber();
            newGame = false;
        }
    }

    /** Loads the undo steps of the current game (saved or kept across rotation) into the stack. */
    private void restoreUndoHistory() {
        undoBoards = new int[GameState.UNDO_LIMIT][][];
        undoCount = 0;
        GameState gs = gameState;
        int steps = (gs == null || gs.n != n || newGame) ? 0 : gs.undoSteps();
        // a longer saved history keeps its newest steps
        int first = Math.max(0, steps - GameState.UNDO_LIMIT);
        for (int k = first; k < steps; k++) {
            int[] flat = gs.undoBoard(k);
            int[][] board = new int[n][n];
            int c = 0;
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    board[i][j] = flat[c++];
            undoBoards[undoCount] = board;
            undoPointsStack[undoCount] = gs.undoPoints(k);
            undoCount++;
        }
        undo = undoCount > 0;
        last_points = undo ? undoPointsStack[undoCount - 1] : points;
    }

    private void settleAll() {
        if (elements == null) return;
        for (Element[] row : elements)
            for (Element e : row)
                if (e != null) TileAnimations.settle(e);
    }

    /**
     * e1 moves into the slot of e2. When it absorbs e2 ({@code absorbedValue != 0}), e2 slides to
     * the meeting point underneath e1 and disappears when e1 lands.
     */
    public void switchElementPositions(Element e1, Element e2, int absorbedValue) {
        int i = e1.getdPosX();
        int j = e1.getdPosY();

        e1.animateMoving = true;
        e1.setDPosition(e2.getdPosX(), e2.getdPosY());
        e2.animateMoving = false;
        e2.setDPosition(i, j);
        if (absorbedValue != 0) {
            e2.victimOf = e1;
            e2.victimShow = absorbedValue;
        }
    }

    // ---- board access along a swipe: line k, position p counted from the edge tiles move to ----

    private Element at(int dir, int k, int p) {
        switch (dir) {
            case LEFT:
                return elements[k][p];
            case RIGHT:
                return elements[k][n - 1 - p];
            case UP:
                return elements[p][k];
            default:
                return elements[n - 1 - p][k];
        }
    }

    private void put(int dir, int k, int p, Element e) {
        switch (dir) {
            case LEFT:
                elements[k][p] = e;
                break;
            case RIGHT:
                elements[k][n - 1 - p] = e;
                break;
            case UP:
                elements[p][k] = e;
                break;
            default:
                elements[n - 1 - p][k] = e;
                break;
        }
    }

    /**
     * The game logic for one swipe (the same algorithm the four handleSwipe* methods used to
     * contain, written once): a merge pass followed by a compaction pass along every line.
     */
    private void handleSwipe(int dir) {
        if (elements == null)
            return;
        if (animationActivated) {
            // leftovers of the previous move (hidden jumps) happen now, before slots change hands
            for (Element[] row : elements)
                for (Element e : row)
                    TileAnimations.flush(e);
        }
        int[][] before = scratchNumbers;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                before[i][j] = elements[i][j].number;
        int temp_points = points;
        moved = false;

        for (int k = 0; k < n; k++) {
            int sNum = at(dir, k, 0).number;
            int sP = 0;
            for (int p = 1; p < n; p++) {
                Element e = at(dir, k, p);
                if (e.number != 0 && (sNum == 0 || sNum == e.number)) {
                    moved = true;
                    Element t = at(dir, k, sP);
                    e.setNumber(sNum + e.number);
                    t.setNumber(0);
                    switchElementPositions(e, t, sNum);
                    put(dir, k, p, t);
                    put(dir, k, sP, e);
                    if (sNum != 0) {
                        points += e.number;
                        sP++;
                    }
                    p = sP;
                    sNum = at(dir, k, p).number;
                } else if (e.number != 0) {
                    sNum = e.number;
                    sP = p;
                }
            }
        }
        for (int k = 0; k < n; k++) {
            int sNum = at(dir, k, 0).number;
            int sP = 0;
            for (int p = 1; p < n; p++) {
                Element e = at(dir, k, p);
                if (e.number != 0 && sNum == 0) {
                    moved = true;
                    Element t = at(dir, k, sP);
                    t.setNumber(0);
                    switchElementPositions(e, t, 0);
                    put(dir, k, p, t);
                    put(dir, k, sP, e);
                    p = sP;
                    sNum = at(dir, k, p).number;
                } else if (sNum != 0) {
                    sNum = at(dir, k, p).number;
                    sP = p;
                }
            }
        }

        if (!moved)
            return;

        gameStatistics.addMoves(1);
        pushUndo(before, temp_points);
        setUndoVisible(true);
        holdHighRefreshRate(); // restart the hold from the move itself, for slow swipes
        switch (dir) {
            case LEFT:
                gameStatistics.moveL();
                break;
            case RIGHT:
                gameStatistics.moveR();
                break;
            case UP:
                gameStatistics.moveT();
                break;
            default:
                gameStatistics.moveD();
                break;
        }
        addNumber();
        setDPositions(animationActivated);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                handleSwipe(UP);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                handleSwipe(RIGHT);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                handleSwipe(LEFT);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                handleSwipe(DOWN);
                return true;
            case KeyEvent.KEYCODE_Z:
                if (event.isCtrlPressed()) {
                    handleUndo();
                }
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void setListener() {
        // tiles and the board are not clickable, so every touch on the play area reaches touch_field
        swipeListener = new Gestures(this) {
            public boolean onSwipeTop() {
                handleSwipe(UP);
                return true;
            }

            public boolean onSwipeRight() {
                handleSwipe(RIGHT);
                return true;
            }

            public boolean onSwipeLeft() {
                handleSwipe(LEFT);
                return true;
            }

            public boolean onSwipeBottom() {
                handleSwipe(DOWN);
                return true;
            }
        };
        touch_field.setOnTouchListener(swipeListener);
    }

    public String display(Element[][] e) {
        String result = "\n";
        for (int i = 0; i < e.length; i++) {
            for (int j = 0; j < e[i].length; j++)
                result = result + " " + elements[i][j].number; //+ " "+elements[i][j];
            result = result + "\n";
        }
        result += "\n";
        for (int i = 0; i < e.length; i++) {
            for (int j = 0; j < e[i].length; j++)
                result = result + " (" + elements[i][j].getX() + " , " + elements[i][j].getY() + ")" + " v:" + elements[i][j].getVisibility();//+" "+elements[i][j];
            result = result + "\n";
        }
        return result;
    }

    public void updateHighestNumber() {
        for (int i = 0; i < elements.length; i++) {
            for (int j = 0; j < elements[i].length; j++) {
                if (highestNumber < elements[i][j].number) {
                    highestNumber = elements[i][j].number;
                    gameStatistics.setHighestNumber(highestNumber);
                }
            }
        }
    }

    public void check2048() {
        if (won2048)
            return;
        for (int i = 0; i < elements.length; i++) {
            for (int j = 0; j < elements[i].length; j++) {
                if (elements[i][j].number == WINTHRESHOLD) {
                    won2048 = true;
                    saveStatisticsToFile(gameStatistics);
                    //MESSAGE
                    new AlertDialog.Builder(this)
                            .setTitle((this.getResources().getString(R.string.Titel_V_Message)))
                            .setMessage((this.getResources().getString(R.string.Winning_Message)))
                            .setNegativeButton((this.getResources().getString(R.string.No_Message)), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    onBackPressed();

                                }
                            })
                            .setPositiveButton((this.getResources().getString(R.string.Yes_Message)), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .setCancelable(false)
                            .create().show();
                    return; // one dialog, even if two 2048 tiles appear in the same move
                }
            }
        }
    }


    public void setDPositions(boolean animation) {
        for (Element[] i : elements) {
            for (Element j : i) {
                boolean off = j.dPosX != j.getX() || j.dPosY != j.getY();
                if (!animation) {
                    if (off || j.number != j.dNumber || j.victimOf != null) {
                        TileAnimations.settle(j);
                        j.setX(j.dPosX);
                        j.setY(j.dPosY);
                        j.drawItem();
                    }
                } else if (j.animateMoving) {
                    // a changed number means this tile absorbs another one when it lands
                    if (off)
                        TileAnimations.slide(j, j.number != j.dNumber);
                } else if (j.victimOf != null) {
                    TileAnimations.absorb(j);
                } else if (off) {
                    // emptied tiles stay put (hidden) until the sliding tiles have arrived
                    TileAnimations.jumpAfterSlide(j);
                }
            }
        }
    }

    /** A light tick when tiles merge, at most once per move. Respects the system haptics setting. */
    private final Runnable mergeFeedback = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            if (now - lastMergeHaptic < 80)
                return;
            lastMergeHaptic = now;
            if (number_field != null)
                number_field.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    };

    /** Restart / undo buttons sink a little under the finger and spring back on release. */
    private final View.OnTouchListener pressFeedback = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.86f).scaleY(0.86f).setDuration(120).setStartDelay(0)
                            .setInterpolator(EASE_OUT).setListener(null).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(420).setStartDelay(0)
                            .setInterpolator(PRESS_RELEASE).setListener(null).start();
                    break;
            }
            return false; // let the click go through
        }
    };

    /** Fades and scales the undo button in or out instead of popping its visibility. */
    private void setUndoVisible(boolean visible) {
        if (undoButton == null)
            return;
        if (!animationActivated) {
            undoButton.animate().cancel();
            undoButton.setAlpha(1f);
            undoButton.setScaleX(1f);
            undoButton.setScaleY(1f);
            undoButton.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            undoShown = visible;
            return;
        }
        if (visible == undoShown && (!visible || undoButton.getVisibility() == View.VISIBLE))
            return;
        undoShown = visible;
        if (visible) {
            if (undoButton.getVisibility() != View.VISIBLE) {
                undoButton.setAlpha(0f);
                undoButton.setScaleX(0.6f);
                undoButton.setScaleY(0.6f);
                undoButton.setVisibility(View.VISIBLE);
            }
            undoButton.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(320)
                    .setInterpolator(EASE_OUT).setListener(null).start();
        } else {
            undoButton.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setStartDelay(0).setDuration(180)
                    .setInterpolator(EASE_IN).setListener(null)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (!undoShown) {
                                undoButton.setVisibility(View.INVISIBLE);
                                undoButton.setScaleX(1f);
                                undoButton.setScaleY(1f);
                            }
                        }
                    }).start();
        }
    }

    /**
     * A score label. When animated, the number counts up with an ease-out curve and gives a small
     * springy nudge, like the score labels in iOS games. The animators are created once and the
     * text is only touched when the shown number actually changes.
     */
    private final class Counter {
        private final TextView view;
        private long shown;
        private long from;
        private long target;
        private ValueAnimator count;
        private ObjectAnimator nudge;

        Counter(TextView view) {
            this.view = view;
            long s;
            try {
                s = Long.parseLong(view.getText().toString());
            } catch (NumberFormatException e) {
                s = Long.MIN_VALUE;
            }
            shown = s;
        }

        private void show(long value) {
            if (value == shown)
                return;
            shown = value;
            view.setText(String.valueOf(value));
        }

        void set(long value, boolean animate) {
            if (count != null && count.isStarted())
                count.cancel(); // snaps to the previous target, the new count starts from there
            if (!animate || !animationActivated || shown >= value) {
                show(value);
                return;
            }
            from = shown;
            target = value;
            if (count == null) {
                count = ValueAnimator.ofFloat(0f, 1f);
                count.setDuration(380);
                count.setInterpolator(EASE_OUT);
                count.addUpdateListener(animation -> {
                    float f = (float) animation.getAnimatedValue();
                    show(from + Math.round((target - from) * (double) f));
                });
                count.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        show(target);
                    }
                });
                nudge = ObjectAnimator.ofPropertyValuesHolder(view,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.12f));
                nudge.setDuration(300);
                nudge.setInterpolator(SCORE_POP);
                nudge.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setScaleX(1f);
                        view.setScaleY(1f);
                    }
                });
            }
            count.start();
            if (nudge.isStarted())
                nudge.cancel();
            nudge.start();
        }
    }

    public void addNumber() {

        if (points > record) {
            record = points;
            gameStatistics.setRecord(record);
            recordCounter.set(record, true);
        }
        if (moved) {
            gameOver = false;
            moved = false;
            pointsCounter.set(points, true);
            Element[] empty_fields = emptyFields;
            int counter = 0;
            for (int i = 0; i < elements.length; i++) {
                for (int j = 0; j < elements[i].length; j++) {
                    if (elements[i][j].number == 0) {
                        empty_fields[counter++] = elements[i][j];
                    }
                }
            }
            if (counter > 0) {
                int index = (int) (Math.random() * counter);
                int number = 2;
                if (Math.random() > PROPABILITYFORTWO)
                    number = 4;

                Element spawned = empty_fields[index];
                spawned.setNumber(number);
                if (animationActivated) {
                    // drawn when the spawn starts, so a tile that is still being absorbed
                    // there stays visible until the arriving tile covers it
                    TileAnimations.spawn(spawned, SPAWNDELAY);
                } else {
                    spawned.drawItem();
                }
                if (counter == 1) {
                    gameOver = true;
                    for (int i = 0; i < elements.length; i++) {
                        for (int j = 0; j < elements[i].length; j++) {
                            if ((i + 1 < elements.length && elements[i][j].number == elements[i + 1][j].number) || (j + 1 < elements[i].length && elements[i][j].number == elements[i][j + 1].number)) {
                                gameOver = false;
                            }
                        }
                    }
                }
            }
            java.util.Arrays.fill(empty_fields, null);
            updateGameState();

            if (gameOver) {
                gameOver();
            }
        }
    }

    public void gameOver() {
        Log.i("record", "" + record + ", " + gameStatistics.getRecord());
        saveStatisticsToFile(gameStatistics);
        new AlertDialog.Builder(this)
                .setTitle((this.getResources().getString(R.string.Titel_L_Message, points)))
                .setMessage(this.getResources().getString(R.string.Lost_Message, points))
                .setNegativeButton((this.getResources().getString(R.string.No_Message)), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        createNewGame = true;
                        getIntent().putExtra("new", true);
                        initialize();
                        deleteStateFile();
                        saveState = false;
                        GameActivity.this.onBackPressed();

                    }
                })
                .setPositiveButton((this.getResources().getString(R.string.Yes_Message)), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        createNewGame();
                    }
                })
                .setCancelable(false)
                .create().show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement

        if (id == R.id.action_settings) {
            return true;
        } else if (id == android.R.id.home) {
            save();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Build the board once. Focus also changes for the notification shade, dialogs or the
        // volume panel; rebuilding then used to recreate every tile, interrupt running animations
        // and reload the statistics file over unsaved progress.
        if (hasFocus)
            buildBoardWhenSized();
    }

    private void buildBoardWhenSized() {
        if (boardBuilt || isFinishing())
            return;
        if (number_field.getWidth() <= 0 || number_field.getHeight() <= 0) {
            number_field.post(this::buildBoardWhenSized);
            return;
        }
        boardBuilt = true;
        start();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        save();

    }

    public void saveStateToFile(GameState nS) {
        if (saveState && nS != null) {
            try {
                if (filename == null)
                    filename = "state" + n + ".txt";
                File file = new File(getFilesDir(), filename);
                FileOutputStream fileOut = new FileOutputStream(file);
                ObjectOutputStream out = new ObjectOutputStream(fileOut);
                out.writeObject(nS);
                out.close();
                fileOut.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean deleteStateFile() {
        try {
            if (filename == null)
                filename = "state" + n + ".txt";
            File directory = getFilesDir();
            File f = new File(directory, filename);
            return f.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public GameState readStateFromFile() {
        GameState nS = new GameState(n);
        try {
            File file = new File(getFilesDir(), filename);
            FileInputStream fileIn = new FileInputStream(file);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            nS = (GameState) in.readObject();
            boolean emptyField = true;
            for (int i = 0; i < nS.numbers.length; i++) {
                if (nS.numbers[i] > 0) {
                    emptyField = false;
                    break;
                }
            }
            if (emptyField || nS.n != n) {
                nS = new GameState(n);
                newGame = true;
            }
            in.close();
            fileIn.close();
        } catch (Exception e) {
            newGame = true;
            e.printStackTrace();
        }
        return nS;
    }

    public GameStatistics readStatisticsFromFile() {
        GameStatistics gS = new GameStatistics(n);
        try {
            File file = new File(getFilesDir(), "statistics" + n + ".txt");
            FileInputStream fileIn = new FileInputStream(file);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            gS = (GameStatistics) in.readObject();
            in.close();
            fileIn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return gS;
    }

    public void saveStatisticsToFile(GameStatistics gS) {
        try {
            File file = new File(getFilesDir(), gS.getFilename());
            FileOutputStream fileOut = new FileOutputStream(file);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(gS);
            out.close();
            fileOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
