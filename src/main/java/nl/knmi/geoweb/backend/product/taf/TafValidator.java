package nl.knmi.geoweb.backend.product.taf;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;

import nl.knmi.adaguc.tools.Debug;
import lombok.Getter;
import lombok.Setter;


@Component
public class TafValidator {
	
	TafSchemaStore tafSchemaStore;
	
	public TafValidator ( final TafSchemaStore tafSchemaStore) throws IOException {
		this.tafSchemaStore = tafSchemaStore;
	}
	
	public TafValidationResult validate(Taf taf) throws IOException, ProcessingException, JSONException, ParseException {
		return validate(taf.toJSON());
	}

	static Map<JsonPointer, String> customMessages; 
	/**
	 * Identifies the prefix for JSON elements in which GeoWeb directives are defined
	 */
	public static final String GEOWEB_DIRECTIVES_ELEMENT_PREFIX = "$geoweb::";

	/**
	 * Identifies the JSON element in which the message for validation errors is defined
	 */
	public static final String GEOWEB_DIRECTIVE_MESSAGE_ELEMENT = GEOWEB_DIRECTIVES_ELEMENT_PREFIX + "messages";

	private static void removeGeowebPrefixedFields(JsonNode jsonNode) {
		jsonNode.findParents(GEOWEB_DIRECTIVE_MESSAGE_ELEMENT).stream()
		.forEach(node -> ((ObjectNode) node).remove(GEOWEB_DIRECTIVE_MESSAGE_ELEMENT));
	}

	private static void harvestFields(JsonNode node, Predicate<String> fieldNamePredicate, JsonPointer parentPointer, Set<FoundJsonField> harvestedSoFar, boolean shouldVisitSubNodes) {

		final JsonPointer localParentPointer = parentPointer == null ? JsonPointer.empty() : parentPointer;
		final Predicate<String> localFieldNamePredicate = fieldNamePredicate != null ? fieldNamePredicate : name -> true;

		if (node == null) {
			return;
		}

		if (node.isObject()) {
			Iterable<Map.Entry<String, JsonNode>> fieldsIterable = () -> node.fields();
			StreamSupport.stream(fieldsIterable.spliterator(), true)
			.forEach(field -> {
				String fieldName = field.getKey();
				JsonPointer childPointer = localParentPointer.append(fieldName);
				if (localFieldNamePredicate.test(fieldName)) {
					harvestedSoFar.add(new FoundJsonField(fieldName, childPointer.parent(), field.getValue(), new ObjectMapper()));
				} else if (shouldVisitSubNodes) {
					harvestFields(field.getValue(), localFieldNamePredicate, childPointer, harvestedSoFar, shouldVisitSubNodes);
				}
			});
		} else if (node.isArray()) {
			IntStream.range(0, node.size())
			.forEach(index -> {
				JsonPointer childPointer = localParentPointer.append(index);
				JsonNode childNode = node.get(index);
				if (childNode.isObject() || childNode.isArray()) {
					harvestFields(childNode, localFieldNamePredicate, childPointer, harvestedSoFar, shouldVisitSubNodes);
				}
			});
		}
	}

