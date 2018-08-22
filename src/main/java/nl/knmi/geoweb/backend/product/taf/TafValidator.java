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
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration;
import com.github.fge.jsonschema.core.load.uri.URITranslatorConfiguration;
import com.github.fge.jsonschema.core.report.ListReportProvider;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;

import lombok.Getter;
import lombok.Setter;
import nl.knmi.adaguc.tools.Debug;

@Component
public class TafValidator {

	//	@Autowired
	//	@Qualifier("tafObjectMapperBean")
	private ObjectMapper objectMapper;

	TafSchemaStore tafSchemaStore;

	public TafValidator(final TafSchemaStore tafSchemaStore, ObjectMapper om) throws IOException {
		this.tafSchemaStore = tafSchemaStore;
		this.objectMapper=om;
	}

	public TafValidationResult validate(Taf taf)
			throws IOException, ProcessingException, JSONException, ParseException {
		return validate(taf.toJSON(objectMapper));
	}

	static Map<JsonPointer, String> customMessages;
	/**
	 * Identifies the prefix for JSON elements in which GeoWeb directives are
	 * defined
	 */
	public static final String GEOWEB_DIRECTIVES_ELEMENT_PREFIX = "$geoweb::";

	/**
	 * Identifies the JSON element in which the message for validation errors is
	 * defined
	 */
	public static final String GEOWEB_DIRECTIVE_MESSAGE_ELEMENT = GEOWEB_DIRECTIVES_ELEMENT_PREFIX + "messages";

	private static void removeGeowebPrefixedFields(JsonNode jsonNode) {
		jsonNode.findParents(GEOWEB_DIRECTIVE_MESSAGE_ELEMENT).stream()
		.forEach(node -> ((ObjectNode) node).remove(GEOWEB_DIRECTIVE_MESSAGE_ELEMENT));
	}

	private static long modularAbs(long n, long mod) {
		n %= mod;
		if (n < 0)
			n += mod;
		return n;
	}

	/**
	 * Adds two numbers in modulo arithmetic. This function is safe for large
	 * numbers and won't overflow long.
	 *
	 * @param a
	 * @param b
	 * @param mod
	 *            grater than 0
	 * @return (a+b)%mod
	 */
	public static long add(long a, long b, long mod) {
		if (mod <= 0)
			throw new IllegalArgumentException("Mod argument is not grater then 0");
		a = modularAbs(a, mod);
		b = modularAbs(b, mod);
		if (b > mod - a) {
			return b - (mod - a);
		}
		return (a + b) % mod;
	}

	/**
	 * Subtract two numbers in modulo arithmetic. This function is safe for large
	 * numbers and won't overflow or underflow long.
	 *
	 * @param a
	 * @param b
	 * @param mod
	 *            grater than 0
	 * @return (a-b)%mod
	 */
	public static long subtract(long a, long b, long mod) {
		if (mod <= 0)
			throw new IllegalArgumentException("Mod argument is not grater then 0");
		return add(a, -b, mod);
	}

	private static void harvestFields(JsonNode node, Predicate<String> fieldNamePredicate, JsonPointer parentPointer,
			Set<FoundJsonField> harvestedSoFar, boolean shouldVisitSubNodes) {

		final JsonPointer localParentPointer = parentPointer == null ? JsonPointer.empty() : parentPointer;
		final Predicate<String> localFieldNamePredicate = fieldNamePredicate != null ? fieldNamePredicate
				: name -> true;

				if (node == null) {
					return;
				}

				if (node.isObject()) {
					Iterable<Map.Entry<String, JsonNode>> fieldsIterable = () -> node.fields();
					StreamSupport.stream(fieldsIterable.spliterator(), true).forEach(field -> {
						String fieldName = field.getKey();
						JsonPointer childPointer = localParentPointer.append(fieldName);
						if (localFieldNamePredicate.test(fieldName)) {
							harvestedSoFar.add(
									new FoundJsonField(fieldName, childPointer.parent(), field.getValue(), new ObjectMapper()));
						} else if (shouldVisitSubNodes) {
							harvestFields(field.getValue(), localFieldNamePredicate, childPointer, harvestedSoFar,
									shouldVisitSubNodes);
						}
					});
				} else if (node.isArray()) {
					IntStream.range(0, node.size()).forEach(index -> {
						JsonPointer childPointer = localParentPointer.append(index);
						JsonNode childNode = node.get(index);
						if (childNode.isObject() || childNode.isArray()) {
							harvestFields(childNode, localFieldNamePredicate, childPointer, harvestedSoFar,
									shouldVisitSubNodes);
						}
					});
				}
	}

