package com.LilG.utils;

import ch.qos.logback.classic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by ggonz on 8/13/2016.
 * Utility class - contains misc functions
 */

public class LilGUtil {
	private final static Random rand = new Random();
	private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(LilGUtil.class);

	/**
	 * Returns a pseudo-random number between min and max, inclusive.
	 * The difference between min and max can be at most
	 * <code>Integer.MAX_VALUE - 1</code>.
	 *
	 * @param min Minimum value
	 * @param max Maximum value.  Must be greater than min.
	 * @return Integer between min and max, inclusive.
	 * @see java.util.Random#nextInt(int)
	 */

	public static int randInt(int min, int max) {
		// nextInt is normally exclusive of the top value,
		// so add 1 to make it inclusive

		return rand.nextInt((max - min) + 1) + min;
	}

	public static double randDec(double min, double max) {
		// nextInt is normally exclusive of the top value,
		// so add 1 to make it inclusive

		return rand.nextFloat() * (max - min) + min;
	}

	public static String getBytes(@NotNull String bytes) {
		byte[] Bytes = bytes.getBytes();
		return Arrays.toString(Bytes);
	}

	public static String formatFileSize(long size) {
		String hrSize;

		double k = size / 1024.0;
		double m = ((size / 1024.0) / 1024.0);
		double g = (((size / 1024.0) / 1024.0) / 1024.0);
		double t = ((((size / 1024.0) / 1024.0) / 1024.0) / 1024.0);

		DecimalFormat dec = new DecimalFormat("0.00");

		if (t > 1) {
			hrSize = dec.format(t).concat(" TB");
		} else if (g > 1) {
			hrSize = dec.format(g).concat(" GB");
		} else if (m > 1) {
			hrSize = dec.format(m).concat(" MB");
		} else if (k > 1) {
			hrSize = dec.format(k).concat(" KB");
		} else {
			hrSize = dec.format((double) size).concat(" B");
		}

		return hrSize;
	}

	public static boolean isNumeric(@NotNull String str) {
		return str.matches("-?\\d+(\\.\\d+)?");  //match a number with optional '-' and decimal.
	}

	/**
	 * This method guarantees that garbage collection is
	 * done unlike <code>{@link System#gc()}</code>
	 */
	public static int gc() {
		int timesRan = 0;
		Object obj = new Object();
		WeakReference<?> ref = new WeakReference<>(obj);
		//noinspection UnusedAssignment
		obj = null;
		while (ref.get() != null) {
			System.gc();
			timesRan++;
		}
		LOGGER.info("Took " + timesRan + " attempt(s) to run GC");
		return timesRan;
	}