	private static Map<String, Set<String>> pointersOfSchemaErrors(JsonNode schema) {
		Map<String, Set<String>> pointers = new HashMap<String, Set<String>>();
		if(schema.isObject()) {
			if (schema.has("schema") && schema.has("keyword")) {
				JsonNode schemaField = schema.get("schema");
				if (schemaField.has("pointer")) {
					String pointer = schemaField.get("pointer").asText();
					String keyword = schema.get("keyword").asText();
					if (pointers.containsKey(pointer) ) {
						Set<String> keywords = pointers.get(pointer);
						keywords.add(keyword);
						pointers.put(pointer, keywords);
					} else {
						pointers.put(pointer, Stream.of(keyword).collect(Collectors.toSet()));
					}
				}
			}

			if (schema.has("reports")) {
				JsonNode subReports = schema.get("reports");
				Iterator<String> subNames = subReports.fieldNames();
				while(subNames.hasNext()) {
					Map<String, Set<String>> subReportErrors = pointersOfSchemaErrors(subReports.get(subNames.next()));
					subReportErrors.forEach((pointer, values) -> {
						if (pointers.containsKey(pointer) ) {
							Set<String> keywords = pointers.get(pointer);
							keywords.addAll(values);
							pointers.put(pointer, keywords);
						} else {
							pointers.put(pointer, values);
						}
					});
				}
			}
		}

		if(schema.isArray()) {
			schema.forEach(s -> {
				Map<String, Set<String>> subReportErrors = pointersOfSchemaErrors(s);
				subReportErrors.forEach((pointer, values) -> {
					if (pointers.containsKey(pointer) ) {
						Set<String> keywords = pointers.get(pointer);
						keywords.addAll(values);
						pointers.put(pointer, keywords);
					} else {
						pointers.put(pointer, values);
					}
				});
			});
		}

		return pointers;
	}

	private static Set<String> findPathInOriginalJson(JsonNode schema, String path) {
		Set<String> pathSet = new HashSet<>();
		if(schema.isObject()) {
			JsonNode schemaField = schema.get("schema");
			if (schemaField.has("pointer")) {
				if(schemaField.get("pointer").asText().equals(path)) {
					pathSet.add(schema.get("instance").get("pointer").asText());
				}
			}
			if (schema.has("reports")) {
				JsonNode subReports = schema.get("reports");
				Iterator<String> subNames = subReports.fieldNames();
				while(subNames.hasNext()) {
					pathSet.addAll(findPathInOriginalJson(subReports.get(subNames.next()), path));
				}
			}
		}
		if(schema.isArray()) {
			schema.forEach(s -> pathSet.addAll(findPathInOriginalJson(s, path)));
		}
		return pathSet;
	}
	
	public static int LCSLength(String a, String b) {
	    int[][] lengths = new int[a.length()+1][b.length()+1];
	    
	    // row 0 and column 0 are initialized to 0 already
	 
	    for (int i = 0; i < a.length(); i++)
	        for (int j = 0; j < b.length(); j++)
	            if (a.charAt(i) == b.charAt(j))
	                lengths[i+1][j+1] = lengths[i][j] + 1;
	            else
	                lengths[i+1][j+1] =
	                    Math.max(lengths[i+1][j], lengths[i][j+1]);
	    return lengths[a.length()][b.length()];
	}