	private static Map<String, Set<String>> pointersOfSchemaErrors(JsonNode schema) {
		Map<String, Set<String>> pointers = new HashMap<String, Set<String>>();
		if (schema.isObject()) {
			if (schema.has("schema") && schema.has("keyword")) {
				JsonNode schemaField = schema.get("schema");
				if (schemaField.has("pointer")) {
					String pointer = schemaField.get("pointer").asText();
					String keyword = schema.get("keyword").asText();
					if (pointers.containsKey(pointer)) {
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
				while (subNames.hasNext()) {
					Map<String, Set<String>> subReportErrors = pointersOfSchemaErrors(subReports.get(subNames.next()));
					subReportErrors.forEach((pointer, values) -> {
						if (pointers.containsKey(pointer)) {
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

		if (schema.isArray()) {
			schema.forEach(s -> {
				Map<String, Set<String>> subReportErrors = pointersOfSchemaErrors(s);
				subReportErrors.forEach((pointer, values) -> {
					if (pointers.containsKey(pointer)) {
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
		if (schema.isObject()) {
			JsonNode schemaField = schema.get("schema");
			if (schemaField.has("pointer")) {
				if (schemaField.get("pointer").asText().equals(path)) {
					if (schema.get("instance") != null) {
						JsonNode ptr = schema.get("instance").get("pointer");
						if (ptr != null) {
							pathSet.add(ptr.asText());
						}
					}
				}
			}
			if (schema.has("reports")) {
				JsonNode subReports = schema.get("reports");
				Iterator<String> subNames = subReports.fieldNames();
				while (subNames.hasNext()) {
					pathSet.addAll(findPathInOriginalJson(subReports.get(subNames.next()), path));
				}
			}
		}
		if (schema.isArray()) {
			schema.forEach(s -> pathSet.addAll(findPathInOriginalJson(s, path)));
		}
		return pathSet;
	}

	public static int LCSLength(String a, String b) {
		int[][] lengths = new int[a.length() + 1][b.length() + 1];

		// row 0 and column 0 are initialized to 0 already

		for (int i = 0; i < a.length(); i++)
			for (int j = 0; j < b.length(); j++)
				if (a.charAt(i) == b.charAt(j))
					lengths[i + 1][j + 1] = lengths[i][j] + 1;
				else
					lengths[i + 1][j + 1] = Math.max(lengths[i + 1][j], lengths[i][j + 1]);
		return lengths[a.length()][b.length()];
	}

	private static Map<String, Set<String>> convertReportInHumanReadableErrors(ProcessingReport validationReport,
			Map<String, Map<String, String>> messagesMap) {
		Map<String, Set<String>> errorMessages = new HashMap<>();
		validationReport.forEach(report -> {
			Map<String, Set<String>> errors = pointersOfSchemaErrors(report.asJson());

			// TODO: this is not ideal but filters only relevant errors
			// Removes forecast errors iff there exists an error which contains needle
			errors.entrySet().stream().collect(Collectors.toMap(Entry::getKey, Entry::getValue))
			.forEach((pointer, keywords) -> {
				if (!messagesMap.containsKey(pointer)) {
					return;
				}
				Map<String, String> messages = messagesMap.get(pointer);

				keywords.forEach(keyword -> {
					if (!messages.containsKey(keyword)) {
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
		if (keys.size() == 0) {
			return finalErrors;
		}
		Collections.sort(keys);
		final double SAME_RATIO = 1.0;
		for (int i = 0; i < keys.size(); ++i) {
			for (int j = i + 1; j < keys.size(); ++j) {
				int lcs = LCSLength(keys.get(i), keys.get(j));
				if (((double) lcs / (double) keys.get(i).length()) < SAME_RATIO) {
					finalErrors.put(keys.get(i), errorMessages.get(keys.get(i)));
					break;
				}
			}
		}
		String lastKey = keys.get(keys.size() - 1);
		finalErrors.put(lastKey, errorMessages.get(lastKey));
		return finalErrors;
	}

	private static Map<String, Map<String, String>> extractMessagesAndCleanseSchema(List<Resource> schemaNodes) {
		return schemaNodes.stream().map(schemaNodePath -> {
			try {
				return extractMessagesAndCleanseSchema(ValidationUtils.getJsonNode(schemaNodePath.getFile()));
			} catch (IOException e) {
				return null;
			}
		}).collect(Collectors.toList())
				.stream()
				.flatMap(m -> m.entrySet().stream())
				.collect(Collectors.toMap(Entry::getKey, Entry::getValue));
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

	private List<Resource> discoverSchemata(String schemaResourceLocation) {
		//		log.debug("Discovering all available validation schemata... ");
		List<Resource> resultList = new ArrayList<Resource>();
		try {
			String locationPattern = "file:" + schemaResourceLocation + "**/*.json";
			//			log.debug("Exploring schemata location: [{}]", locationPattern);
			PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(
					getClass().getClassLoader());
			resultList = Arrays.asList(resolver.getResources(locationPattern));
			if (resultList.isEmpty()) {
				throw new Exception(String.format("Empty files list while discovering schemata"));
			}
		} catch (Exception exception) {
			throw new IllegalStateException(
					String.format("Could not discover the configuration files containing the validation rules"));
		}
		return resultList;
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

	public static void enrich(JsonNode input) throws ParseException, JsonProcessingException, IOException {
		augmentChangegroupsIncreasingInTime(input);
		augmentOverlappingBecomingChangegroups(input);
		augmentChangegroupDuration(input);					
		augmentWindGust(input);								
		augmentAscendingHeightClouds(input);						//Done
		augmentAmountCoverageClouds(input);
		augmentEndTimes(input);
		augmentVisibilityWeatherRequired(input);
		augmentEnoughWindChange(input);						//Done
		augmentCloudNeededRainOrModifierNecessary(input);
		augmentMaxVisibility(input);
		augmentNonRepeatingChanges(input);
		augmentMaxVerticalVisibility(input);
		//		Debug.println(input.toString());
	}

	private static void augmentNonRepeatingChanges(JsonNode input) throws JsonProcessingException, IOException {
		ObjectNode currentForecast = (ObjectNode) input.get("forecast");
		if (currentForecast == null || currentForecast.isNull() || currentForecast.isMissingNode())
			return;
		JsonNode currentWeather = currentForecast.get("weather");

		// If the base forecast contains no weather group, this means NSW.
		// This is because space on punch cards is expensive. Because that matters here.
		if (currentWeather == null || currentWeather.isNull() || currentWeather.isMissingNode()) {
			currentForecast.setAll((ObjectNode) (new ObjectMapper().readTree("{\"weather\":\"NSW\"}")));
		}
		JsonNode changeGroups = input.get("changegroups");
		if (changeGroups == null || changeGroups.isNull() || changeGroups.isMissingNode())
			return;

		for (Iterator<JsonNode> change = changeGroups.elements(); change.hasNext();) {
			JsonNode nextNode = change.next(); 
			if (nextNode == null || nextNode == NullNode.getInstance()) continue;
			ObjectNode changegroup = (ObjectNode) nextNode;

			ObjectNode changeForecast = (ObjectNode) changegroup.get("forecast");
			if (changeForecast == null || changeForecast.isNull() || changeForecast.isMissingNode())
				continue;

			String changeGroupChangeAsText = "";
			if (changegroup.get("changeType") != null ) {
				changeGroupChangeAsText = changegroup.get("changeType").asText();
			}
//			if (!changeGroupChangeAsText.equals("BECMG") && !changeGroupChangeAsText.equals("TEMPO")) {
//				continue;
//			}

			boolean nonRepeatingChange = false;

			JsonNode forecastWind = currentForecast.get("wind");
			JsonNode changeWind = changeForecast.get("wind");

			JsonNode forecastVisibility = currentForecast.get("visibility");
			JsonNode changeVisibility = changeForecast.get("visibility");

			JsonNode forecastWeather = currentForecast.get("weather");
			JsonNode changeWeather = changeForecast.get("weather");

			JsonNode forecastClouds = currentForecast.get("clouds");
			JsonNode changeClouds = changeForecast.get("clouds");

			if (forecastWind != null && !forecastWind.isNull() && !forecastWind.isMissingNode())
				nonRepeatingChange |= forecastWind.equals(changeWind);
			if (forecastVisibility != null && !forecastVisibility.isNull() && !forecastVisibility.isMissingNode())
				nonRepeatingChange |= forecastVisibility.equals(changeVisibility);
			if (forecastWeather != null && !forecastWeather.isNull() && !forecastWeather.isMissingNode())
				nonRepeatingChange |= forecastWeather.equals(changeWeather);
			if (forecastClouds != null && !forecastClouds.isNull() && !forecastClouds.isMissingNode())
				nonRepeatingChange |= forecastClouds.equals(changeClouds);

			changegroup.put("repeatingChange", nonRepeatingChange);
			JsonNode changeType = changegroup.get("changeType");
			if (changeType != null && !changeType.isNull() && !changeType.isMissingNode()
					&& !changeType.asText().startsWith("PROB") && !changeType.asText().equalsIgnoreCase("TEMPO")) {
				currentForecast = changeForecast;
			}

		}
	}

	/**
	 * Checks if visibility is in range for either a changegroup (weatherGroup) or forecast (weatherGroup)
	 * @param weatherGroup
	 * @param forecast
	 * @param visibility
	 */
	private static void checkVisibilityWithinLimit (JsonNode weatherGroup, ObjectNode forecast, int visibility ){
		if (!weatherGroup.has("phenomena"))
			return;
		ArrayNode phenomena = (ArrayNode) weatherGroup.get("phenomena");
		boolean isFoggy = StreamSupport.stream(phenomena.spliterator(), false)
				.anyMatch(phenomenon -> phenomenon.asText().equals("fog"));
		if (isFoggy) {
			if (!weatherGroup.has("descriptor")) {
				forecast.put("visibilityAndFogWithoutDescriptorWithinLimit", visibility <= 1000);
			} else {
				String descriptor = weatherGroup.get("descriptor").asText();
				if (descriptor.equals("freezing")) {
					forecast.put("visibilityWithinLimit", visibility <= 1000);
				} else if (descriptor.equals("shallow")) {
					forecast.put("visibilityWithinLimit", visibility > 1000); // TODO Check > vs >=
				} else {
					forecast.put("visibilityWithinLimit", true);
				}
			}
		}
		if (StreamSupport.stream(phenomena.spliterator(), false)
				.anyMatch(phenomenon -> phenomenon.asText().equals("smoke")
						|| phenomenon.asText().equals("widespread dust") || phenomenon.asText().equals("sand")
						|| phenomenon.asText().equals("volcanic ash"))) {
			forecast.put("visibilityWithinLimit", visibility <= 5000);
		}

		if (StreamSupport.stream(phenomena.spliterator(), false)
				.anyMatch(phenomenon -> phenomenon.asText().equals("mist"))) {
			forecast.put("visibilityWithinLimit", visibility >= 1000 && visibility <= 5000);
		}

		if (StreamSupport.stream(phenomena.spliterator(), false)
				.anyMatch(phenomenon -> phenomenon.asText().equals("haze"))) {
			forecast.put("visibilityWithinLimit", visibility <= 5000);
		}	
	}

	private static void augmentMaxVisibility(JsonNode input) {
		//		Debug.println("Augmenting max visibility");
		ObjectNode forecast = (ObjectNode) input.get("forecast");
		if (forecast == null || forecast.isNull() || forecast.isMissingNode()) {
			Debug.println("augmentMaxVisibility: No forecast");
			return;
		}


		JsonNode forecastWeather = input.get("forecast").get("weather");
		JsonNode forecastVisibility = input.get("forecast").get("visibility");
		if (forecastWeather != null && !forecastWeather.isNull() && !forecastWeather.isMissingNode()
				&& forecastVisibility != null && !forecastVisibility.isNull() && !forecastVisibility.isMissingNode()) {
			int visibility = forecastVisibility.get("value").asInt();
			for (Iterator<JsonNode> weatherNode = forecastWeather.elements(); weatherNode.hasNext();) {
				JsonNode nextNode = weatherNode.next();
				if (nextNode == null || nextNode == NullNode.getInstance()) continue;
				JsonNode weatherGroup = (ObjectNode) nextNode;
				checkVisibilityWithinLimit (weatherGroup, forecast, visibility);
			}
		}


		JsonNode changeGroups = input.get("changegroups");
		if (changeGroups == null || changeGroups.isNull() || changeGroups.isMissingNode()) 
			return;

		for (Iterator<JsonNode> change = changeGroups.elements(); change.hasNext();) {
			JsonNode nextNode = change.next(); 
			if (nextNode == null || nextNode == NullNode.getInstance()) continue;
			ObjectNode changegroup = (ObjectNode) nextNode;


			ObjectNode changeForecast = (ObjectNode) changegroup.get("forecast");
			if (changeForecast == null || changeForecast.isNull() || changeForecast.isMissingNode())
				return;

			JsonNode changeWeather = changeForecast.get("weather");
			JsonNode changeVisibility = changeForecast.get("visibility");

			if ((changeWeather == null || changeWeather.isNull() || changeWeather.isMissingNode())
					&& (changeVisibility == null || changeVisibility.isNull() || changeVisibility.isMissingNode()))
				return;

			String changeGroupChangeAsText = "";
			if (changegroup.get("changeType") != null ) {
				changeGroupChangeAsText = changegroup.get("changeType").asText();
			}
			if (changeGroupChangeAsText.equals("BECMG") || changeGroupChangeAsText.equals("TEMPO")) {
				if (changeWeather == null || changeWeather.isNull() || changeWeather.isMissingNode()) {
					changeWeather = forecastWeather;
				}
				if (changeVisibility == null || changeVisibility.isNull() || changeVisibility.isMissingNode()) {
					changeVisibility = forecastVisibility;
				}
			}
			if (changeWeather == null || changeVisibility == null)
				continue;
			int visibility = changeVisibility.get("value").asInt();
			for (Iterator<JsonNode> weatherNode = changeWeather.elements(); weatherNode.hasNext();) {
				JsonNode weatherGroup = (ObjectNode) weatherNode.next();
				checkVisibilityWithinLimit (weatherGroup, changeForecast, visibility);
			}

			if (changegroup.get("changeType") != null && !changegroup.get("changeType").asText().startsWith("PROB")
					&& !changegroup.get("changeType").asText().equalsIgnoreCase("TEMPO")) {
				forecastWeather = changeWeather;
				forecastVisibility = changeVisibility;
			}
		}
	}	

	private static void checkVerticalVisibilityWithinLimit (JsonNode weatherGroup, ObjectNode forecast, int visibility ){
		if (!weatherGroup.has("phenomena"))
			return;
		ArrayNode phenomena = (ArrayNode) weatherGroup.get("phenomena");
		boolean isFoggy = StreamSupport.stream(phenomena.spliterator(), false)
				.anyMatch(phenomenon -> phenomenon.asText().equals("fog"));
		boolean isPrecip = StreamSupport.stream(phenomena.spliterator(), false)
				.anyMatch(phenomenon -> phenomenon.asText().equals("rain"));
		if (isFoggy) {
			forecast.put("verticalVisibilityAndFogWithinLimit", visibility <= 5);
		} else if (isPrecip) {
			forecast.put("verticalVisibilityAndPrecipitationWithinLimit", visibility <= 10);
		}
	}

	private static void augmentMaxVerticalVisibility(JsonNode input) {
		if (!checkIfNodeHasValue(input.get("forecast")) || 
				!checkIfNodeHasValue(input.get("forecast").get("vertical_visibility")))return;
		{
			JsonNode forecastWeather = input.get("forecast").get("weather");
			JsonNode forecastVerticalVisibility = input.get("forecast").get("vertical_visibility");
			if (forecastWeather != null && !forecastWeather.isNull() && !forecastWeather.isMissingNode()
					&& forecastVerticalVisibility != null && !forecastVerticalVisibility.isNull() && !forecastVerticalVisibility.isMissingNode()) {
				int visibility = forecastVerticalVisibility.asInt();
				for (Iterator<JsonNode> weatherNode = forecastWeather.elements(); weatherNode.hasNext();) {
					JsonNode nextNode = weatherNode.next();
					if (nextNode == null || nextNode == NullNode.getInstance()) continue;
					JsonNode weatherGroup = (ObjectNode) nextNode;
					checkVerticalVisibilityWithinLimit(weatherGroup, (ObjectNode) input.get("forecast"), visibility);
				}
			}
		}

		JsonNode changeGroups = input.get("changegroups");
		if (changeGroups == null || changeGroups.isNull() || changeGroups.isMissingNode()) 
			return;

		for (Iterator<JsonNode> change = changeGroups.elements(); change.hasNext();) {
			JsonNode nextNode = change.next(); 
			if (nextNode == null || nextNode == NullNode.getInstance()) continue;
			ObjectNode changegroup = (ObjectNode) nextNode;

			ObjectNode changeForecast = (ObjectNode) changegroup.get("forecast");
			if (changeForecast == null || changeForecast.isNull() || changeForecast.isMissingNode())
				return;

			JsonNode changeWeather = changeForecast.get("weather");
			JsonNode changeVisibility = changeForecast.get("visibility");
			if (changeWeather != null && !changeWeather.isNull() && !changeWeather.isMissingNode()
					&& changeVisibility != null && !changeVisibility.isNull() && !changeVisibility.isMissingNode()) {
				int visibility = changeVisibility.asInt();
				for (Iterator<JsonNode> weatherNode = changeWeather.elements(); weatherNode.hasNext();) {
					JsonNode weatherGroup = (ObjectNode) weatherNode.next();
					checkVerticalVisibilityWithinLimit (weatherGroup, changeForecast, visibility);
				}
			}


		}
	}

	private static void augmentCloudNeededRainOrModifierNecessary(JsonNode input) {
		ObjectNode forecast = (ObjectNode) input.get("forecast");
		if (forecast == null || forecast.isNull() || forecast.isMissingNode())
			return;

		JsonNode forecastWeather = input.get("forecast").get("weather");
		JsonNode forecastClouds = input.get("forecast").get("clouds");

		processWeatherAndCloudGroup(forecast, forecastWeather, forecastClouds);
		JsonNode changeGroups = input.get("changegroups");
		if (changeGroups == null || changeGroups.isNull() || changeGroups.isMissingNode())
			return;

		for (Iterator<JsonNode> change = changeGroups.elements(); change.hasNext();) {
			JsonNode nextNode = change.next(); 
			if (nextNode == null || nextNode == NullNode.getInstance()) continue;
			ObjectNode changegroup = (ObjectNode) nextNode;


			if (!changegroup.has("forecast")) {
				continue;
			}
			JsonNode changeForecastNode = changegroup.get("forecast");
			if (changeForecastNode.isNull() || changeForecastNode == null || changeForecastNode.isMissingNode()) {
				continue;
			}
			ObjectNode changeForecast = (ObjectNode) changeForecastNode;
			JsonNode changeWeather = changeForecast.get("weather");
			if (changeWeather == null) {
				changeWeather = forecastWeather;
			}
			JsonNode changeClouds = changeForecast.get("clouds");
			if (changeClouds == null) {
				changeClouds = forecastClouds;
			}

			processWeatherAndCloudGroup(changeForecast, changeWeather, changeClouds);

			if (changegroup.has("changeType") && !changegroup.get("changeType").asText().startsWith("PROB")
					&& !changegroup.get("changeType").asText().equalsIgnoreCase("TEMPO")) {
				forecastWeather = changeWeather;
				forecastClouds = changeClouds;
			}
		}
	}

	private static void processWeatherAndCloudGroup(ObjectNode forecast, JsonNode forecastWeather,
			JsonNode forecastClouds) {
		if (forecastWeather != null && !forecastWeather.asText().equals("NSW")) {
			boolean requiresClouds = false;
			boolean requiresCB = false;
			boolean requiresCBorTCU = false;
			boolean rainOrThunderstormPresent = false;
			ArrayNode weatherArray = (ArrayNode) forecastWeather;
			for (Iterator<JsonNode> weather = weatherArray.elements(); weather.hasNext();) {
				JsonNode weatherDescriptor = weather.next();
				if (weatherDescriptor.has("descriptor")
						&& weatherDescriptor.get("descriptor").asText().equals("showers")) {
					requiresClouds = true;
					requiresCBorTCU = true;
					rainOrThunderstormPresent = true;
				}
				if (weatherDescriptor.has("descriptor")
						&& weatherDescriptor.get("descriptor").asText().equals("thunderstorm")) {
					requiresCB = true;
					rainOrThunderstormPresent = true;
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

			if (requiresCB) {
				if (forecastClouds == null || forecastClouds.asText().equals("NSC")) {
					forecast.put("cloudsCBNeededAndPresent", false);
				} else {
					ArrayNode cloudsArray = (ArrayNode) forecastClouds;
					forecast.put("cloudsCBNeededAndPresent", StreamSupport.stream(cloudsArray.spliterator(), true)
							.anyMatch(cloud -> cloud.has("mod") && cloud.get("mod").asText().equals("CB")));
				}
			}
			if (requiresCBorTCU) {
				if (forecastClouds == null || forecastClouds.asText().equals("NSC")) {
					forecast.put("cloudsCBorTCUNeededAndPresent", false);
				} else {
					ArrayNode cloudsArray = (ArrayNode) forecastClouds;
					forecast.put("cloudsCBorTCUNeededAndPresent",
							StreamSupport.stream(cloudsArray.spliterator(), true)
							.anyMatch(cloud -> cloud.has("mod") && (cloud.get("mod").asText().equals("CB")
									|| cloud.get("mod").asText().equals("TCU"))));
				}
			}
			if (forecastClouds != null && forecastClouds.isArray()) {
				ArrayNode cloudsArray = (ArrayNode) forecastClouds;
				boolean modifierPresent = StreamSupport.stream(cloudsArray.spliterator(), true)
						.anyMatch(cloud -> cloud.has("mod")
								&& (cloud.get("mod").asText().equals("CB") || cloud.get("mod").asText().equals("TCU")));
				if (modifierPresent) {
					forecast.put("cloudsModifierHasWeatherPresent", rainOrThunderstormPresent);
				}
			}

		} else {
			if (forecastClouds != null && forecastClouds.isArray()) {
				ArrayNode cloudsArray = (ArrayNode) forecastClouds;
				boolean modifierPresent = StreamSupport.stream(cloudsArray.spliterator(), true)
						.anyMatch(cloud -> cloud.has("mod")
								&& (cloud.get("mod").asText().equals("CB") || cloud.get("mod").asText().equals("TCU")));
				forecast.put("cloudsModifierHasWeatherPresent", !modifierPresent);
			}
		}
	}

	private static void augmentEnoughWindChange(JsonNode input) {
		JsonNode forecastNode = input.get("forecast");
		if (forecastNode == null || forecastNode.isNull() || forecastNode.isMissingNode())
			return;

		JsonNode forecastWind = forecastNode.get("wind");
		if (forecastWind == null || forecastWind.isNull() || forecastWind.isMissingNode())
			return;

		if (forecastWind == null || !forecastWind.has("direction") || !forecastWind.has("speed"))
			return;


		String unit = forecastWind.get("unit").asText();

		int forecastWindDirection = forecastWind.get("direction").asInt();
		int forecastWindSpeed = forecastWind.get("speed").asInt();
		//		// TODO: MP @ 18-06-2018, Where is forecastGust needed for?
		//		JsonNode forecastGustNode = forecastWind.get("gusts");
		//		boolean forecastGust = forecastGustNode == null || forecastGustNode.isNull() || forecastGustNode.isMissingNode()
		//				|| forecastGustNode.asInt() == 0;
		//		Debug.println("forecastGust == " + forecastGust);
		JsonNode changeGroups = input.get("changegroups");
		if (changeGroups == null || changeGroups.isNull() || changeGroups.isMissingNode())
			return;
		for (Iterator<JsonNode> change = changeGroups.elements(); change.hasNext();) {
			boolean becomesGusty = false;
			JsonNode nextNode = change.next(); 
			if (nextNode == null || nextNode == NullNode.getInstance()) continue;
			ObjectNode changegroup = (ObjectNode) nextNode;

			if (!changegroup.has("forecast"))
				continue;

			String changeGroupChangeAsText = "";
			if (changegroup.get("changeType") != null ) {
				changeGroupChangeAsText = changegroup.get("changeType").asText();
			}
//			if (!changeGroupChangeAsText.equals("BECMG") && !changeGroupChangeAsText.equals("TEMPO")) {
//				continue;
//			}
			JsonNode changeForecast =  changegroup.get("forecast");
			if (changeForecast.has("wind")) {
				ObjectNode wind = (ObjectNode) changeForecast.get("wind");
				if (!wind.has("direction") || !wind.has("speed"))
					continue;
				becomesGusty = wind.has("gusts") && wind.get("gusts").asInt() > 0;
				int changeWindDirection = wind.get("direction").asInt();
				int changeWindSpeed = wind.get("speed").asInt();
				String changeUnit = wind.get("unit").asText();
				if (!unit.equals(changeUnit)) {
					// one is in knots and the other in meters per second.
					// compute it such that both are in knots
					double MPS_TO_KNOTS_FACTOR = 1.943844; // 1 mps = 1.943844kt
					if (unit.equalsIgnoreCase("KT")) {
						changeUnit = "KT";
						changeWindSpeed = (int) Math.round((changeWindSpeed * MPS_TO_KNOTS_FACTOR));
					} else {
						unit = "KT";
						changeWindSpeed = (int) Math.round((forecastWindSpeed * MPS_TO_KNOTS_FACTOR));
					}
				}
				int speedDifference = Math.abs(changeWindSpeed - forecastWindSpeed);

				long directionDifference = Math.min(subtract(changeWindDirection, forecastWindDirection, 360),
						subtract(forecastWindDirection, changeWindDirection, 360));
				wind.put("directionDiff", directionDifference);
				wind.put("speedDiff", speedDifference);
				// Wind speed difference should be more than 5 knots or 2 meters per second.

				int limitSpeedDifference = unit.equals("KT") ? 5 : 2;
				wind.put("windEnoughDifference",
						directionDifference >= 30 || speedDifference >= limitSpeedDifference || becomesGusty);
				JsonNode changeType = changegroup.get("changeType");
				if (changeType != null && !changeType.isNull() && !changeType.isMissingNode()
						&& !changeType.asText().startsWith("PROB") && !changeType.asText().equalsIgnoreCase("TEMPO")) {
					forecastWindDirection = changeWindDirection;
					forecastWindSpeed = changeWindSpeed;
				}
			}
		}
	}

	private static void augmentVisibilityWeatherRequired(JsonNode input) {
		ObjectNode forecastNode = (ObjectNode) input.get("forecast");
		if (forecastNode == null || forecastNode.isNull() || forecastNode.isMissingNode())
			return;

		JsonNode forecastWeather = forecastNode.get("weather");
		JsonNode visibilityNode = forecastNode.findValue("visibility");

		if (visibilityNode != null && visibilityNode.get("value") != null
				&& visibilityNode.get("value").asInt() <= 5000)
			forecastNode.put("visibilityWeatherRequiredAndPresent",
					forecastWeather != null && forecastWeather.isArray());

		JsonNode changeGroups = input.get("changegroups");
		if (changeGroups == null || changeGroups.isNull() || changeGroups.isMissingNode())
			return;
		for (Iterator<JsonNode> change = changeGroups.elements(); change.hasNext();) {
			JsonNode nextNode = change.next(); 
			if (nextNode == null || nextNode == NullNode.getInstance()) continue;
			ObjectNode changegroup = (ObjectNode) nextNode;

			ObjectNode changegroupForecast = (ObjectNode) changegroup.get("forecast");
			if (changegroupForecast == null || changegroupForecast.isNull() || changegroupForecast.isMissingNode())
				continue;

			JsonNode changeVisibilityNode = changegroupForecast.findValue("visibility");
			if (changeVisibilityNode == null || !changeVisibilityNode.has("value")) {
				changeVisibilityNode = visibilityNode;
			}
			;
			int visibility;
			if (changeVisibilityNode == null || !changeVisibilityNode.has("value")) {
				// cavok
				visibility = 9999;
			} else {
				visibility = changeVisibilityNode.get("value").asInt();
			}
			JsonNode weather = changegroupForecast.findValue("weather");
			if (weather == null) {
				weather = forecastWeather;
			}
			// TODO: Check visibility <=5000 or 5000
			if (visibility < 5000) {
				changegroupForecast.put("visibilityWeatherRequiredAndPresent", weather != null && weather.isArray());
			}
			JsonNode changeType = changegroup.get("changeType");
			if (changeType != null && !changeType.asText().startsWith("PROB")) {
				if (weather != null) {
					forecastWeather = weather;
				}
				if (changeVisibilityNode != null) {
					visibilityNode = changeVisibilityNode;
				}
			}
		}
	}

	private static void augmentEndTimes(JsonNode input) throws ParseException {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));

		JsonNode changeGroups = input.get("changegroups");
		if (changeGroups == null || changeGroups.isNull() || changeGroups.isMissingNode())
			return;
		for (Iterator<JsonNode> change = changeGroups.elements(); change.hasNext();) {
			JsonNode nextNode = change.next(); 
			if (nextNode == null || nextNode == NullNode.getInstance()) continue;
			ObjectNode changegroup = (ObjectNode) nextNode;

			JsonNode changeStartNode = changegroup.findValue("changeStart");
			if (changeStartNode == null)
				continue;
			try {
				Date changeStart = formatter.parse(changeStartNode.asText());
				Date changeEnd = null;
				JsonNode end = changegroup.findValue("changeEnd");
				if (end == null)
					continue;
				changeEnd = formatter.parse(end.asText());
				changegroup.put("endAfterStart", changeStart.compareTo(changeEnd) < 1);
			} catch (ParseException e) {
				continue;
			}
		}
	}

	private static void augmentAscendingHeightClouds(JsonNode input) throws ParseException {
		List<JsonNode> forecasts = input.findParents("clouds");
		for (JsonNode forecast : forecasts) {
			if (forecast == null || forecast.isNull() || forecast.isMissingNode())
				continue;
			int prevHeight = 0;
			JsonNode cloudsNode=forecast.findValue("clouds");
			if (cloudsNode.getClass().equals(String.class) || cloudsNode.getClass().equals(TextNode.class)) {
				continue;
			}
			ArrayNode node = (ArrayNode) cloudsNode;

			for (Iterator<JsonNode> it = node.iterator(); it.hasNext();) {
				JsonNode nextNode = it.next(); 
				if (nextNode == null || nextNode == NullNode.getInstance()) continue;
				ObjectNode cloudNode = (ObjectNode) nextNode;				

				JsonNode cloudHeight = cloudNode.findValue("height");
				if (cloudHeight == null || cloudHeight.asText().equals("null"))
					continue;
				int height = Integer.parseInt(cloudHeight.asText());
				if (height <= prevHeight) {
					cloudNode.put("cloudsHeightAscending", false);
				}else{
					cloudNode.put("cloudsHeightAscending", true);
				}
				prevHeight = height;
			}
		}
	}

	private static void augmentAmountCoverageClouds(JsonNode input) throws ParseException {
		// FEW -> SCT -> BKN -> OVC
		List<JsonNode> forecasts = input.findParents("clouds");
		for (JsonNode forecast : forecasts) {
			if (forecast == null || forecast.isNull() || forecast.isMissingNode())
				continue;
			String prevAmount = null;
			JsonNode cloudsNode=forecast.findValue("clouds");
			if (cloudsNode.getClass().equals(String.class) || cloudsNode.getClass().equals(TextNode.class)) {
				continue;
			}
			ArrayNode node = (ArrayNode) cloudsNode;

			for (Iterator<JsonNode> it = node.iterator(); it.hasNext();) {
				JsonNode nextNode = it.next(); 
				if (nextNode == null || nextNode == NullNode.getInstance()) continue;
				ObjectNode cloudNode = (ObjectNode) nextNode;				

				JsonNode amountNode = cloudNode.findValue("amount");
				if (amountNode == null || amountNode.asText().equals("null"))
					continue;
				boolean isCBorTCU = false;
				JsonNode modNode = cloudNode.findValue("mod");
				if (modNode != null) {
					if (modNode.asText().equals("CB") || modNode.asText().equals("TCU")) {
						isCBorTCU = true;
					}
				}

				String amount = amountNode.asText();
				if (prevAmount != null && isCBorTCU == false ){
					/* SCT can only be preceded by FEW */
					if ("SCT".equals(amount)){
						if ("FEW".equals(prevAmount) == false){
							cloudNode.put("cloudsAmountAscending", false);
						}
					}
					/* BKN can only be preceded by FEW or SCT */
					if ("BKN".equals(amount)){
						if ("FEW".equals(prevAmount) == false && "SCT".equals(prevAmount) == false){
							cloudNode.put("cloudsAmountAscending", false);
						}
					}
					/* OVC can only be preceded by FEW or SCT or BKN */
					if ("OVC".equals(amount)){
						if ("FEW".equals(prevAmount) == false && "SCT".equals(prevAmount) == false && "BKN".equals(prevAmount) == false){
							cloudNode.put("cloudsAmountAscending", false);
						}
					}
				}
				prevAmount = amount;
			}
		}
	}

	private static void augmentWindGust(JsonNode input) throws ParseException {
		List<JsonNode> windGroups = input.findValues("wind");
		if (windGroups == null)
			return;
		for (JsonNode node : windGroups) {
			ObjectNode windNode = (ObjectNode) node;
			JsonNode gustField = node.findValue("gusts");
			if (gustField == null)
				continue;
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
		if (changeGroups == null || changeGroups.isNull() || changeGroups.isMissingNode())
			return;
		for (Iterator<JsonNode> change = changeGroups.elements(); change.hasNext();) {
			JsonNode nextNode = change.next(); 
			if (nextNode == null || nextNode == NullNode.getInstance()) continue;
			ObjectNode changegroup = (ObjectNode) nextNode;

			JsonNode changeStartNode = changegroup.findValue("changeStart");
			if (changeStartNode == null)
				continue;
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

	/**
	 * Returns true if this node has a value
	 * @param node
	 * @return
	 */
	private static boolean checkIfNodeHasValue(JsonNode node){
		if ( node == null || node.isMissingNode() || node.isNull())return false;
		return true;		
	}

	private static void augmentOverlappingBecomingChangegroups(JsonNode input) throws ParseException {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));

		List<Date> becmgEndTimes = new ArrayList<Date>();
		JsonNode changeGroups = input.get("changegroups");
		if (changeGroups == null || changeGroups.isNull() || changeGroups.isMissingNode())
			return;
		for (Iterator<JsonNode> change = changeGroups.elements(); change.hasNext();) {
			JsonNode nextNode = change.next(); 
			if (nextNode == null || nextNode == NullNode.getInstance()) continue;
			ObjectNode changegroup = (ObjectNode) nextNode;

			JsonNode changeType = changegroup.findValue("changeType");
			JsonNode changeStart = changegroup.findValue("changeStart");
			if (changeType == null || changeType.isMissingNode() || changeType.isNull())
				continue;
			if (changeStart == null || changeStart.isMissingNode() || changeStart.isNull())
				continue;

			/* Check if in range */
			JsonNode changeEndNode = changegroup.findValue("changeEnd");
			JsonNode validityStart = input.findValue("validityStart");
			JsonNode validityEnd = input.findValue("validityEnd");

			if (checkIfNodeHasValue(validityEnd) &&
					checkIfNodeHasValue(validityStart) && 
					checkIfNodeHasValue(changeStart) && 
					checkIfNodeHasValue(changeEndNode)) {

				Date validityStartDate = formatter.parse(input.findValue("validityStart").asText());
				Date validityEndDate = formatter.parse(input.findValue("validityEnd").asText());
				Date changeStartDate = formatter.parse(changegroup.findValue("changeStart").asText());
				Date changeEndDate = formatter.parse(changegroup.findValue("changeEnd").asText());
//				Debug.println(validityStartDate.toGMTString() + "," + validityEndDate.toGMTString() + "," + changeStartDate.toGMTString() + "," + changeEndDate.toGMTString());
				if (changeStartDate.compareTo(validityStartDate) < 0 || changeStartDate.compareTo(validityEndDate) > 0 ||
						changeEndDate.compareTo(validityStartDate) < 0 || changeEndDate.compareTo(validityEndDate) > 0){
					changegroup.put("changegroupDateOutsideRange", false);
				}		else {
					changegroup.put("changegroupDateOutsideRange", true);
				}


			}






			String type = changegroup.findValue("changeType").asText();
			if (!"BECMG".equals(type))
				continue;

			Date becmgStart = formatter.parse(changegroup.findValue("changeStart").asText());
			boolean overlap = false;
			for (Date otherEnd : becmgEndTimes) {
				if (becmgStart.before(otherEnd)) {
					overlap = true;
				}
			}

			if (changeEndNode != null && !changeEndNode.isNull() && !changeEndNode.isMissingNode()) {
				becmgEndTimes.add(formatter.parse(changeEndNode.asText()));
			}
			changegroup.put("changegroupBecomingOverlaps", overlap);
		}
	}

	private static void augmentChangegroupsIncreasingInTime(JsonNode input) throws ParseException {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		Date prevChangeStart;
		Date tafStartTime;
		try {
			prevChangeStart = formatter.parse(input.findValue("validityStart").asText());
			tafStartTime = (Date) prevChangeStart.clone();
		} catch (ParseException e) {
			return;
		}
		JsonNode changeGroups = input.get("changegroups");
		if (changeGroups == null || changeGroups.isNull() || changeGroups.isMissingNode())
			return;
		for (Iterator<JsonNode> change = changeGroups.elements(); change.hasNext();) {
			JsonNode nextNode = change.next(); 
			if (nextNode == null || nextNode == NullNode.getInstance()) continue;
			ObjectNode changegroup = (ObjectNode) nextNode;
			JsonNode changeStartNode = changegroup.findValue("changeStart");

			if (changeStartNode == null)
				continue;
			String changeStart = changeStartNode.asText();
			JsonNode changeTypeNode = changegroup.findValue("changeType");
			if (changeTypeNode == null)
				continue;
			String changeType = changeTypeNode.asText();
			try {
				Date parsedDate = formatter.parse(changeStart);
				boolean comesAfter = parsedDate.compareTo(prevChangeStart) >= 0
						|| (parsedDate.equals(prevChangeStart) && changeType.startsWith("PROB"))
						|| (parsedDate.equals(prevChangeStart) && changeType.startsWith("BECMG")
								&& parsedDate.equals(tafStartTime))
						|| (parsedDate.equals(prevChangeStart) && changeType.startsWith("TEMPO")
								&& parsedDate.equals(tafStartTime));
				if ("FM".equals(changeType) && parsedDate.compareTo(prevChangeStart) <= 0) {
					comesAfter = false;
				}
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
		// This is necessary because otherwise the schema is invalid and thus always
		// needs to happen.
		// The messages map is a mapping from a pointer in the JSONSchema to another map
		// This is a map from keyword to human-readable message. So the full structure
		// is something like
		// /definitions/vertical_visibilitiy --> minimum -> "Vertical visibility must be
		// greater than 0 meters"
		// |-> maximum -> "Vertical visibility must be less than 1000 meters"
		// |-> multipleOf -> "Vertical visibility must a multiple of 30 meters"
		Map<String, Map<String, String>> messagesMap = extractMessagesAndCleanseSchema(discoverSchemata(this.tafSchemaStore.getDirectory()));
		// Construct the final schema based on the filtered schema
		// Set the namespace to the tafstore location such that external schemas can be resolved
		URITranslatorConfiguration uribuilder = URITranslatorConfiguration.newBuilder()
				.setNamespace("file:"+this.tafSchemaStore.getDirectory()+"/").freeze();
		LoadingConfiguration config = LoadingConfiguration
				.newBuilder()
				.setURITranslatorConfiguration(uribuilder)
				.freeze();		
		final JsonSchemaFactory factory = JsonSchemaFactory
				.newBuilder()
				.setReportProvider(new ListReportProvider(LogLevel.ERROR, LogLevel.FATAL))
				.setLoadingConfiguration(config)
				.freeze();


		final JsonSchema schema = factory.getJsonSchema(schemaNode);
		// Try and validate the TAF

		ProcessingReport validationReport = schema.validate(jsonNode);
		return new DualReturn(validationReport, messagesMap);
	}

	private static void removeLastEmptyChangegroup(JsonNode jsonNode) {
		if (jsonNode == null)
			return;
		JsonNode changegroupsNode = jsonNode.at("/changegroups");
		if (changegroupsNode == null || changegroupsNode.isMissingNode() || changegroupsNode.isNull())
			return;
		ArrayNode changegroups = (ArrayNode) changegroupsNode;
		// If there are no changegroups we are done
		if (changegroups == null || changegroups.size() <= 1)
			return;

		/* Remove all empty changegroups */
		for (int i = changegroups.size() - 1; i >= 0; i--) {
			JsonNode elem = changegroups.get(i);
			if (elem == null || elem.isMissingNode() || elem.isNull() || elem == NullNode.getInstance() || elem.size() == 0) {
				changegroups.remove(i);
			}
		}
		if (changegroups.size() <= 1)
			return;
		JsonNode lastChangegroup = changegroups.get(changegroups.size() - 1);

		// If the last changegroup is null or {} we can throw it away
		if (lastChangegroup == null || lastChangegroup.size() == 0) {
			changegroups.remove(changegroups.size() - 1);
			return;
		}
		;
		ObjectNode lastForecast = (ObjectNode) lastChangegroup.get("forecast");

		// If the forecast in the last changegroup is null or {} we can throw it away
		if (lastForecast == null || lastForecast.size() == 0) {
			changegroups.remove(changegroups.size() - 1);
			return;
		}
		;

		// If it is a well-formed changegroup but has no content, we can throw it away
		if ((!lastChangegroup.has("changeType") || lastChangegroup.get("changeType").asText().equals(""))
				&& !lastChangegroup.has("changeStart") && !lastChangegroup.has("changeEnd")
				&& lastForecast.get("wind").size() == 0 && lastForecast.get("visibility").size() == 0
				&& lastForecast.get("weather").asText().equals("NSW")
				&& lastForecast.get("clouds").asText().equals("NSC")) {
			changegroups.remove(changegroups.size() - 1);
		}
	}

	public TafValidationResult validate(String tafStr)
			throws ProcessingException, JSONException, IOException, ParseException {
		//		Debug.println("Validate\n" + tafStr);
		//		System.out.println(discoverSchemata(this.tafSchemaStore.getDirectory()));

		String schemaFile = tafSchemaStore.getLatestTafSchema();
		JsonNode jsonNode = ValidationUtils.getJsonNode(tafStr);
		removeLastEmptyChangegroup(jsonNode);
		DualReturn ret = performValidation(schemaFile, jsonNode);
		ProcessingReport validationReport = ret.getReport();
		Map<String, Map<String, String>> messagesMap = ret.getMessages();

		// If the validation is not successful we try to find all relevant errors
		// They are relevant if the error path in the schema exist in the
		// possibleMessages set
		if (validationReport == null) {
			ObjectMapper om = new ObjectMapper();
			return new TafValidationResult(false,
					(ObjectNode) om.readTree("{\"message\": \"Validation report was null\"}"), validationReport);
		}

		//		Debug.println(messagesMap.toString());

		Map<String, Set<String>> errorMessages = convertReportInHumanReadableErrors(validationReport, messagesMap);
		JsonNode errorJson = new ObjectMapper().readTree("{}");

		if (!validationReport.isSuccess()) {
			//			Debug.println("Validation report failed: " + validationReport.toString());	

			//			validationReport.forEach(report -> {
			//				
			//				try {
			//					Debug.println((new JSONObject(
			//							report.asJson().toString()
			//							)).toString(4));
			//				} catch (JSONException e) {
			//					// TODO Auto-generated catch block
			//					e.printStackTrace();
			//				}
			//				
			//			});

			String errorsAsJson = new ObjectMapper().writeValueAsString(errorMessages);



			//			Debug.println((new JSONObject(new ObjectMapper().writeValueAsString(messagesMap).toString())).toString(4));
			// Try to find all possible errors and map them to the human-readable variants
			// using the messages map
			((ObjectNode) errorJson).setAll((ObjectNode) (ValidationUtils.getJsonNode(errorsAsJson)));
			//			Debug.println((new JSONObject(errorJson.toString())).toString(4));
		}
		// Enrich the JSON with custom data validation, this is validated using a second
		// schema
		enrich(jsonNode);
		String enrichedSchemaFile = tafSchemaStore.getLatestEnrichedTafSchema();
		ret = performValidation(enrichedSchemaFile, jsonNode);
		ProcessingReport enrichedValidationReport = ret.getReport();
		Map<String, Map<String, String>> enrichedMessagesMap = ret.getMessages();
		if (enrichedValidationReport == null) {
			ObjectMapper om = new ObjectMapper();
			return new TafValidationResult(false,
					(ObjectNode) om.readTree("{\"message\": \"Validation report was null\"}"), validationReport,
					enrichedValidationReport);
		}
		//		Debug.println("Second: " + enrichedValidationReport.toString());

		if (!enrichedValidationReport.isSuccess()) {
			// Try to find all possible errors and map them to the human-readable variants
			// using the messages map
			// Append them to any previous errors, if any
			Map<String, Set<String>> enrichedErrorMessages = convertReportInHumanReadableErrors(
					enrichedValidationReport, enrichedMessagesMap);
			String errorsAsJson = new ObjectMapper().writeValueAsString(enrichedErrorMessages);
			((ObjectNode) errorJson).setAll((ObjectNode) ValidationUtils.getJsonNode(errorsAsJson));
		}

		// If everything is okay, return true as succeeded with null as errors
		if (enrichedValidationReport.isSuccess() && validationReport.isSuccess()) {
			return new TafValidationResult(true);
		}
		return new TafValidationResult(false, (ObjectNode) errorJson, validationReport, enrichedValidationReport);
	}
}
