package nl.knmi.geoweb.backend.product.taf;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import fi.fmi.avi.converter.ConversionHints;
import lombok.Getter;
import lombok.Setter;
import nl.knmi.adaguc.tools.Debug;
import nl.knmi.adaguc.tools.Tools;
import nl.knmi.geoweb.backend.product.IExportable;
import nl.knmi.geoweb.backend.product.taf.converter.TafConverter;
import nl.knmi.geoweb.backend.product.taf.serializers.CloudsSerializer;
import nl.knmi.geoweb.backend.product.taf.serializers.WeathersSerializer;
import nl.knmi.geoweb.iwxxm_2_1.converter.GeoWebConverter;
import nl.knmi.geoweb.iwxxm_2_1.converter.GeoWebTAFConverter;

@Getter
@Setter
public class Taf implements IExportable {
	public static final String DATEFORMAT_ISO8601 = "yyyy-MM-dd'T'HH:mm:ss'Z'";

	public enum TAFReportType {
		retarded, normal, amendment, canceled, correction, missing;
	}

	public enum TAFReportPublishedConcept {
		concept, published, inactive
	}

	@Getter
	@Setter
	public static class Metadata {
		private String previousUuid = null;
		private String uuid = null;
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		OffsetDateTime issueTime;
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		OffsetDateTime validityStart;
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		OffsetDateTime validityEnd;
		String location;
		TAFReportPublishedConcept status;
		TAFReportType type;
	};

	public Metadata metadata;

	@Setter
	@Getter
	public static class Forecast {
		@Getter
		@Setter
		public static class TAFCloudType {
			@JsonInclude(JsonInclude.Include.NON_NULL)
			Boolean isNSC = null;
			String amount;
			String mod;
			Integer height;

			public TAFCloudType() {
				this.isNSC = null;
			}

			public TAFCloudType(String cld) {
				if ("NSC".equalsIgnoreCase(cld)) {
					isNSC = true;
				}
			}

			public String toTAC() {
				StringBuilder sb = new StringBuilder();
				if (isNSC != null && isNSC) {
					sb.append("NSC");
				} else {
					sb.append(amount.toString());
					sb.append(String.format("%03d", height));
					if (mod != null) {
						sb.append(mod);
					}
				}
				return sb.toString();
			}
		}

		// @JsonInclude(JsonInclude.Include.NON_NULL)
		Integer vertical_visibility;

		@JsonSerialize(using = CloudsSerializer.class)
		@JsonInclude(JsonInclude.Include.NON_EMPTY)
		List<TAFCloudType> clouds;

		@Getter
		@Setter
		public static class TAFWeather {
			@JsonInclude(JsonInclude.Include.NON_NULL)
			Boolean isNSW = null;
			String qualifier;
			String descriptor;
			List<String> phenomena;

			TAFWeather(String ww) {
				if ("NSW".equalsIgnoreCase(ww)) {
					isNSW = true;
				}
			}

			public TAFWeather() {
				isNSW = null;
			}

			public String toString() {
				StringBuilder sb = new StringBuilder();
				if (this.isNSW != null && this.isNSW == true) {
					return "NSW";
				}
				if (this.qualifier != null) {
					sb.append(TAFtoTACMaps.getQualifier(this.qualifier));
				}
				if (this.descriptor != null) {
					sb.append(TAFtoTACMaps.getDescriptor(this.descriptor));
				}
				if (this.phenomena != null && !this.phenomena.isEmpty()) {
					for (String phenomenon : this.phenomena) {
						sb.append(TAFtoTACMaps.getPhenomena(phenomenon));
					}
				}
				return sb.toString();
			}
		}

		@JsonInclude(JsonInclude.Include.NON_EMPTY)
		@JsonSerialize(using = WeathersSerializer.class)
		List<TAFWeather> weather;

		@Setter
		@Getter
		public static class TAFVisibility {
			Integer value;
			String unit;

			public String toTAC() {
				if (unit == null || unit.equalsIgnoreCase("M")) {
					return String.format(" %04d", value);
				}
				if (unit.equals("KM")) {
					return String.format(" %02d", value) + "KM";
				}
				throw new IllegalArgumentException("Unknown unit found for visibility");
			}
		}

		TAFVisibility visibility;

		@Getter
		@Setter
		public static class TAFWind {
			Object direction;
			Integer speed;
			Integer gusts;
			String unit;

			public String toTAC() {
				StringBuilder sb = new StringBuilder();
				if (direction.toString().equals("VRB")) {
					sb.append("VRB");
				} else {
					sb.append(String.format("%03d", Integer.parseInt(direction.toString())));
				}
				sb.append(String.format("%02d", speed));
				if (gusts != null) {
					sb.append(String.format("G%02d", gusts));
				}
				sb.append(unit.toString());
				return sb.toString();
			}
		}

		TAFWind wind;

		@Getter
		@Setter
		public class TAFTemperature {
			Float maximum;
			@JsonFormat(shape = JsonFormat.Shape.STRING)
			@JsonInclude(JsonInclude.Include.NON_NULL)
			OffsetDateTime maxTime;
			Float minimum;
			@JsonFormat(shape = JsonFormat.Shape.STRING)
			@JsonInclude(JsonInclude.Include.NON_NULL)
			OffsetDateTime minTime;
		}

		TAFTemperature temperature;

		Boolean CaVOK;

		/**
		 * Converts Forecast to TAC
		 * 
		 * @return String with TAC representation of Forecast
		 */
		public String toTAC() {
			StringBuilder sb = new StringBuilder();
			if (getWind() != null) {
				sb.append(getWind().toTAC());
			}
			if (CaVOK != null && CaVOK == true) {
				sb.append(" CAVOK");
			} else {
				if (visibility != null && visibility.value != null) {
					sb.append(visibility.toTAC());
				}
				if (getWeather() != null) {
					for (TAFWeather w : getWeather()) {
						sb.append(" " + w);
					}
				}

				if (getClouds() != null) {
					for (TAFCloudType tp : getClouds()) {
						sb.append(" ");
						sb.append(tp.toTAC());
					}
				}
			}
			return sb.toString();
		}
	}

