package com.viyable.util;

public class SchedulerExpressions {
	
	//Example Expressions
		//
		//"0 0 * * * *" = the top of every hour of every day.
		//"*/10 * * * * *" = every ten seconds.
		//"0 0 8-10 * * *" = 8, 9 and 10 o'clock of every day.
		//"0 0 6,19 * * *" = 6:00 AM and 7:00 PM every day.
		//"0 0/30 8-10 * * *" = 8:00, 8:30, 9:00, 9:30, 10:00 and 10:30 every day.
		//"0 0 9-17 * * MON-FRI" = on the hour nine-to-five weekdays
		//"0 0 0 25 12 ?" = every Christmas Day at midnight
	
	 //every midnight at 12 AM. cron (seconds, minutes, hours, day 1-31, month 1-12, everyday/weekday)
	
	public static final String EVERYDAY_MID_NIGHT = "0 0 0 * * ?";
	
	public static final String EVERY_MORNING_3AM = "0 0 3 * * *";
	
	public static final String EVERY_MORNING_5AM = "0 0 5 * * *";
	
	public static final String EVERY_15DAYS_AT_5AM = "0 0 2 15,28 * ?";
	
	public static final String AFTERNOON_3PM = "0 0 15 * * *";

}
