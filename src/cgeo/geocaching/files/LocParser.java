package cgeo.geocaching.files;

import java.io.File;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cgeo.geocaching.cgBase;
import cgeo.geocaching.cgCache;
import cgeo.geocaching.cgCacheWrap;
import cgeo.geocaching.cgCoord;
import cgeo.geocaching.cgSearch;
import cgeo.geocaching.cgSettings;
import cgeo.geocaching.cgeoapplication;

import android.os.Handler;
import android.util.Log;

public final class LocParser extends FileParser {
	private static final Pattern patternGeocode = Pattern
			.compile("name id=\"([^\"]+)\"");
	private static final Pattern patternLat = Pattern
			.compile("lat=\"([^\"]+)\"");
	private static final Pattern patternLon = Pattern
			.compile("lon=\"([^\"]+)\"");
	// premium only >>
	private static final Pattern patternDifficulty = Pattern
			.compile("<difficulty>([^<]+)</difficulty>");
	private static final Pattern patternTerrain = Pattern
			.compile("<terrain>([^<]+)</terrain>");
	private static final Pattern patternContainer = Pattern
			.compile("<container>([^<]+)</container>");
	private static final Pattern patternName = Pattern.compile("CDATA\\[([^\\]]+)\\]");

	public static void parseLoc(final cgCacheWrap caches,
			final String fileContent) {
		final HashMap<String, cgCoord> cidCoords = parseCoordinates(fileContent);

		// save found cache coordinates
		for (cgCache cache : caches.cacheList) {
			if (cidCoords.containsKey(cache.geocode)) {
				cgCoord coord = cidCoords.get(cache.geocode);

				copyCoordToCache(coord, cache);
			}
		}
	}

	private static void copyCoordToCache(final cgCoord coord, final cgCache cache) {
		cache.latitude = coord.latitude;
		cache.longitude = coord.longitude;
		cache.difficulty = coord.difficulty;
		cache.terrain = coord.terrain;
		cache.size = coord.size;
		cache.geocode = coord.geocode.toUpperCase();
		if (cache.name == null || cache.name.length() == 0) {
			cache.name = coord.name;
		}
	}

	private static HashMap<String, cgCoord> parseCoordinates(
			final String fileContent) {
		final HashMap<String, cgCoord> coords = new HashMap<String, cgCoord>();
		if (fileContent == null || fileContent.length() <= 0) {
			return coords;
		}
		// >> premium only

		final String[] points = fileContent.split("<waypoint>");

		// parse coordinates
		for (String pointString : points) {
			final cgCoord pointCoord = new cgCoord();
			HashMap<String, Object> tmp = null;

			final Matcher matcherGeocode = patternGeocode.matcher(pointString);
			if (matcherGeocode.find()) {
				String geocode = matcherGeocode.group(1).trim().toUpperCase();
				pointCoord.name = geocode;
				pointCoord.geocode = geocode;
			}
			final Matcher matcherName = patternName.matcher(pointString);
			if (matcherName.find()) {
				String name = matcherName.group(1).trim();
				int pos = name.indexOf(" by ");
				if (pos > 0) {
					name = name.substring(0, pos).trim();
				}
				pointCoord.name = name;
			}
			final Matcher matcherLat = patternLat.matcher(pointString);
			if (matcherLat.find()) {
				tmp = cgBase.parseCoordinate(matcherLat.group(1).trim(), "lat");
				pointCoord.latitude = (Double) tmp.get("coordinate");
			}
			final Matcher matcherLon = patternLon.matcher(pointString);
			if (matcherLon.find()) {
				tmp = cgBase.parseCoordinate(matcherLon.group(1).trim(), "lon");
				pointCoord.longitude = (Double) tmp.get("coordinate");
			}
			final Matcher matcherDifficulty = patternDifficulty.matcher(pointString);
			if (matcherDifficulty.find()) {
				pointCoord.difficulty = new Float(matcherDifficulty.group(1)
						.trim());
			}
			final Matcher matcherTerrain = patternTerrain.matcher(pointString);
			if (matcherTerrain.find()) {
				pointCoord.terrain = new Float(matcherTerrain.group(1).trim());
			}
			final Matcher matcherContainer = patternContainer.matcher(pointString);
			if (matcherContainer.find()) {
				final int size = Integer.parseInt(matcherContainer.group(1)
						.trim());

				if (size == 1) {
					pointCoord.size = "not chosen";
				} else if (size == 2) {
					pointCoord.size = "micro";
				} else if (size == 3) {
					pointCoord.size = "regular";
				} else if (size == 4) {
					pointCoord.size = "large";
				} else if (size == 5) {
					pointCoord.size = "virtual";
				} else if (size == 6) {
					pointCoord.size = "other";
				} else if (size == 8) {
					pointCoord.size = "small";
				} else {
					pointCoord.size = "unknown";
				}
			}

			if (pointCoord.name != null && pointCoord.name.length() > 0) {
				coords.put(pointCoord.name, pointCoord);
			}
		}

		Log.i(cgSettings.tag,
				"Coordinates found in .loc file: " + coords.size());
		return coords;
	}

	public static long parseLoc(cgeoapplication app, File file, int listId,
			Handler handler) {
		cgSearch search = new cgSearch();
		long searchId = 0l;

		try {
			HashMap<String, cgCoord> coords = parseCoordinates(readFile(file).toString());
			final cgCacheWrap caches = new cgCacheWrap();
			for (Entry<String, cgCoord> entry : coords.entrySet()) {
				cgCoord coord = entry.getValue();
				if (coord.geocode == null || coord.geocode.length() == 0 || coord.name == null || coord.name.length() == 0) {
					continue;
				}
				cgCache cache = new cgCache();
				copyCoordToCache(coord, cache);
				caches.cacheList.add(cache);
				
				fixCache(cache);
				cache.reason = listId;
				cache.detailed = false;
				
				app.addCacheToSearch(search, cache);
			}
			caches.totalCnt = caches.cacheList.size();
			showFinishedMessage(handler, search);
		} catch (Exception e) {
			Log.e(cgSettings.tag, "cgBase.parseGPX: " + e.toString());
		}

		Log.i(cgSettings.tag, "Caches found in .gpx file: " + app.getCount(searchId));

		return search.getCurrentId();
	}
}
