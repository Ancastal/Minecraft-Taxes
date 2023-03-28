package org.ancastal.taxes.models;

import java.util.List;

public class TaxData {
	private final String taxType;
	private final double flatPercent;
	private final double flatAmount;
	private final List<TaxBracket> taxBrackets;
	private final double payAmount;

	public TaxData(String taxType, double flatPercent, double flatAmount, List<TaxBracket> taxBrackets, double payAmount) {
		this.taxType = taxType;
		this.flatPercent = flatPercent;
		this.flatAmount = flatAmount;
		this.taxBrackets = taxBrackets;
		this.payAmount = payAmount;
	}

	public String getTaxType() {
		return taxType;
	}

	public double getFlatPercent() {
		return flatPercent;
	}

	public double getFlatAmount() {
		return flatAmount;
	}

	public List<TaxBracket> getTaxBrackets() {
		return taxBrackets;
	}

	public double getPayAmount() {
		return payAmount;
	}
}
