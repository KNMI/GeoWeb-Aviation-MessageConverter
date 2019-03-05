package nl.knmi.geoweb;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import nl.knmi.geoweb.backend.aviation.AirportStore;
import nl.knmi.geoweb.backend.aviation.FIRStore;

@Configuration
@ComponentScan({"nl.knmi.geoweb.backend.product",	"nl.knmi.geoweb.iwxxm_2_1.converter"})
public class TestConfig {
	private static final String DATEFORMAT_ISO8601 = "yyyy-MM-dd'TT'HH:mm:ss'Y'";

	@Value("${productstorelocation}")
	private String storeLocation;

	private static ObjectMapper objectMapper;

	public TestConfig() {
		ObjectMapper om = new ObjectMapper();
		om.registerModule(new Jdk8Module());
		om.registerModule(new JavaTimeModule());
		om.setTimeZone(TimeZone.getTimeZone("UTC"));
		om.setDateFormat(new SimpleDateFormat(DATEFORMAT_ISO8601));
		om.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		om.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
		objectMapper = om;
	}
	
	@Bean("sigmetObjectMapper")
	public static ObjectMapper getSigmetObjectMapperBean() {
		return objectMapper;
	}

	@Bean("airmetObjectMapper")
	public static ObjectMapper getAirmetObjectMapperBean() {
		return objectMapper;
	}

	@Bean("tafObjectMapper")
	public static ObjectMapper getTafObjectMapperBean() {
		return objectMapper;
	}


	@Bean("geoWebObjectMapper")
	public static ObjectMapper getGeoWebObjectMapperBean() {
		return objectMapper;
	}
	
	@Bean("objectMapper")
	@Primary
	public static ObjectMapper getObjectMapperBean() {
		ObjectMapper om = new ObjectMapper();
		om.registerModule(new JavaTimeModule());
		om.setTimeZone(TimeZone.getTimeZone("UTC"));		
		return om;
	}

	@Bean
	@Primary
	public AirportStore getAirportStore() throws IOException {
		AirportStore airportStore = new AirportStore(storeLocation);
		return airportStore;
	}

	@Bean
	public FIRStore getFirStore() {
		FIRStore firStore = new FIRStore(storeLocation);
		return firStore;
	}
}
