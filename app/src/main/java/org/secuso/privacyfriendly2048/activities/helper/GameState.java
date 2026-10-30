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


package org.secuso.privacyfriendly2048.activities.helper;


import org.secuso.privacyfriendly2048.activities.Element;

import java.io.Serializable;

/**
 * @author Julian Wadephul and Saskia Jacob
 * @version 20180807
 */
public class GameState implements Serializable {
    /**
     * Pinned to the value the JVM computed for the class before the undo history was added,
     * so saved games from older versions still load (new fields simply read as null / 0).
     */
    private static final long serialVersionUID = 4417047672509562656L;

    /** How many moves can be undone in a row. */
    public static final int UNDO_LIMIT = 5;

    public int n = 4;
    public int[] numbers;
    /** Board before the last move (kept for saves read by older versions). */
    public int[] last_numbers;
    public int points = 0;
    public int last_points = 0;
    public boolean undo = false;

    /** Boards before the last moves, oldest first; only the first {@link #historyCount} are valid. */
    public int[][] history;
    /** Score before each move in {@link #history}. */
    public int[] historyPoints;
    public int historyCount = 0;

    /**
     * Number of undo steps stored in this state. Saves from versions with a single undo step
     * have no history but may still carry that one step in last_numbers.
     */
    public int undoSteps() {
        int size = n * n;
        if (history != null && historyPoints != null) {
            int c = Math.min(historyCount, Math.min(history.length, historyPoints.length));
            for (int i = 0; i < c; i++)
                if (history[i] == null || history[i].length != size)
                    return 0;
            return Math.max(0, c);
        }
        if (undo && last_numbers != null && last_numbers.length == size && last_numbers != numbers)
            return 1;
        return 0;
    }

    /** Board of undo step {@code k} (0 = oldest), flattened row by row. */
    public int[] undoBoard(int k) {
        if (history != null && historyPoints != null)
            return history[k];
        return last_numbers;
    }

    public int undoPoints(int k) {
        if (history != null && historyPoints != null)
            return historyPoints[k];
        return last_points;
    }

    public GameState(int size) {
        numbers = new int[size * size];
    }

    public GameState(int[][] e) {
        int length = 1;
        for (int i = 0; i < e.length; i++) {
            if (e[i].length > length)
                length = e[i].length;
        }
        this.n = e.length;
        numbers = new int[e.length * e.length];
        int c = 0;
        for (int i = 0; i < e.length; i++) {
            for (int j = 0; j < e[i].length; j++) {
                numbers[c++] = e[i][j];
            }
        }
        last_numbers = numbers;
    }

    public GameState(Element[][] e, Element[][] e2) {
        int length = 1;
        for (int i = 0; i < e.length; i++) {
            if (e[i].length > length)
                length = e[i].length;
        }
        this.n = e.length;
        numbers = new int[e.length * e.length];
        int c = 0;
        for (int i = 0; i < e.length; i++) {
            for (int j = 0; j < e[i].length; j++) {

                numbers[c++] = e[i][j].number;
            }
        }
        length = 1;
        for (int i = 0; i < e2.length; i++) {
            if (e2[i].length > length)
                length = e2[i].length;
        }
        last_numbers = new int[e2.length * e2.length];
        c = 0;
        for (int i = 0; i < e2.length; i++) {
            for (int j = 0; j < e2[i].length; j++) {

                last_numbers[c++] = e2[i][j].number;
            }
        }
    }

    public int getNumber(int i, int j) {

        try {
            return numbers[i * n + j];
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
        }

        return 0;
    }

    public int getLastNumber(int i, int j) {

        try {
            return last_numbers[i * n + j];
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
        }

        return 0;
    }


    @Override
    public String toString() {
        String result = "numbers: ";
        for (int i : numbers) {
            result += i + " ";
        }
        result += ", n: " + n;
        result += ", points: " + points;
        result += ", undo: " + undo;
        return result;
    }
}
