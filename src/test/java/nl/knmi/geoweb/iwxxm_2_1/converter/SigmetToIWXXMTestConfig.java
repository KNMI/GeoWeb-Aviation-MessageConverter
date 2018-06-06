package nl.knmi.geoweb.iwxxm_2_1.converter;

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
import nl.knmi.geoweb.backend.aviation.FIRStore;

@Configuration
@ComponentScan({"nl.knmi.geoweb.backend.product.sigmet"})
public class SigmetToIWXXMTestConfig {
	public static final String DATEFORMAT_ISO8601 = "yyyy-MM-dd'T'HH:mm:ss'Z'";
	@Bean("sigmetObjectMapper")
	public static ObjectMapper getSigmetObjectMapperBean() {
		Debug.println("Init SigmetObjectMapperBean");
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
	
	@Bean 
	public static FIRStore getFirStore() {
		System.err.println("Init firStore");
		FIRStore firStore=new FIRStore("/tmp/test");
		return firStore;
		
	}
}
