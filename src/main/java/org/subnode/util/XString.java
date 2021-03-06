package org.subnode.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.subnode.config.SpringContextUtil;
import org.subnode.exception.base.RuntimeEx;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.Resource;

/**
 * General string utilities.
 */
public class XString {

	public static final ObjectMapper jsonMapper = new ObjectMapper();
	static {
		jsonMapper.setSerializationInclusion(Include.NON_NULL);
	}
	private static ObjectWriter jsonPrettyWriter = jsonMapper.writerWithDefaultPrettyPrinter();

	public static String prettyPrint(Object obj) {
		try {
			return jsonPrettyWriter.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			return "";
		}
	}

	public static String getStringFromStream(InputStream inputStream) {
		try {
			StringWriter writer = new StringWriter();
			String encoding = StandardCharsets.UTF_8.name();
			IOUtils.copy(inputStream, writer, encoding);
			return writer.toString();
		} catch (Exception e) {
			throw new RuntimeEx("getStringFromStream failed.", e);
		}
	}

	public static String lastNChars(String val, int chars) {
		if (val.length() > chars) {
			return val.substring(val.length() - chars);
		} else {
			return val;
		}
	}

	public static String repeatingTrimFromFront(String val, String prefix) {
		if (val == null)
			return null;
		int loopSafe = 0;
		while (++loopSafe < 1000) {
			int len = val.length();
			val = stripIfStartsWith(val.trim(), prefix);

			/* if string remained same length we're done */
			if (len == val.length()) {
				break;
			}
		}
		return val;
	}

	public static List<String> tokenize(String val, String delimiter, boolean trim) {
		if (val == null)
			return null;
		List<String> list = null;
		StringTokenizer t = new StringTokenizer(val, delimiter, false);
		while (t.hasMoreTokens()) {
			if (list == null) {
				list = new LinkedList<String>();
			}
			list.add(trim ? t.nextToken().trim() : t.nextToken());
		}
		return list;
	}

	public static HashSet<String> tokenizeToSet(String val, String delimiter, boolean trim) {
		HashSet<String> list = null;
		StringTokenizer t = new StringTokenizer(val, delimiter, false);
		while (t.hasMoreTokens()) {
			if (list == null) {
				list = new HashSet<String>();
			}
			list.add(trim ? t.nextToken().trim() : t.nextToken());
		}
		return list;
	}

	public static String trimToMaxLen(String val, int maxLen) {
		if (val == null)
			return null;
		if (val.length() <= maxLen)
			return val;
		return val.substring(0, maxLen - 1);
	}

	// Resource resource =
	// SpringContextUtil.getApplicationContext().getResource(resourceName);
	// InputStream is = null;
	// SubNode rootNode = null;
	// try {
	// is = resource.getInputStream();
	// rootNode = inputZipFileFromStream(session, is, node, true);
	// } catch (Exception e) {
	// throw ExUtil.newEx(e);
	// } finally {
	// StreamUtil.close(is);
	// }

	public static String getResourceAsString(String resourceName) {
		Resource resource = SpringContextUtil.getApplicationContext().getResource(resourceName);
		String content = XString.loadResourceIntoString(resource);
		return content;
	}

	public static String loadResourceIntoString(Resource resource) {
		BufferedReader in = null;
		StringBuilder sb = new StringBuilder();

		try {
			in = new BufferedReader(new InputStreamReader(resource.getInputStream()));
			String line;
			while ((line = in.readLine()) != null) {
				sb.append(line);
				sb.append("\n");
			}
		} catch (Exception e) {
			sb.setLength(0);
		} finally {
			StreamUtil.close(in);
		}
		return sb.toString();
	}

	/* Truncates after delimiter including truncating the delimiter */
	public final static String truncateAfterFirst(String text, String delim) {
		if (text == null)
			return null;

		int idx = text.indexOf(delim);
		if (idx != -1) {
			text = text.substring(0, idx);
		}
		return text;
	}

	public static String stripIfEndsWith(String val, String suffix) {
		if (val.endsWith(suffix)) {
			val = val.substring(0, val.length() - suffix.length());
		}
		return val;
	}

	public static String stripIfStartsWith(String val, String prefix) {
		if (val.startsWith(prefix)) {
			val = val.substring(prefix.length());
		}
		return val;
	}

	public static String removeLastChar(String str) {
		return str.substring(0, str.length() - 1);
	}

	public final static String truncateAfterLast(String text, String delim) {
		if (text == null)
			return null;

		int idx = text.lastIndexOf(delim);
		if (idx != -1) {
			text = text.substring(0, idx);
		}
		return text;
	}

	public final static String parseAfterLast(String text, String delim) {
		if (text == null)
			return null;

		int idx = text.lastIndexOf(delim);
		if (idx != -1) {
			text = text.substring(idx + delim.length());
		}
		return text;
	}

	/*
	 * Ensures string containing val which is number is prepended with leading
	 * zeroes to make the string 'count' chars long. Using simplest inefficient
	 * algorithm for now. Can be done faster with one concat
	 */
	public final static String addLeadingZeroes(String val, int count) {
		while (val.length() < count) {
			val = "0" + val;
		}
		return val;
	}

	/**
	 * input: abc--file.txt, -- output: file.txt
	 */
	public final String truncateBefore(String fileName, String delims) {
		if (fileName == null)
			return null;

		String ret = null;
		int idx = fileName.indexOf(delims);
		if (idx != -1) {
			ret = fileName.substring(idx + delims.length());
		} else {
			ret = fileName;
		}
		// log.debug("truncateBefore: input[" + fileName + "] output[" + ret + "]");
		return ret;
	}

	/**
	 * input: abc--file.txt, . output: abc--file
	 */
	public final String truncateAfter(String fileName, String delims) {
		if (fileName == null)
			return null;

		String ret = null;
		int idx = fileName.lastIndexOf(delims);
		if (idx != -1) {
			ret = fileName.substring(0, idx);
		} else {
			ret = fileName;
		}
		// log.debug("truncateAfter: input[" + fileName + "] output[" + ret + "]");
		return ret;
	}

	/**
	 * input: /home/clay/path/file.txt output: /home/clay/path
	 */
	public final String getPathPart(String fileName) {
		if (fileName == null)
			return null;

		String pathPart = null;
		int idx = fileName.lastIndexOf(File.separatorChar);
		if (idx != -1) {
			pathPart = fileName.substring(0, idx);
		} else {
			pathPart = fileName;
		}
		// log.debug("Short name of [" + fileName + "] is [" + shortName + "]");
		return pathPart;
	}
}
