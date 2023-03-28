package org.ancastal.taxes.models;

public class TaxBracket {
	private final int min;
	private final int max;
	private final double rate;

	public TaxBracket(int min, int max, double rate) {
		this.min = min;
		this.max = max;
		this.rate = rate;
	}

	public int getMin() {
		return min;
	}

	public int getMax() {
		return max;
	}

	public double getRate() {
		return rate;
	}

	public boolean isWithinRange(double balance) {
		return balance >= min && balance <= max;
	}
}
