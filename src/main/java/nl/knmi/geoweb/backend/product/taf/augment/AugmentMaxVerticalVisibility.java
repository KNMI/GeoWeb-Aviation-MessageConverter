package nl.knmi.geoweb.backend.product.taf.augment;

import java.util.Iterator;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.knmi.geoweb.backend.product.taf.TafValidator;

public class AugmentMaxVerticalVisibility {


	public static void augment(JsonNode input) {
		if (!TafValidator.checkIfNodeHasValue(input.get("forecast")) || 
				!TafValidator.checkIfNodeHasValue(input.get("forecast").get("vertical_visibility")))return;
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

}
