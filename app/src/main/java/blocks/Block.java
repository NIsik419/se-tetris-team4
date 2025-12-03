package blocks;

import java.awt.Color;

public abstract class Block implements Cloneable {

	protected int[][] shape;
	protected Color color;

	protected boolean canRotate = true; // 기본값: 회전 가능

    

	public int[][] getShapeArray() {
		int[][] copy = new int[shape.length][];
		for (int i = 0; i < shape.length; i++) {
			copy[i] = shape[i].clone();
		}
		return copy;
	}

	public Block() {
		shape = new int[][] {
				{ 1, 1 },
				{ 1, 1 }
		};
		color = Color.YELLOW;
	}

	// 아이템 블록에서 호출할 수 있게 추가 생성자
	public Block(Color color, int[][] shape) {
		this.color = color;
		this.shape = shape;
	}

	public int getShape(int x, int y) {
		return shape[y][x];
	}

	public Color getColor() {
		return color;
	}

	public void rotate() {
		if (!canRotate)
			return;
		int h = shape.length;
		int w = shape[0].length;
		int[][] rotated = new int[w][h];
		for (int i = 0; i < h; i++) {
			for (int j = 0; j < w; j++) {
				rotated[j][h - 1 - i] = shape[i][j];
			}
		}
		shape = rotated;
	}

	public int height() {
		return shape.length;
	}

	public int width() {
		if (shape.length > 0)
			return shape[0].length;
		return 0;
	}

	// clone
	@Override
	public Block clone() {
		try {
			Block copy = (Block) super.clone();
			// shape deep copy
			copy.shape = new int[this.shape.length][];
			for (int i = 0; i < this.shape.length; i++) {
				copy.shape[i] = this.shape[i].clone();
			}
			// 색상도 새로운 객체로 복사
			copy.color = new Color(this.color.getRGB());
			return copy;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

}