	private static Map<String, Set<String>> convertReportInHumanReadableErrors(ProcessingReport validationReport, Map<String, Map<String, String>> messagesMap) {
		Map<String, Set<String>> errorMessages = new HashMap<>();
		validationReport.forEach(report -> {
			Map<String, Set<String>> errors = pointersOfSchemaErrors(report.asJson());

			// TODO: this is not ideal but filters only relevant errors
			// Removes forecast errors iff there exists an error which contains needle
			errors.entrySet().stream()
			.collect(Collectors.toMap(Entry::getKey, Entry::getValue)).forEach((pointer, keywords) -> {
				if(!messagesMap.containsKey(pointer)) {
					return;
				}
				Map<String, String> messages = messagesMap.get(pointer);
				keywords.forEach(keyword -> {
					if(!messages.containsKey(keyword)) {
						return;
					}
					findPathInOriginalJson(report.asJson(), pointer).forEach(path -> {
						if (!errorMessages.containsKey(path)) {
							errorMessages.put(path, new HashSet<String>(Arrays.asList(messages.get(keyword))));
						} else {
							Set<String> set = errorMessages.get(path);
							set.add(messages.get(keyword));
							errorMessages.put(path, set);
						}
					});
				});
			});
		});
		List<String> keys = errorMessages.keySet().stream().collect(Collectors.toList());
		Map<String, Set<String>> finalErrors = new HashMap<>();
		if(keys.size() == 0) {
			return finalErrors;
		}
		Collections.sort(keys);
		final double SAME_RATIO = 1.0;
		for(int i = 0; i < keys.size(); ++i) {
			for (int j = i + 1; j < keys.size(); ++j) {
				int lcs = LCSLength(keys.get(i), keys.get(j));
				if (((double)lcs / (double)keys.get(i).length()) < SAME_RATIO) {
					finalErrors.put(keys.get(i), errorMessages.get(keys.get(i)));
					break;
				}
			}
		}
		String lastKey = keys.get(keys.size() - 1);
		finalErrors.put(lastKey, errorMessages.get(lastKey));
		return finalErrors;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Map<String, String>> extractMessagesAndCleanseSchema(JsonNode schemaNode) {
		Predicate<String> pred = name -> name.equals(GEOWEB_DIRECTIVE_MESSAGE_ELEMENT);
		Set<FoundJsonField> harvests = new HashSet<>();

		// Pointer in schema to a keyword/message pair
		Map<String, Map<String, String>> messagesMap = new HashMap<>();
		harvestFields(schemaNode, pred, null, harvests, true);
		ObjectMapper _mapper = new ObjectMapper();

		harvests.forEach(harvest -> {
			String path = harvest.getPointer().toString();
			JsonNode rawMessages = harvest.getValue();
			Map<String, String> messages = _mapper.convertValue(rawMessages, HashMap.class);
			messagesMap.put(path, messages);
		});

		// Remove custom fields
		removeGeowebPrefixedFields(schemaNode);
		
		return messagesMap;
	}
	
	public boolean validateSchema(JsonNode schema) throws IOException, ProcessingException {
		JsonNode cpy = schema.deepCopy();
		removeGeowebPrefixedFields(cpy);
		String schemaschemaString = tafSchemaStore.getSchemaSchema();
		ObjectMapper om = new ObjectMapper();
		final JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
		final JsonSchema schemaschema = factory.getJsonSchema(om.readTree(schemaschemaString));
		ProcessingReport validReport = schemaschema.validate(cpy);
		
		return validReport.isSuccess();
	}
	
	public static void enrich(JsonNode input) throws ParseException {
		augmentChangegroupsIncreasingInTime(input);
		augmentOverlappingBecomingChangegroups(input);
		augmentChangegroupDuration(input);
		augmentWindGust(input);
		augmentAscendingClouds(input);
		augmentEndTimes(input);
		augmentVisibilityWeatherRequired(input);
		augmentEnoughWindChange(input);
		augmentCloudNeededRain(input);
	}
	
	private static void augmentCloudNeededRain(JsonNode input) {
		ObjectNode forecast = (ObjectNode) input.get("forecast");
		if (forecast == null || forecast.isNull() || forecast.isMissingNode()) return;

		JsonNode forecastWeather = input.get("forecast").get("weather");
		JsonNode forecastClouds = input.get("forecast").get("clouds");
		if (forecastWeather == null || forecastWeather.isNull() || forecastWeather.isMissingNode()) return;
		if (forecastClouds == null || forecastClouds.isNull() || forecastClouds.isMissingNode()) return;

		processWeatherAndCloudGroup(forecast, forecastWeather, forecastClouds);
		JsonNode changeGroups = input.get("changegroups");
		if (changeGroups == null || changeGroups.isNull() || changeGroups.isMissingNode()) return;

		for (Iterator<JsonNode> change = changeGroups.elements(); change.hasNext(); ) {
			JsonNode changegroup = (ObjectNode) change.next();
			JsonNode changeForecastNode = changegroup.get("forecast");
			if (changeForecastNode == null || changeForecastNode.isMissingNode() || changeForecastNode.isNull()) continue;
			ObjectNode changeForecast = (ObjectNode) changeForecastNode;
			JsonNode changeWeather = changeForecast.get("weather");
			JsonNode changeClouds = changeForecast.get("clouds");
			processWeatherAndCloudGroup(changeForecast, changeWeather, changeClouds);
		}
	}

	private static void processWeatherAndCloudGroup(ObjectNode forecast, JsonNode forecastWeather,
			JsonNode forecastClouds) {
		if (forecastWeather != null && !forecastWeather.asText().equals("NSW")) {
			boolean requiresClouds = false;
			ArrayNode weatherArray = (ArrayNode) forecastWeather;
			for (Iterator<JsonNode> weather = weatherArray.elements(); weather.hasNext(); ) {
				JsonNode weatherDescriptor = weather.next();
				if (weatherDescriptor.has("descriptor") && weatherDescriptor.get("descriptor").asText().equals("showers")) {
					requiresClouds = true;
					break;
				}
			}
			
			if (requiresClouds) {
				if (forecastClouds == null || forecastClouds.asText().equals("NSC")) {
					forecast.put("cloudsNeededAndPresent", false);
				} else {
					ArrayNode cloudsArray = (ArrayNode) forecastClouds;
					forecast.put("cloudsNeededAndPresent", cloudsArray.size() > 0);
				}
			}
		}
	}
	
	private static void augmentEnoughWindChange(JsonNode input) {
		JsonNode forecastNode = input.get("forecast");
		if (forecastNode == null || forecastNode.isNull() || forecastNode.isMissingNode()) return;

		JsonNode forecastWind = forecastNode.get("wind");
		if (forecastWind == null || forecastWind.isNull() || forecastWind.isMissingNode()) return;

		if (forecastWind == null || !forecastWind.has("direction") || !forecastWind.has("speed")) return;
		int forecastWindDirection = forecastWind.get("direction").asInt();
		int forecastWindSpeed = forecastWind.get("speed").asInt();
		JsonNode changeGroups = input.get("changegroups");
		if (changeGroups == null || changeGroups.isNull() || changeGroups.isMissingNode()) return;
		for (Iterator<JsonNode> change = changeGroups.elements(); change.hasNext(); ) {
			ObjectNode changegroup = (ObjectNode) change.next();
			if (!changegroup.has("forecast")) continue;
			ObjectNode changeForecast = (ObjectNode) changegroup.get("forecast");
			if (changeForecast.has("wind")) {
				JsonNode wind = changeForecast.get("wind");
				if (!wind.has("direction") || !wind.has("speed")) continue;

				int changeWindDirection = wind.get("direction").asInt();
				int changeWindSpeed = wind.get("speed").asInt();
				int speedDifference = Math.abs(changeWindSpeed - forecastWindSpeed);
				int directionDifference = Math.abs(changeWindDirection - forecastWindDirection);
				changegroup.put("windEnoughDifference", directionDifference >= 30 || speedDifference >= 5);
			}
		}
	}
	
	private static void augmentVisibilityWeatherRequired(JsonNode input) {
		JsonNode changeGroups = input.get("changegroups");
		if (changeGroups == null || changeGroups.isNull() || changeGroups.isMissingNode()) return;
		for (Iterator<JsonNode> change = changeGroups.elements(); change.hasNext(); ) {
			ObjectNode changegroup = (ObjectNode) change.next();
			JsonNode visibilityNode = changegroup.findValue("visibility");
			if (visibilityNode == null || !visibilityNode.has("value")) continue;
			int visibility = visibilityNode.get("value").asInt();
			if (visibility <= 5000) {
				JsonNode weather = changegroup.findValue("weather");
				changegroup.put("visibilityWeatherRequiredAndPresent", weather != null && weather.isArray());
			}
		}
	}
	
	private static void augmentEndTimes(JsonNode input) throws ParseException {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));

