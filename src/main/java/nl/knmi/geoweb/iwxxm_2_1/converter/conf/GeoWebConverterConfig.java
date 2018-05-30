package nl.knmi.geoweb.iwxxm_2_1.converter.conf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import fi.fmi.avi.converter.AviMessageSpecificConverter;
import fi.fmi.avi.converter.ConversionSpecification;
import fi.fmi.avi.model.sigmet.SIGMET;
import fi.fmi.avi.model.taf.TAF;
import nl.knmi.geoweb.backend.product.sigmet.Sigmet;
import nl.knmi.geoweb.backend.product.taf.Taf;
import nl.knmi.geoweb.iwxxm_2_1.converter.GeoWebConverter;
import nl.knmi.geoweb.iwxxm_2_1.converter.GeoWebSIGMETConverter;
import nl.knmi.geoweb.iwxxm_2_1.converter.GeoWebSigmetConverter;
import nl.knmi.geoweb.iwxxm_2_1.converter.GeoWebTAFConverter;

@Configuration
public class GeoWebConverterConfig {

	   public static final ConversionSpecification<Taf, TAF> GEOWEBTAF_TO_TAF_POJO = new ConversionSpecification<>(Taf.class, TAF.class, "ICAO Annex 3 TAC",
	            null);
	   
	   public static final ConversionSpecification<Sigmet, SIGMET> GEOWEBSIGMET_TO_SIGMET_POJO = new ConversionSpecification<>(Sigmet.class, SIGMET.class, "ICAO Annex 3 SIGMET",
	            null);
	   
	   @Bean
	    AviMessageSpecificConverter<Taf, TAF> geowebTafConverter() {
	        GeoWebConverter<TAF> p = new GeoWebTAFConverter();
	        return p;
	    }
	   
	   @Bean
	    AviMessageSpecificConverter<Sigmet, SIGMET> geowebSIGMETConverter() {
	        GeoWebSigmetConverter<SIGMET> p = new GeoWebSIGMETConverter();
	        return p;
	    }
	   
}
