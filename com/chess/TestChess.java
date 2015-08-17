
package com.test;

import java.util.ArrayList;
import java.util.List;

/**
 * 给你一局未下完的五子棋棋局，现在轮到白棋下，试问白棋能不能至多下两颗棋子就赢了？ 
 */
public class TestChess {

	/**
	 * 其中‘1’表示白棋子，‘0’表示黑棋子，‘ ’表示没有棋子。保证输入棋局合法。
	 */
	public static String[][] game = new String[][] {
		//棋局一
		{" "," ","0"," "," "," "," "," "," "," "},
		{" "," "," ","1"," "," "," ","1"," "," "},
		{" "," "," ","0","1"," "," ","1","1"," "},
		{" "," "," ","0","1","1","1","1","0"," "},
		{" "," "," ","0","1","0","1","0","0"," "},
		{" "," "," "," "," ","0","0","0","1"," "},
		{" "," "," "," "," ","1"," "," "," "," "},
		{" "," "," "," "," "," "," "," "," "," "},
		{" "," "," "," "," "," "," "," "," "," "},
		{" "," "," "," "," "," "," "," "," "," "}
		//棋局二
//		{" "," ","0"," "," "," "," "," "," "," "},
//		{" "," "," ","1"," "," "," "," "," "," "},
//		{" "," "," ","0","1"," ","1","1"," "," "},
//		{" "," "," ","0","1","1","1","1","0"," "},
//		{" "," "," "," ","1","0","1","0","0"," "},
//		{" "," "," "," "," ","0","0","0","1"," "},
//		{" "," "," "," "," ","1","0","1"," "," "},
//		{" "," "," "," "," "," "," ","0"," "," "},
//		{" "," "," "," "," "," "," ","0"," "," "},
//		{" "," "," "," "," "," "," "," "," "," "}
	};
	
	public static void main(String[] args) {
		
		// 判断执子
		int white_num = 0;
		int black_num = 0;
		for (int i = 0; i < 10; i++) {
			for (int j = 0; j < 10; j++) {
				if ("1".equals(game[i][j])) {
					white_num++;
				} else if ("0".equals(game[i][j])) {
					black_num++;
				}
			}
		}
		
		boolean isWhite = true;//white_num <= black_num;
		System.out.println("执[" + (isWhite? "白" : "黑") + "]子!");

		// 判断对方是否将赢
		boolean lose = false;
		List<Line> otherLines = getLine(isWhite ? "0" : "1", 4);
		if (otherLines != null && !otherLines.isEmpty()) {
			for (Line line : otherLines) {
				List<Point> nextPoints = line.getConnectedPoint();
				for (Point nextPoint : nextPoints) {
					int x = nextPoint.x;
					int y = nextPoint.y;
					if (x >= 0 && x < 10 && y >= 0 && y < 10) {
						if (!"1".equals(game[x][y]) && !"0".equals(game[x][y])) {
							lose = true;
							break;
						}
					}
				}
			}
		}
		if (lose) {
			System.out.println("NO!已经输了!");
			return;
		}
		
		// 我方下
		boolean isWin = false;
		for (int i = 0; i < 10; i++) {
			for (int j = 0; j < 10; j++) {
				int chances = 0;
				if (!"1".equals(game[i][j]) && !"0".equals(game[i][j])) {
					
					Point chancesPoint = null;
					
					// 试落子
					game[i][j] = isWhite ? "1" : "0";
				
					// 判断我方是否将赢
					List<Line> myLines = getLine(isWhite ? "1" : "0", 4);
					if (myLines != null && !myLines.isEmpty()) {
						for (Line line : myLines) {
							List<Point> nextPoints = line.getConnectedPoint();
							for (Point nextPoint : nextPoints) {
								int x = nextPoint.x;
								int y = nextPoint.y;
								if (x >= 0 && x < 10 && y >= 0 && y < 10) {
									if (!"1".equals(game[x][y]) && !"0".equals(game[x][y])) {
										if (chancesPoint == null || chancesPoint != null && chancesPoint.x != x && chancesPoint.y != y) {
											chances++;
										}
										if (chancesPoint == null) {
											chancesPoint = new Point(x, y, "");
										}
										if (chances > 1) {
											System.out.println("第一步(行" + (i+1) + ",列" + (j+1) + "),第二步(行" + (x+1) + ",列" + (y+1) + ")可赢!");
										}
									}
								}
							}
						}
					}
					game[i][j] = " ";
					if (chances > 1) {
						isWin = true;
						System.out.println("YES!");
					}
				}
			}
		}
		if (!isWin) {
			System.out.println("NO!");
		}

	}
	
	public static List<Line> getLine(String flag, int length) {
		List<Line> lines = new ArrayList<Line>();
		for (int i = 0; i < 10; i++) {
			for (int j = 0; j < 10; j++) {
				if (!flag.equals(game[i][j])) {
					continue;
				}
				int[] step = new int[] {0, -1, 1};
				for (int step_i : step) {
					for (int step_j : step) {
						if (step_i == 0 && step_j == 0) {
							continue;
						}
						if (step_i + step_j < 0) {
							continue;
						}
						if (i + step_i * 4 < 0 || i + step_i * 4 >= 10 || j + step_j * 4 < 0 || j + step_j * 4 >= 10) {
							continue;
						}
						int matchCount = 0;
						int blankCount = 0;
						for (int n = 1; n < 5; n++) {
							if (flag.equals(game[i + step_i * n][j + step_j * n])) {
								matchCount++;
							}
							if (" ".equals(game[i + step_i * n][j + step_j * n])) {
								blankCount++;
							}
						}
						if (matchCount == 3 && blankCount == 1) {
							Line line = new Line();
							line.points.add(new Point(i, j, game[i][j]));
							line.points.add(new Point(i + step_i * 1, j + step_j * 1, game[i + step_i * 1][j + step_j * 1]));
							line.points.add(new Point(i + step_i * 2, j + step_j * 2, game[i + step_i * 2][j + step_j * 2]));
							line.points.add(new Point(i + step_i * 3, j + step_j * 3, game[i + step_i * 3][j + step_j * 3]));
							line.points.add(new Point(i + step_i * 4, j + step_j * 4, game[i + step_i * 4][j + step_j * 4]));
							lines.add(line);
						}
					}
				}
			}
		}
		return lines;
	}
	
}

class Point {
	int x;
	int y;
	String value;
	Point(int x, int y, String value) {
		this.x = x;
		this.y = y;
		this.value = value;
	}
}

class Line {
	List<Point> points = new ArrayList<Point>();
	List<Point> getConnectedPoint() {
		List<Point> nextPoints = new ArrayList<Point>();
		if (points != null && points.size() > 1) {
			for (int i = 0; i < points.size(); i++) {
				if (" ".endsWith(points.get(i).value)) {
					nextPoints.add(points.get(i));
				}
			}
		}
		return nextPoints;
	}
}
