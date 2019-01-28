package nl.knmi.geoweb.backend.product.airmet.converter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.w3c.dom.Document;

import fi.fmi.avi.converter.AviMessageConverter;
import fi.fmi.avi.converter.AviMessageSpecificConverter;
import fi.fmi.avi.converter.ConversionHints;
import fi.fmi.avi.converter.ConversionIssue;
import fi.fmi.avi.converter.ConversionResult;
import fi.fmi.avi.converter.iwxxm.conf.IWXXMConverter;
import fi.fmi.avi.model.sigmet.AIRMET;
import nl.knmi.geoweb.backend.product.ProductConverter;
import nl.knmi.geoweb.backend.product.airmet.Airmet;
import nl.knmi.geoweb.iwxxm_2_1.converter.conf.GeoWebConverterConfig;

//import fi.fmi.avi.converter.iwxxm.conf.IWXXMConverter;

@Configuration
//@Import({ IWXXMConverter.class, nl.knmi.geoweb.iwxxm_2_1.converter.GeoWebAirMETConverter.class})
@Import({ IWXXMConverter.class})
public class AirmetConverter implements ProductConverter<Airmet>{
/*
	@Autowired
	private AviMessageSpecificConverter<AIRMET, String> airmetIWXXMStringSerializer;
	
	@Autowired
	private AviMessageSpecificConverter<AIRMET, Document> airmetIWXXMDOMSerializer;
	
	@Autowired
	private AviMessageSpecificConverter<Airmet,AIRMET> geoWebAirmetImporter;
*/

	@Bean
	public AviMessageConverter aviMessageConverter() {
		AviMessageConverter p = new AviMessageConverter();
/*
		p.setMessageSpecificConverter(GeoWebConverterConfig.GEOWEBAIRMET_TO_AIRMET_POJO, geoWebAirmetImporter);
		p.setMessageSpecificConverter(IWXXMConverter.AIRMET_POJO_TO_IWXXM21_DOM, airmetIWXXMDOMSerializer);
		p.setMessageSpecificConverter(IWXXMConverter.AIRMET_POJO_TO_IWXXM21_STRING, airmetIWXXMStringSerializer);
*/
		return p;
	}

	public String ToIWXXM_2_1(Airmet geoWebAirmet) {
        return "AIRMET in IWXXM";
/*

		ConversionResult<AIRMET> result = geoWebAirmetImporter.convertMessage(geoWebAirmet, ConversionHints.AIRMET);
		if (ConversionResult.Status.SUCCESS == result.getStatus()) {
			System.err.println("SUCCESS");
			AIRMET pojo = result.getConvertedMessage().get();
			System.err.println("POJO:"+pojo);
			ConversionResult<String>iwxxmResult=airmetIWXXMStringSerializer.convertMessage(pojo, ConversionHints.AIRMET);
			if (ConversionResult.Status.SUCCESS == iwxxmResult.getStatus()) {
				return iwxxmResult.getConvertedMessage().get();
			} else {
				System.err.println("ERR: "+iwxxmResult.getStatus());
				for (ConversionIssue iss:iwxxmResult.getConversionIssues()) {
					System.err.println("iss: "+iss.getMessage());
				}
			}
		}else {
			System.err.println("Airmet2IWXXM failed");
			System.err.println("ERR: "+result.getStatus());
			for (ConversionIssue iss:result.getConversionIssues()) {
				System.err.println("iss: "+iss.getMessage());
			}
		}
		return "FAIL";
*/

	}
}
