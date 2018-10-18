package nl.knmi.geoweb.backend.product.taf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fi.fmi.avi.converter.ConversionHints;
import fi.fmi.avi.converter.ConversionResult;
import fi.fmi.avi.model.*;
import fi.fmi.avi.model.immutable.AerodromeImpl;
import fi.fmi.avi.model.taf.TAF;
import fi.fmi.avi.model.taf.TAFBaseForecast;
import fi.fmi.avi.model.taf.immutable.TAFImpl;
import nl.knmi.geoweb.backend.aviation.AirportStore;
import nl.knmi.geoweb.backend.product.taf.converter.TafConverter;
import nl.knmi.geoweb.iwxxm_2_1.converter.GeoWebTafInConverter;
import nl.knmi.geoweb.iwxxm_2_1.converter.GeoWebTafToIWXXMTestConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = {TafTestConfig.class})
public class TAFToGeoWebTaf {
    @Autowired
    @Qualifier("tafObjectMapper")
    private ObjectMapper tafObjectMapper;

    @Autowired
    private AirportStore airportStore;

	@Autowired
	private GeoWebTafInConverter geowebTafInConverter;

    @Test
    public void testTaftoGeoWebTaf() {
        TAF taf=null;
        try {
            taf = readFromJSON("TAFToGeoWebTaf1.json");
            System.err.println("TAF:"+taf);
        } catch (IOException e) {
            //TODO
            System.err.println("OhOh "+e);
        }
        TAF completedTaf= TAFImpl.Builder.from(taf).withAllTimesComplete(ZonedDateTime.now()).build();
        System.err.println("completedTAF:"+completedTaf);
        ConversionHints hints=new ConversionHints();
        ConversionResult<Taf> result=geowebTafInConverter.convertMessage(completedTaf, hints);
        System.err.println("Conversion status: "+ result.getStatus());
        System.err.println(result.getConvertedMessage().get().toTAC());


    }

    protected TAF readFromJSON(String fileName) throws IOException {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new Jdk8Module());
        om.registerModule(new JavaTimeModule());
        InputStream is = TafTestConfig.class.getResourceAsStream(fileName);
        if (is != null) {
            return om.readValue(is, TAFImpl.class);
        } else {
            throw new FileNotFoundException("Resource '" + fileName + "' could not be loaded");
        }
    }
}
