package nl.knmi.geoweb.backend.product.airmet;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import nl.knmi.adaguc.tools.Debug;

@Configuration
@ComponentScan({"nl.knmi.geoweb.backend.product.airmet"})
public class AirmetStoreTestConfig {
	public static final String DATEFORMAT_ISO8601 = "yyyy-MM-dd'T'HH:mm:ss'Z'";
	@Bean("airmetObjectMapper")
	public static ObjectMapper getAirmetObjectMapperBean() {
		Debug.println("Init AirmetObjectMapperBean (AirmetStoreTest)");
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
	

}