	Forecast forecast;

	@Getter
	@Setter
	public static class ChangeForecast {
		String changeType;
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		OffsetDateTime changeStart;
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		OffsetDateTime changeEnd;
		Forecast forecast;

		public String toTAC() {
			StringBuilder sb = new StringBuilder();
			sb.append(changeType.toString());
			sb.append(" " + TAFtoTACMaps.toDDHH(changeStart));
			sb.append("/" + TAFtoTACMaps.toDDHH(changeEnd));
			sb.append(" " + forecast.toTAC());
			return sb.toString();
		}
	}

	List<ChangeForecast> changegroups;

	public String toJSON() throws JsonProcessingException {
		ObjectMapper om = getTafObjectMapperBean();
		return om.writerWithDefaultPrettyPrinter().writeValueAsString(this);
	}

	public static Taf fromJSONString(String tafJson) throws JsonParseException, JsonMappingException, IOException {
		ObjectMapper om = getTafObjectMapperBean();
		Taf taf = om.readValue(tafJson, Taf.class);
		return taf;
	}

	public static Taf fromFile(File f) throws JsonParseException, JsonMappingException, IOException {
		return fromJSONString(Tools.readFile(f.getAbsolutePath()));
	}

	public String toTAC() {
		System.out.println(this.metadata.type);
		StringBuilder sb = new StringBuilder();
		sb.append("TAF ");
		switch (this.metadata.type) {
		case amendment:
			sb.append("AMD ");
			break;
		case correction:
			sb.append("COR ");
			break;
		case retarded:
			sb.append("RTD ");
			break;
		default:
			// Append nothing here
			break;
		}

		sb.append(this.metadata.location);
		sb.append(" " + TAFtoTACMaps.toDDHHMM(this.metadata.issueTime));
		switch (this.metadata.type) {
		case missing:
			// If missing, we're done here
			sb.append(" NIL");
			return sb.toString();
		default:
			// do nothing
			break;
		}
		sb.append(" " + TAFtoTACMaps.toDDHH(this.metadata.validityStart) + "/"
				+ TAFtoTACMaps.toDDHH(this.metadata.validityEnd));
		switch (this.metadata.type) {
		case canceled:
			// In case of a cancel there are no change groups so we're done here
			sb.append(" CNL");
			return sb.toString();
		default:
			// do nothing
			break;
		}
		// Add the rest of the TAC
		sb.append(" " + this.forecast.toTAC());
		if (this.changegroups != null) {
			for (ChangeForecast ch : this.changegroups) {
				sb.append("\n" + ch.toTAC());
			}
		}

		return sb.toString();
	}
	
	// Same as TAC, but maximum line with 69 chars where words (e.g. "BKN040") are not splitted
	// Also has a header and footer to the message
	private String getPublishableTAC() {
		String line = "";
		String publishTAC = "";
		System.out.println("Publish: " + this.toTAC());
		String[] TACwords = this.toTAC().split("\\s+");
		for(int i = 0; i < TACwords.length; ++i) {
			if (line.length() + TACwords[i].length() + 1 <= 69) {
				if (line.length() > 0) line += " ";
				line += TACwords[i];
			} else {
				publishTAC += line + '\n';
				line = "";
			}
		}
		publishTAC += line;
		OffsetDateTime minusOne = this.metadata.validityStart.minusHours(1);
		String time = TAFtoTACMaps.toDDHH(minusOne) + "00";
		String paddedDayOfMonth = String.format("%02d", minusOne.getDayOfMonth());
		String status = "";
		switch (this.metadata.type) {
		case amendment:
			status = " AMD";
			break;
		case correction:
			status = " COR";
			break;
		case retarded:
			status = " RTD";
			break;
		case missing:
			status = " NIL";
			break;
		case canceled:
			status = " CNL";
			break;
		default:
			// Append nothing here
			break;
			
		}

		
		String header = "FTNL99 " + this.metadata.location + " " + paddedDayOfMonth + time + status +'\n';
		String footer = "=";
		return header + publishTAC + footer;
	}

	// TODO use BEAN in proper way (Ask WvM)
	@Bean(name = "objectMapper")
	public static ObjectMapper getTafObjectMapperBean() {
		ObjectMapper om = new ObjectMapper();
		om.registerModule(new JavaTimeModule());
		om.setTimeZone(TimeZone.getTimeZone("UTC"));
		om.setDateFormat(new SimpleDateFormat(DATEFORMAT_ISO8601));
		om.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		om.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
		return om;

	}

	@Bean(name = "objectMapper")
	public static ObjectMapper getObjectMapperBean() {
		ObjectMapper om = new ObjectMapper();
		om.registerModule(new JavaTimeModule());
		om.setTimeZone(TimeZone.getTimeZone("UTC"));
		om.setDateFormat(new SimpleDateFormat(DATEFORMAT_ISO8601));
		om.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return om;

	}

	@Override
	public void export(File path) {
		try {
			String time = this.metadata.validityStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm"));

			String name = "TAF_" + this.metadata.getLocation() + "_" + time;
			Tools.writeFile(path.getPath() + "/" + name + "_" + this.metadata.uuid + ".tac", this.getPublishableTAC());
			Tools.writeFile(path.getPath() + "/" + name + "_" + this.metadata.uuid + ".json", this.toJSON());
			// TODO: Tools.writeFile(path.getPath() + this.metadata.uuid + ".xml", this.toIWXXM());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
