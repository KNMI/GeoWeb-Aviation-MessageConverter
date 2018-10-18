package nl.knmi.geoweb.backend.product.taf;


import java.text.SimpleDateFormat;
import java.util.TimeZone;

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import nl.knmi.geoweb.backend.aviation.AirportStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.annotation.JsonInclude;

import nl.knmi.adaguc.tools.Debug;

@Configuration
@ComponentScan({"nl.knmi.geoweb.backend.product.taf"})
public class TafTestConfig {
    public static final String DATEFORMAT_ISO8601 = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    public static AirportStore as=null;

    @Bean("airportstore")
    public static AirportStore getAirportStore() {
        if (as==null) {
            as=new AirportStore("/tmp/test");
        }
        return as;
    }

    @Bean("tafObjectMapper")
    public static ObjectMapper getTafObjectMapperBean() {
        Debug.println("Init TafObjectMapperBean (TafTest)");
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.registerModule(new Jdk8Module());
        om.setTimeZone(TimeZone.getTimeZone("UTC"));
        om.setDateFormat(new SimpleDateFormat(DATEFORMAT_ISO8601));
        om.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        om.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        return om;
    }
}
