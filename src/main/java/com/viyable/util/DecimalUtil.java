package com.viyable.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

public class DecimalUtil {
	public static DecimalFormat DF = new DecimalFormat(".##");
	
	public static double round(double value, int places) {
	    if (places < 0) throw new IllegalArgumentException();

	    BigDecimal bd = new BigDecimal(value);
	    bd = bd.setScale(places, RoundingMode.HALF_UP);
	    return bd.doubleValue();
	}
	
	
	public static Double toTwoDecimalPlaces(Double value){
		if (value == null) {
			value = 0d;
		}
		// ⚠⚠ NO println HERE. This used to print every value it rounded to stdout. All 24 callers
		//    are in EmpSalaryUtil, so a payroll run printed twenty-four money figures PER EMPLOYEE
		//    — salary, allowances, overtime, every deduction — into the container log.
		// ⚠ stdout bypasses the structured logger entirely, and the PII scrubbing is an
		//   <includeMdcKeyName> allowlist on the JSON encoder. Anything printed this way is not
		//   scrubbed because it never passes through the thing that scrubs.
		Double twoDecimalValue = (double)Math.round(value * 100d) / 100d;
		return twoDecimalValue;
	}
	
	public static String toStringTwoDecimalPlaces(Double value){
		if (value == null) {
			value = 0d;
		}
		String stringValue = String.format("%.2f", value);
		//System.out.println("String value: "+stringValue);
		return stringValue;
	}
	
	public static String toStringTwoDecimalPlaces(Float value){
		String stringValue = String.format("%.2f", value);
		//System.out.println("String value: "+stringValue);
		return stringValue;
	}
	
	// ⚠ A main() that printed a hardcoded figure lived here — debug scaffolding in a
	//   shared library, shipped to every service. Removed.

}
