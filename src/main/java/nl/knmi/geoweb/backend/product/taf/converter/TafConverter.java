package nl.knmi.geoweb.backend.product.taf.converter;

import java.io.File;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
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
import fi.fmi.avi.model.Aerodrome;
import fi.fmi.avi.model.GeoPosition;
import fi.fmi.avi.model.taf.TAF;
import nl.knmi.geoweb.backend.product.taf.Taf;
import nl.knmi.geoweb.iwxxm_2_1.converter.conf.GeoWebConverterConfig;

@Configuration
@Import({fi.fmi.avi.converter.iwxxm.conf.IWXXMConverter.class, nl.knmi.geoweb.iwxxm_2_1.converter.GeoWebTAFConverter.class})
public class TafConverter {
	@Autowired
	private AviMessageSpecificConverter<TAF, Document> tafIWXXMDOMSerializer;

	@Autowired
	private AviMessageSpecificConverter<TAF, String> tafIWXXMStringSerializer;

	@Autowired
	private AviMessageSpecificConverter<Taf,TAF> geoWebTafImporter;

	@Bean
	public AviMessageConverter aviMessageConverter() {
		AviMessageConverter p = new AviMessageConverter();
		p.setMessageSpecificConverter(IWXXMConverter.TAF_POJO_TO_IWXXM21_DOM, tafIWXXMDOMSerializer);
		p.setMessageSpecificConverter(IWXXMConverter.TAF_POJO_TO_IWXXM21_STRING, tafIWXXMStringSerializer);
		p.setMessageSpecificConverter(GeoWebConverterConfig.GEOWEBTAF_TO_TAF_POJO, geoWebTafImporter);
		return p;
	}

	public String ToIWXXM_2_1(Taf geoWebTaf) {

		ConversionResult<TAF> result = geoWebTafImporter.convertMessage(geoWebTaf,ConversionHints.TAF);
		if (ConversionResult.Status.SUCCESS == result.getStatus()) {
			TAF pojo = result.getConvertedMessage();
			//			pojo.amendTimeReferences(ZonedDateTime.of(2017, 9, 4, 6, 0, 0, 0, ZoneId.of("Z")));
			System.out.println(pojo.getAerodrome().isResolved());
			Aerodrome ad=pojo.getAerodrome();
			ad.setReferencePoint(new GeoPosition("EPSG:4326", 52.0, 5.2));
			ad.setFieldElevation(-4.);
			ad.setLocationIndicatorICAO("EHAM");
			ad.setName("AMSTERDAM");
			System.out.println(pojo.getAerodrome().isResolved());

			ConversionResult<String>iwxxmResult=tafIWXXMStringSerializer.convertMessage(pojo, ConversionHints.TAF);
			if (ConversionResult.Status.SUCCESS == iwxxmResult.getStatus()) {
				System.out.println("Result: "+iwxxmResult.getConvertedMessage());
				return iwxxmResult.getConvertedMessage();
			} else {
				System.out.println("ERR: "+iwxxmResult.getStatus());
				for (ConversionIssue iss:iwxxmResult.getConversionIssues()) {
					System.out.println("iss: "+iss.getMessage());
				}
			}
		}else {
			System.out.println("Taf2IWXXM failed");
			System.out.println("ERR: "+result.getStatus());
			for (ConversionIssue iss:result.getConversionIssues()) {
				System.out.println("iss: "+iss.getMessage());
			}
		}
		return "FAIL";
	}

}
