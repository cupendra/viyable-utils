package com.viyable.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class DateUtils {

	public static final String INDIA_DATE_FORMAT = "dd-MM-yyyy";
	public static final String INDIA_DATE_TIME_FORMAT = "dd-MM-yyyy HH:mm:ss";
	public static final String INDIA_DATE_LONG_FORMAT = "dd-MMM-yyyy";
	public static final String INDIA_DATE_TIME_LONG_FORMAT = "dd-MMM-yyyy HH:mm:ss";
	public static final String STANDARD_DATE_FORMAT = "yyyy-MM-dd";
	public static final String STANDARD_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm";
	
	public static final String []MONTHS = {"", "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
	
	public static Date asDate(String dateString) {
		LocalDate localDate = LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE);
		return asDate(localDate);
	}
	
	public static Date asDate(String dateString, String pattern) {
		LocalDate localDate = LocalDate.parse(dateString, DateTimeFormatter.ofPattern(pattern));
		return asDate(localDate);
	}

	public static Date asDateTime(String dateTimeString) {
		LocalDateTime localDateTime = LocalDateTime.parse(dateTimeString, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		return asDate(localDateTime);
	}

	public static Date asDateTime(String dateTimeString, String pattern) {
		LocalDateTime localDateTime = LocalDateTime.parse(dateTimeString, DateTimeFormatter.ofPattern(pattern));
		return asDate(localDateTime);
	}

	public static Date asDate(LocalDate localDate) {
		return Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
	}

	public static Date asDate(LocalDateTime localDateTime) {
		return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
	}

	public static LocalDate asLocalDate(Date date) {
		return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
	}

	public static LocalDateTime asLocalDateTime(Date date) {
		return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
	}

	public static String asDateString(Date date) {
		return asDateString(date, null);
	}

	public static String asDateString(Date date, String pattern) {
		if (date != null) {
			LocalDate localDate = asLocalDate(date);
			return asDateString(localDate, pattern, false);
		}
		
		return "";
	}

	private static String asDateString(LocalDate localDate, String pattern, boolean upperCaseOnly) {
		if (pattern == null) {
			pattern = STANDARD_DATE_FORMAT;
		}
		String dateString = localDate.format(DateTimeFormatter.ofPattern(pattern));
		if (upperCaseOnly) {
			dateString.toUpperCase();
		}
		
		return dateString;
	}

	public static String asDateTimeString(Date dateTime) {
		LocalDateTime localDateTime = asLocalDateTime(dateTime);
		return asDateTimeString(localDateTime, STANDARD_DATE_TIME_FORMAT);
	}

	public static String asDateTimeString(Date dateTime, String pattern) {
		LocalDateTime localDateTime = asLocalDateTime(dateTime);
		return asDateTimeString(localDateTime, pattern);
	}

	private static String asDateTimeString(LocalDateTime localDateTime, String pattern) {
		if (pattern == null) {
			pattern = STANDARD_DATE_TIME_FORMAT;
		}
		return localDateTime.format(DateTimeFormatter.ofPattern(pattern)).toUpperCase();
	}

	public static Date setToFirstDayOfMonth(Date date) {
		LocalDate ld = asLocalDate(date);

		String strDay = "01";

		String strMonth = "";
		int month = ld.getMonthValue();
		if (month < 10) {
			strMonth = "0" + month;
		} else {
			strMonth = strMonth + month;
		}

		String strYear = "" + ld.getYear();

		String dateString = strYear + "-" + strMonth + "-" + strDay;

		return asDate(dateString);
	}
	
	public static Date getDayBeginning(Date date) {
		Calendar cal = Calendar.getInstance();
		
		cal.setTime(date);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		
		Date dayBegginingMidnight = cal.getTime();
		
		return dayBegginingMidnight;
	}
	
	public static Date getDayEnding(Date date) {
		Calendar cal = Calendar.getInstance();
		
		cal.setTime(date);
		cal.set(Calendar.HOUR_OF_DAY, 23);
		cal.set(Calendar.MINUTE, 59);
		cal.set(Calendar.SECOND, 59);
		cal.set(Calendar.MILLISECOND, 999);
		
		Date dayEndingMidnight = cal.getTime();
		
		return dayEndingMidnight;
	}
	
	public static Date getTodayBeginning() {
		return getDayBeginning(new Date());
	}
	
	public static Date getTodayEnding() {
		return getDayEnding(new Date());
	}
	
	public static boolean isToday(Date givenDate) {
		boolean status = false;
		
		Date today = new Date();
		
		String givenDateString = asDateString(givenDate);
		String todayString = asDateString(today);
		
		status = todayString.equals(givenDateString);
		
		return status;
	}
	
	public static Long differenceInDays(Date date1, Date date2) {
		long difference = 0;
		
		if (date1 == null) {
			date1 = new Date();
		}
		
		if (date2 == null) {
			date2 = new Date();
		}
		
		long diffInMilliseconds = date2.getTime() - date1.getTime();
		
		difference = TimeUnit.DAYS.convert(diffInMilliseconds, TimeUnit.MILLISECONDS) + 1;
		
		return difference;
	}
	
	public static String getWeekDay(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		
		String displayName = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG_FORMAT, Locale.forLanguageTag("en-IN"));

		return displayName;
		
	}
	
	public static String getShortWeekDay(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		
		String displayName = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT_FORMAT, Locale.forLanguageTag("en-IN"));

		return displayName;
	}
	
	public static Integer getMonth (Integer value) {
		Calendar cal = Calendar.getInstance();
		
		cal.add(Calendar.MONTH, value);
		
		return cal.get(Calendar.MONTH) + 1;
	}
	
	public static String getMonthName(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		
		String displayName = cal.getDisplayName(Calendar.MONTH, Calendar.LONG_FORMAT, Locale.forLanguageTag("en-IN"));

		return displayName;
		
	}
	
	public static String getShortMonthName(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		
		String displayName = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT_FORMAT, Locale.forLanguageTag("en-IN"));

		return displayName;
	}
	
	public static String getMonthName(int month) {
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.MONTH, month);
		
		String displayName = cal.getDisplayName(Calendar.MONTH, Calendar.LONG_FORMAT, Locale.forLanguageTag("en-IN"));

		return displayName;
		
	}
	
	public static String getShortMonthName(int month) {
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.MONTH, month);
		
		String displayName = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT_FORMAT, Locale.forLanguageTag("en-IN"));

		return displayName;
	}
	
	public static Integer getYear (Integer value) {
		Calendar cal = Calendar.getInstance();
		
		cal.add(Calendar.YEAR, value);
		
		return cal.get(Calendar.YEAR);
	}
	
	public static Integer getDay () {
		Calendar cal = Calendar.getInstance();
		
		return cal.get(Calendar.DAY_OF_MONTH);
	}
	
	public static Integer getMonth () {
		Calendar cal = Calendar.getInstance();
		
		return cal.get(Calendar.MONTH) + 1;
	}
	
	public static Integer getYear () {
		Calendar cal = Calendar.getInstance();
		
		return cal.get(Calendar.YEAR);
	}
	
	public static Integer getNumberOfDaysInCurrentMonth() {
		return YearMonth.now().lengthOfMonth();
	}
	
	public static Integer getNumberOfDaysInGivenMonth(int month) {
		return YearMonth.now().withMonth(month).lengthOfMonth();
	}
	
	public static Integer getNumberOfDaysInGivenMonthYear(int month, int year) {
		YearMonth yearMonth = YearMonth.of(year, month);
		int lengthOfMonth = yearMonth.lengthOfMonth();
		
		return lengthOfMonth;
	}
	
	public static int getMonthOfTheDate(Date date) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		
		return calendar.get(Calendar.MONTH) + 1;
	}
	
	public static int getYearOfTheDate(Date date) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		
		return calendar.get(Calendar.YEAR);
	}
	
	public static Date getFirstDateOfPrevMonth() {
		YearMonth yearMonth = YearMonth.now().minusMonths(1);
		
		return asDate(yearMonth.atDay(1));
	}
	
	public static Date getLastDateOfPrevMonth() {
		YearMonth yearMonth = YearMonth.now().minusMonths(1);
		int lengthOfMonth = yearMonth.lengthOfMonth();
		
		return asDate(yearMonth.atDay(lengthOfMonth));
	}
	
	public static Date getFirstDateOfMonth() {
		YearMonth yearMonth = YearMonth.now();
		
		return asDate(yearMonth.atDay(1));
	}
	
	public static Date getLastDateOfMonth() {
		YearMonth yearMonth = YearMonth.now();
		int lengthOfMonth = yearMonth.lengthOfMonth();
		
		return asDate(yearMonth.atDay(lengthOfMonth));
	}
	
	public static Date getFirstDateOfMonth(int month) {
		YearMonth yearMonth = YearMonth.now().withMonth(month);
		
		return asDate(yearMonth.atDay(1));
	}
	
	public static Date getLastDateOfMonth(int month) {
		YearMonth yearMonth = YearMonth.now().withMonth(month);
		int lengthOfMonth = yearMonth.lengthOfMonth();
		
		return asDate(yearMonth.atDay(lengthOfMonth));
	}
	
	public static Date getFirstDateOfMonth(int month, int year) {
		return asDate(YearMonth.of(year, month).atDay(1));
	}
	
	public static Date getPrevDate (Date date, int daysBefore) {
		LocalDate prevDate = LocalDate.of(getYearOfTheDate(date), getMonthOfTheDate(date), getDayOfDate(date)).minusDays(daysBefore);
		
		return asDate(prevDate);
	}
	
	public static Date getNextDate (Date date, int daysAfter) {
		LocalDate nextDate = LocalDate.of(getYearOfTheDate(date), getMonthOfTheDate(date), getDayOfDate(date)).plusDays(daysAfter);
		
		return asDate(nextDate);
	}
	
	public static Date getPrevDate (Date date) {
		LocalDate prevDate = LocalDate.of(getYearOfTheDate(date), getMonthOfTheDate(date), getDayOfDate(date)).minusDays(1);
		
		return asDate(prevDate);
	}
	
	public static Date getNextDate (Date date) {
		LocalDate nextDate = LocalDate.of(getYearOfTheDate(date), getMonthOfTheDate(date), getDayOfDate(date)).plusDays(1);
		
		return asDate(nextDate);
	}
	
	public static Date getDate(int day, int month, int year) {
		return asDate(YearMonth.of(year, month).atDay(day));
	}
	
	public static Date getDate(int dayOfMonth) {
		return asDate(YearMonth.of(getYear(), getMonth()).atDay(dayOfMonth));
	}
	
	public static Date getLastDateOfMonth(int month, int year) {
		YearMonth yearMonth = YearMonth.of(year, month);
		int lengthOfMonth = yearMonth.lengthOfMonth();
		return asDate(yearMonth.atDay(lengthOfMonth));
	}
	
	public static Integer getDayOfDate(Date date) {
		Integer day = -1;
		
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		
		day = cal.get(Calendar.DAY_OF_MONTH);
		
		return day;
	}
	
	
	
	public static Date getFirstDateOfYear () {
		Calendar cal = Calendar.getInstance();
		
		cal.set(Calendar.MONTH, Calendar.JANUARY);
		cal.set(Calendar.DAY_OF_MONTH, 1);
		
		return getDayBeginning(cal.getTime());
	}
	
	public static Date getLastDateOfYear () {
		Calendar cal = Calendar.getInstance();
		
		cal.set(Calendar.MONTH, Calendar.DECEMBER);
		cal.set(Calendar.DAY_OF_MONTH, 31);
		
		return getDayBeginning(cal.getTime());
	}
	
	public static int getNumberOfMonths(Date startDate, Date endDate) {
		LocalDate localStartDate = asLocalDate(startDate);
		LocalDate localEndDate = asLocalDate(endDate);
		Period period = Period.between(localStartDate, localEndDate);
		
		return period.getMonths();
	}
	
	public static void main(String args[]) {
		Date today = new Date();
		System.out.println(setToFirstDayOfMonth(today));
		System.out.println(asDateTime("2017-12-03T11:46"));
		System.out.println(getTodayBeginning());
		
		System.out.println("TimeZone:: "+TimeZone.getTimeZone("IST"));
		
		System.out.println("Locale:: "+Locale.forLanguageTag("en-IN").getDisplayName());
		System.out.println("WeekDay:: "+getWeekDay(today));
		System.out.println("No.of Days::"+differenceInDays(setToFirstDayOfMonth(today), today));
		
		System.out.println("Year of the date :: "+getYear());
		
		System.out.println("First day of month :: "+getFirstDateOfMonth(2, 2018));
		System.out.println("Last day of month :: "+getLastDateOfMonth(2, 2018));
		
		LocalDate startDate = LocalDate.of(2017, 9, 1);
		LocalDate endDate = LocalDate.of(2018, 10, 8);
		
		long nMonths = ChronoUnit.MONTHS.between(startDate, endDate);
		System.out.println("Number of months :: "+nMonths);
		
		Period period = Period.between(startDate, endDate);
		System.out.println("Years : "+period.getYears());
		System.out.println("Months : "+period.getMonths());
		System.out.println("Days : "+period.getDays());
		
		System.out.println("Month of the current date :: " + getMonthOfTheDate(today));
		System.out.println("Day of the month :: "+getDayOfDate(new Date()));
		
		System.out.println("Last date of previous month :: "+getLastDateOfPrevMonth());
		
		System.out.println("Short week day :: "+getShortWeekDay(today));
		System.out.println("Week day :: "+getWeekDay(today));
		
		System.out.println("Days before :"+getPrevDate(today, 17));
		System.out.println("Days after :"+getNextDate(today, 2));
		
		System.out.println("Month Name: "+getMonthName(new Date()));
		System.out.println("Month Short Name: "+getShortMonthName(new Date()));
		
		System.out.println("Month Name: "+getMonthName(11));
		System.out.println("Month Short Name: "+getShortMonthName(1));
		
	}

}