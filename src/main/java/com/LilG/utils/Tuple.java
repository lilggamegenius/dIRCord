package com.LilG.utils;

import com.google.common.base.Objects;

public class Tuple<X, Y> {
	public final X x;
	public final Y y;

	public Tuple(X x, Y y) {
		this.x = x;
		this.y = y;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Tuple)) return false;
		Tuple test = (Tuple) obj;
		return test.x == x && test.y == y;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(x, y);
	}

	@Override
	public String toString() {
		return String.format("<%s,%s>", x, y);
	}
}