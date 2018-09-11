package nl.knmi.geoweb.backend.product.taf.augment;

import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.knmi.geoweb.backend.product.taf.TafValidator;

public class AugmentWindEnoughChange {
	
	public static void augment (JsonNode input) {
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
		boolean wasGusty = false;

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

				long directionDifference = Math.min(TafValidator.subtract(changeWindDirection, forecastWindDirection, 360),
						TafValidator.subtract(forecastWindDirection, changeWindDirection, 360));
				wind.put("directionDiff", directionDifference);
				wind.put("speedDiff", speedDifference);
				
				/* From groups are treated as a new TAF */
				if (!changeGroupChangeAsText.equals("FM")) {
					/* Wind speed difference should be more than 5 knots or 2 meters per second.*/	
					int limitSpeedDifference = unit.equals("KT") ? 5 : 2;
					/* If previous was gusty and new one is not gusty, all is allowed */
					if (!(wasGusty && !becomesGusty)) { 
						wind.put("windEnoughDifference",
								directionDifference >= 30 || speedDifference >= limitSpeedDifference || becomesGusty);
					}
				}
				
				/* Copy previous wind if change type is not PROB* or TEMPO */
				if (!changeGroupChangeAsText.startsWith("PROB") && !changeGroupChangeAsText.equalsIgnoreCase("TEMPO")) {
					forecastWindDirection = changeWindDirection;
					forecastWindSpeed = changeWindSpeed;
					wasGusty = becomesGusty;
				}
			}
		}
	}
}