		JsonNode changeGroups = input.get("changegroups");
		if (changeGroups == null || changeGroups.isNull() || changeGroups.isMissingNode()) return;
		for (Iterator<JsonNode> change = changeGroups.elements(); change.hasNext(); ) {
			ObjectNode changegroup = (ObjectNode) change.next();
			JsonNode changeStartNode = changegroup.findValue("changeStart");
			if (changeStartNode == null) continue;
			try {
				Date changeStart = formatter.parse(changeStartNode.asText());
				Date changeEnd = null; 
				JsonNode end = changegroup.findValue("changeEnd");
				if (end == null) continue;
				changeEnd = formatter.parse(end.asText());
				changegroup.put("endAfterStart", changeStart.compareTo(changeEnd) < 1);
			} catch (ParseException e) {
				continue;
			}
		}
	}
	
	private static void augmentAscendingClouds(JsonNode input) throws ParseException {
		List<JsonNode> forecasts = input.findParents("clouds");
		for (JsonNode forecast : forecasts) {
			if (forecast == null || forecast.isNull() || forecast.isMissingNode()) continue;
			ObjectNode editableForecast = (ObjectNode) forecast;
			int prevHeight = 0;
			JsonNode node = forecast.findValue("clouds");
			if(node.getClass().equals(String.class) || node.getClass().equals(TextNode.class)) {
				editableForecast.put("cloudsAscending", true);
				continue;
			};
			boolean ascending = true;
			for (JsonNode cloud : node) {
				if (cloud == null || cloud.isNull() || cloud.isMissingNode()) continue;
				ObjectNode cloudNode = (ObjectNode) cloud;
				JsonNode cloudHeight = cloudNode.findValue("height");
				if (cloudHeight == null || cloudHeight.asText().equals("null")) continue;
				int height = Integer.parseInt(cloudHeight.asText());
				
				// If ascending hadn't been previously set to false and the height is greater than the previously seen height
				// we keep it at true, otherwise we set it at false
				if (ascending) {
					if (height < prevHeight) {
						ascending = false;
					}
				}
				prevHeight = height;
			}
			editableForecast.put("cloudsAscending", ascending);
		}
	}
	
	private static void augmentWindGust(JsonNode input) throws ParseException {
		List<JsonNode> windGroups = input.findValues("wind");
		if (windGroups == null) return;
		for(JsonNode node : windGroups) {
			ObjectNode windNode = (ObjectNode) node;
			JsonNode gustField = node.findValue("gusts");
			if(gustField == null) continue;
			try {
				int gust = Integer.parseInt(gustField.asText());
				int windspeed = Integer.parseInt(node.findValue("speed").asText());
				windNode.put("gustFastEnough", gust >= (windspeed + 10));
			} catch (NumberFormatException e) {
				continue;
			}
		}
	}
	
	private static void augmentChangegroupDuration(JsonNode input) throws ParseException {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		JsonNode changeGroups = input.get("changegroups");
		if (changeGroups == null || changeGroups.isNull() || changeGroups.isMissingNode()) return;
		for (Iterator<JsonNode> change = changeGroups.elements(); change.hasNext(); ) {
			ObjectNode changegroup = (ObjectNode) change.next();
			JsonNode changeStartNode = changegroup.findValue("changeStart");
			if (changeStartNode == null) continue;
			try {
				Date changeStart = formatter.parse(changeStartNode.asText());
				Date changeEnd = null; 
				JsonNode end = changegroup.findValue("changeEnd");
				if (end != null) {
					changeEnd = formatter.parse(end.asText());
				} else {
					changeEnd = formatter.parse(input.findValue("validityEnd").asText());
				}
				long diffInMillies = Math.abs(changeEnd.getTime() - changeStart.getTime());
				long diffInHours = TimeUnit.HOURS.convert(diffInMillies, TimeUnit.MILLISECONDS);
				changegroup.put("changeDurationInHours", diffInHours);
			} catch (ParseException e) {
				continue;
			}
		}
	}
	
	private static void augmentOverlappingBecomingChangegroups(JsonNode input) throws ParseException {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));

		List<Date> becmgEndTimes = new ArrayList<Date>();
		JsonNode changeGroups = input.get("changegroups");
		if (changeGroups == null || changeGroups.isNull() || changeGroups.isMissingNode()) return;
		for (Iterator<JsonNode> change = changeGroups.elements(); change.hasNext(); ) {
			ObjectNode changegroup = (ObjectNode) change.next();
			JsonNode changeType = changegroup.findValue("changeType");
			JsonNode changeStart = changegroup.findValue("changeStart");
			if(changeType == null || changeType.isMissingNode() || changeType.isNull()) continue;
			if(changeStart == null || changeStart.isMissingNode() || changeStart.isNull()) continue;

			String type = changegroup.findValue("changeType").asText();
			if (!"BECMG".equals(type)) continue;
			Date becmgStart = formatter.parse(changegroup.findValue("changeStart").asText());
			boolean overlap = false;
			for (Date otherEnd : becmgEndTimes) {
				if (becmgStart.before(otherEnd)) {
					overlap = true;
				}
			}
			becmgEndTimes.add(formatter.parse(changegroup.findValue("changeEnd").asText()));
			changegroup.put("changegroupBecomingOverlaps", overlap);
		}
	}
	
	private static void augmentChangegroupsIncreasingInTime(JsonNode input) throws ParseException {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		Date prevChangeStart;
		try {
			prevChangeStart = formatter.parse(input.findValue("validityStart").asText());
		} catch (ParseException e) {
			return;
		}
		JsonNode changeGroups = input.get("changegroups");
		if (changeGroups == null || changeGroups.isNull() || changeGroups.isMissingNode()) return;
		for (Iterator<JsonNode> change = changeGroups.elements(); change.hasNext(); ) {
			ObjectNode changegroup = (ObjectNode) change.next();
			JsonNode changeStartNode = changegroup.findValue("changeStart");
			if (changeStartNode == null) continue;
			String changeStart = changeStartNode.asText();
			try {
				Date parsedDate = formatter.parse(changeStart);
				boolean comesAfter = parsedDate.after(prevChangeStart);
				changegroup.put("changegroupsAscending", comesAfter);
				prevChangeStart = parsedDate;
			} catch (ParseException e) {
				changegroup.put("changegroupsAscending", false);
			}
		}
	}
	
	public DualReturn performValidation(String schemaFile, String tafStr) throws IOException, ProcessingException {
		return performValidation(schemaFile, ValidationUtils.getJsonNode(tafStr));
	}
	
	private class DualReturn {
		@Getter
		@Setter
		private ProcessingReport report = null;
		
		@Getter
		@Setter
		private Map<String, Map<String, String>> messages = null;
		
		public DualReturn(ProcessingReport report, Map<String, Map<String, String>> messages) {
			this.report = report;
			this.messages = messages;
		}
	}

	public DualReturn performValidation(String schemaFile, JsonNode jsonNode) throws IOException, ProcessingException {
		JsonNode schemaNode = ValidationUtils.getJsonNode(schemaFile);
		// This extracts the custom error messages in the JSONSchema and removes them
		// This is necessary because otherwise the schema is invalid and thus always needs to happen.
		// The messages map is a mapping from a pointer in the JSONSchema to another map
		// This is a map from keyword to human-readable message. So the full structure is something like
		// /definitions/vertical_visibilitiy --> minimum -> "Vertical visibility must be greater than 0 meters"
		//	                                 |-> maximum -> "Vertical visibility must be less than 1000 meters"
		//	                                 |-> multipleOf -> "Vertical visibility must a multiple of 30 meters"
		Map<String, Map<String, String>> messagesMap = extractMessagesAndCleanseSchema(schemaNode);
		// Construct the final schema based on the filtered schema
		final JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
		final JsonSchema schema = factory.getJsonSchema(schemaNode);
		// Try and validate the TAF
		ProcessingReport validationReport = schema.validate(jsonNode);
		return new DualReturn(validationReport, messagesMap);
	}
	
	private static void removeLastEmptyChangegroup(JsonNode jsonNode) {
		if (jsonNode == null) return;
		JsonNode changegroupsNode = jsonNode.at("/changegroups");
		if (changegroupsNode == null || changegroupsNode.isMissingNode() || changegroupsNode.isNull()) return;
		ArrayNode changegroups = (ArrayNode)changegroupsNode;
		// If there are no changegroups we are done
		if (changegroups == null || changegroups.size() <= 1) return;
		for (int i = 0; i < changegroups.size(); i++) {
			JsonNode elem = changegroups.get(i);
			if (elem == null || elem.isMissingNode() || elem.isNull() || elem.size() == 0) {
				changegroups.remove(i);
			}
		}
		if (changegroups.size() <= 1) return;
		JsonNode lastChangegroup = changegroups.get(changegroups.size() - 1);

		// If the last changegroup is null or {} we can throw it away
		if (lastChangegroup == null || lastChangegroup.size() == 0) {
			changegroups.remove(changegroups.size() - 1);
			return;
		};
		ObjectNode lastForecast = (ObjectNode)lastChangegroup.get("forecast");

		// If the forecast in the last changegroup is null or {} we can throw it away
		if (lastForecast == null || lastForecast.size() == 0) {
			changegroups.remove(changegroups.size() - 1);
			return;
		};
		
		// If it is a well-formed changegroup but has no content, we can throw it away
		if ((!lastChangegroup.has("changeType") || lastChangegroup.get("changeType").asText().equals("")) && 
			!lastChangegroup.has("changeStart") && 
			!lastChangegroup.has("changeEnd") &&
			lastForecast.get("wind").size() == 0 && lastForecast.get("visibility").size() == 0 &&
			lastForecast.get("weather").asText().equals("NSW") && lastForecast.get("clouds").asText().equals("NSC")) {
			changegroups.remove(changegroups.size() - 1);
		}
	}
	
	public TafValidationResult validate(String tafStr) throws  ProcessingException, JSONException, IOException, ParseException {
		String schemaFile = tafSchemaStore.getLatestTafSchema();
		JsonNode jsonNode = ValidationUtils.getJsonNode(tafStr);

		removeLastEmptyChangegroup(jsonNode);
		System.out.println(jsonNode);

		DualReturn ret = performValidation(schemaFile, jsonNode);
		ProcessingReport validationReport = ret.getReport();
		Map<String, Map<String, String>> messagesMap = ret.getMessages();

		// If the validation is not successful we try to find all relevant errors
		// They are relevant if the error path in the schema exist in the possibleMessages set
		if (validationReport == null) {
			ObjectMapper om = new ObjectMapper();
			return new TafValidationResult(false, (ObjectNode)om.readTree("{\"message\": \"Validation report was null\"}"));
		}
		Map<String, Set<String>> errorMessages = convertReportInHumanReadableErrors(validationReport, messagesMap);	
		JsonNode errorJson = new ObjectMapper().readTree("{}");
		if(!validationReport.isSuccess()) {
			String errorsAsJson = new ObjectMapper().writeValueAsString(errorMessages);
			// Try to find all possible errors and map them to the human-readable variants using the messages map
			((ObjectNode)errorJson).setAll((ObjectNode)(ValidationUtils.getJsonNode(errorsAsJson)));
		} 

		// Enrich the JSON with custom data validation, this is validated using a second schema
		enrich(jsonNode);
		String enrichedSchemaFile = tafSchemaStore.getLatestEnrichedTafSchema();
		ret = performValidation(enrichedSchemaFile, jsonNode);
		ProcessingReport enrichedValidationReport = ret.getReport();
		Map<String, Map<String, String>> enrichedMessagesMap = ret.getMessages();
		if (enrichedValidationReport == null) {
			ObjectMapper om = new ObjectMapper();
			return new TafValidationResult(false, (ObjectNode)om.readTree("{\"message\": \"Validation report was null\"}"));
		}
		if(!enrichedValidationReport.isSuccess()) {
			// Try to find all possible errors and map them to the human-readable variants using the messages map
			// Append them to any previous errors, if any
			Map<String, Set<String>> enrichedErrorMessages = convertReportInHumanReadableErrors(enrichedValidationReport, enrichedMessagesMap);
			String errorsAsJson = new ObjectMapper().writeValueAsString(enrichedErrorMessages);
			((ObjectNode)errorJson).setAll((ObjectNode)ValidationUtils.getJsonNode(errorsAsJson));
		}
		
		// If everything is okay, return true as succeeded with null as errors
		if (enrichedValidationReport.isSuccess() && validationReport.isSuccess()) {
			return new TafValidationResult(true, null);
		}
		return new TafValidationResult(false, (ObjectNode)errorJson);
	}
}