	public static Unsafe getUnsafe() {
		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			return (Unsafe) f.get(null);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			LOGGER.error("Error getting unsafe class", e);
		}
		return null;
	}

	public static long sizeOf(Object o) {
		Unsafe u = getUnsafe();
		assert u != null;
		HashSet<Field> fields = new HashSet<>();
		Class c = o.getClass();
		while (c != Object.class) {
			for (Field f : c.getDeclaredFields()) {
				if ((f.getModifiers() & Modifier.STATIC) == 0) {
					fields.add(f);
				}
			}
			c = c.getSuperclass();
		}

		// get offset
		long maxSize = 0;
		for (Field f : fields) {
			long offset = u.objectFieldOffset(f);
			if (offset > maxSize) {
				maxSize = offset;
			}
		}

		return ((maxSize / 8) + 1) * 8;   // padding
	}

	@NotNull
	public static String[] splitMessage(String stringToSplit) {
		return splitMessage(stringToSplit, 0);
	}

	@NotNull
	public static String[] splitMessage(String stringToSplit, int amountToSplit) {
		return splitMessage(stringToSplit, amountToSplit, true);
	}

	@NotNull
	public static String[] splitMessage(String stringToSplit, boolean removeQuotes) {
		return splitMessage(stringToSplit, 0, removeQuotes);
	}

	@NotNull
	public static String[] splitMessage(@Nullable String stringToSplit, int amountToSplit, boolean removeQuotes) {
		if (stringToSplit == null)
			return new String[]{""};

		LinkedList<String> list = new LinkedList<>();
		Matcher argSep = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(stringToSplit);
		while (argSep.find())
			list.add(argSep.group(1));

		if (removeQuotes) {
			if (amountToSplit != 0) {
				for (int i = 0; list.size() > i; i++) { // go through all of the
					list.set(i, list.get(i).replaceAll("\"", "")); // remove quotes left in the string
					list.set(i, list.get(i).replaceAll("''", "\"")); // replace double ' to quotes
					// go to next string
				}
			} else {
				for (int i = 0; list.size() > i || amountToSplit > i; i++) { // go through all of the
					list.set(i, list.get(i).replaceAll("\"", "")); // remove quotes left in the string
					list.set(i, list.get(i).replaceAll("''", "\"")); // replace double ' to quotes
					// go to next string
				}
			}
		}
		return list.toArray(new String[list.size()]);
	}

	public static boolean containsAny(@NotNull String check, @NotNull String... contain) {
		for (String aContain : contain) {
			if (check.contains(aContain)) {
				return true;
			}
		}
		return false;
	}

	public static boolean equalsAny(@NotNull String check, @NotNull String... equal) {
		for (String aEqual : equal) {
			if (check.equals(aEqual)) {
				return true;
			}
		}
		return false;
	}

	public static boolean equalsAnyIgnoreCase(@NotNull String check, @NotNull String... equal) {
		for (String aEqual : equal) {
			if (check.equalsIgnoreCase(aEqual)) {
				return true;
			}
		}
		return false;
	}

	public static boolean containsAnyIgnoreCase(@NotNull String check, @NotNull String... equal) {
		for (String aEqual : equal) {
			if (check.toLowerCase().contains(aEqual.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	public static boolean startsWithAny(@NotNull String check, String... equal) {
		for (String aEqual : equal) {
			if (aEqual == null) continue; // IRC Lib sometimes returns null info?? wtf
			if (check.startsWith(aEqual)) {
				return true;
			}
		}
		return false;
	}

	public static boolean endsWithAny(@NotNull String check, @NotNull String... equal) {
		for (String aEqual : equal) {
			if (check.endsWith(aEqual)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Performs a wildcard matching for the text and pattern
	 * provided.
	 *
	 * @param text    the text to be tested for matches.
	 * @param pattern the pattern to be matched for.
	 *                This can contain the wildcard character '*' (asterisk).
	 * @return <tt>true</tt> if a match is found, <tt>false</tt>
	 * otherwise.
	 */
	public static boolean wildCardMatch(@NotNull String text, @NotNull String pattern) {
		int i = 0;
		int j = 0;
		int starIndex = -1;
		int iIndex = -1;

		while (i < text.length()) {
			if (j < pattern.length() && (pattern.charAt(j) == '?' || pattern.charAt(j) == text.charAt(i))) {
				++i;
				++j;
			} else if (j < pattern.length() && pattern.charAt(j) == '*') {
				starIndex = j;
				iIndex = i;
				j++;
			} else if (starIndex != -1) {
				j = starIndex + 1;
				i = iIndex + 1;
				iIndex++;
			} else {
				return false;
			}
		}
		while (j < pattern.length() && pattern.charAt(j) == '*') {
			++j;
		}
		return j == pattern.length();
	}

	public static boolean matchHostMask(@NotNull String hostmask, @NotNull String pattern) {
		try {
			String nick = hostmask.substring(0, hostmask.indexOf("!"));
			String userName = hostmask.substring(hostmask.indexOf("!") + 1, hostmask.indexOf("@"));
			String hostname = hostmask.substring(hostmask.indexOf("@") + 1);

			String patternNick = pattern.substring(0, pattern.indexOf("!"));
			String patternUserName = pattern.substring(pattern.indexOf("!") + 1, pattern.indexOf("@"));
			String patternHostname = pattern.substring(pattern.indexOf("@") + 1);
			if (wildCardMatch(nick, patternNick)) {
				if (wildCardMatch(userName, patternUserName)) {
					return wildCardMatch(hostname, patternHostname);
				}
			}
		} catch (StringIndexOutOfBoundsException ignored) {
		}
		return false;
	}

	public static void pause(int time) throws InterruptedException {
		pause(time, true);
	}

	public static void pause(int time, boolean echoTime) throws InterruptedException {
		if (echoTime) {
			LOGGER.debug("Sleeping for " + time + " seconds");
		}
		Thread.sleep(time * 1000);
	}

	public static <T extends Enum<?>> T searchEnum(Class<T> enumeration, String search) {
		for (T each : enumeration.getEnumConstants()) {
			if (each.name().equalsIgnoreCase(search)) {
				return each;
			}
		}
		return null;
	}

	public static void removeDuplicates(LinkedList<String> list) {
		LinkedList<String> ar = new LinkedList<>();
		while (list.size() > 0) {
			ar.add(list.get(0));
			list.removeAll(Collections.singleton(list.get(0)));
		}
		list.addAll(ar);
	}

	public static <T, S> S cast(T type, Class<S> cast) throws ClassCastException {
		return cast.cast(type);
	}

	public static int hash(@NotNull String string, int maxNum) {
		int hash = 0;
		for (int i = 0; i < string.length(); i++) {
			int charCode = string.charAt(i);
			hash += charCode;
		}
		return hash % maxNum;
	}

	public static double getProcessCpuLoad() throws Exception {

		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		ObjectName name = ObjectName.getInstance("java.lang:type=OperatingSystem");
		AttributeList list = mbs.getAttributes(name, new String[]{"ProcessCpuLoad"});

		if (list.isEmpty()) return Double.NaN;

		Attribute att = (Attribute) list.get(0);
		Double value = (Double) att.getValue();

		// usually takes a couple of seconds before we get real values
		if (value == -1.0) return Double.NaN;
		// returns a percentage value with 1 decimal point precision
		return ((int) (value * 1000) / 10.0);
	}

	/**
	 * Returns a list with all links contained in the input
	 */
	public static List<String> extractUrls(String text) {
		List<String> containedUrls = new ArrayList<>();
		String urlRegex = "((https?|ftp|gopher|telnet|file):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?+-=\\\\.&]*)";
		Pattern pattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE);
		Matcher urlMatcher = pattern.matcher(text);

		while (urlMatcher.find()) {
			containedUrls.add(text.substring(urlMatcher.start(0),
					urlMatcher.end(0)));
		}

		return containedUrls;
	}
}
