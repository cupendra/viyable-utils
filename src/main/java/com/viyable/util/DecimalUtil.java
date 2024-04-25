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
		Double twoDecimalValue = (double)Math.round(value * 100d) / 100d;//Where number of zeros indicate number of decimal places
		//Double twoDecimalValue = round(value, 2);
		//String stringValue = String.format("%.2f", value);
		System.out.println("Two decimal value: "+twoDecimalValue);
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
	
	public static void main (String args[]){
		String parsedString = toStringTwoDecimalPlaces(50000.4564d);
		toStringTwoDecimalPlaces(50000.4564f);
		Double backToDouble = Double.valueOf(parsedString);
		System.out.println("Back to double:: "+backToDouble);
		System.out.println("Back to double:: "+toTwoDecimalPlaces(50000.4564d));
	}

}
