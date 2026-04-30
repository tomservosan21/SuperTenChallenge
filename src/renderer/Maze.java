package renderer;

import java.util.Random;

public class Maze {
    private int width;
    private int height;
    private char[][] grid;
    private boolean[][] visited;
    private Random rand = new Random();

    // Directions: up, down, left, right
    private final int[] dx = {0, 0, -2, 2};
    private final int[] dy = {-2, 2, 0, 0};

    public Maze(int width, int height) {
        this.width = (width % 2 == 0) ? width + 1 : width;
        this.height = (height % 2 == 0) ? height + 1 : height;

        grid = new char[this.height][this.width];
        visited = new boolean[this.height][this.width];

        initializeGrid();
        generateMaze(1, 1);
        addStartAndEnd(); // 👈 NEW
    }

    private void initializeGrid() {
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                grid[i][j] = 'X';
                visited[i][j] = false;
            }
        }
    }

    private void generateMaze(int x, int y) {
        visited[y][x] = true;
        grid[y][x] = 'O';

        int[] directions = {0, 1, 2, 3};
        shuffleArray(directions);

        for (int dir : directions) {
            int nx = x + dx[dir];
            int ny = y + dy[dir];

            if (isInBounds(nx, ny) && !visited[ny][nx]) {
                grid[y + dy[dir] / 2][x + dx[dir] / 2] = 'O';
                generateMaze(nx, ny);
            }
        }
    }

    private void addStartAndEnd() {
        // Start at top row
        for (int x = 1; x < width - 1; x++) {
            if (grid[1][x] == 'O') {
                //grid[0][x] = 'S';
                grid[0][x] = 'X';
                break;
            }
        }

        // End at bottom row
        for (int x = width - 2; x > 0; x--) {
            if (grid[height - 2][x] == 'O') {
                grid[height - 1][x] = 'E';
                break;
            }
        }
    }

    private boolean isInBounds(int x, int y) {
        return x > 0 && x < width - 1 && y > 0 && y < height - 1;
    }

    private void shuffleArray(int[] array) {
        for (int i = array.length - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            int temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }

    public char[][] getGrid() {
        return grid;
    }
}