package nl.knmi.geoweb.backend.product.taf.converter;

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
import fi.fmi.avi.model.Aerodrome;
import fi.fmi.avi.model.GeoPosition;
import fi.fmi.avi.model.taf.TAF;
import nl.knmi.adaguc.tools.Debug;
import nl.knmi.geoweb.backend.aviation.AirportInfo;
import nl.knmi.geoweb.backend.aviation.AirportStore;
import nl.knmi.geoweb.backend.product.ProductConverter;
import nl.knmi.geoweb.backend.product.taf.Taf;
import nl.knmi.geoweb.iwxxm_2_1.converter.conf.GeoWebConverterConfig;

@Configuration
@Import({fi.fmi.avi.converter.iwxxm.conf.IWXXMConverter.class, nl.knmi.geoweb.iwxxm_2_1.converter.GeoWebTAFConverter.class})
public class TafConverter implements ProductConverter<Taf>{
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

	@Autowired
	AirportStore airportStore;

	public String ToIWXXM_2_1(Taf geoWebTaf) {

		ConversionResult<TAF> result = geoWebTafImporter.convertMessage(geoWebTaf,ConversionHints.TAF);
		if (ConversionResult.Status.SUCCESS == result.getStatus()) {
			TAF pojo = result.getConvertedMessage();
			String airportName=	geoWebTaf.getMetadata().getLocation();
			AirportInfo airportInfo=airportStore.lookup(airportName);
			if (airportInfo!=null) {
				Aerodrome ad=pojo.getAerodrome();
				GeoPosition refPoint =new GeoPosition(airportInfo.getGeoLocation().getEPSG(),
						airportInfo.getGeoLocation().getLat(), airportInfo.getGeoLocation().getLon());
				refPoint.setElevationValue(airportInfo.getFieldElevation());
		        refPoint.setElevationUom("m");
				ad.setReferencePoint(refPoint);
				
				ad.setFieldElevation(airportInfo.getFieldElevation());
				ad.setLocationIndicatorICAO(airportInfo.getICAOName());
				ad.setName(airportInfo.getName());
				ad.setDesignator(airportName);
				
				pojo.amendAerodromeInfo(ad);
			}
			else {
				System.err.println("airportinfo for "+airportName+" not found");
			}
			ConversionResult<String>iwxxmResult=tafIWXXMStringSerializer.convertMessage(pojo, ConversionHints.TAF);
			if (ConversionResult.Status.SUCCESS == iwxxmResult.getStatus()) {
				return iwxxmResult.getConvertedMessage();
			} else {
				System.err.println("ERR: "+iwxxmResult.getStatus());
				for (ConversionIssue iss:iwxxmResult.getConversionIssues()) {
					System.err.println("iss: "+iss.getMessage());
				}
			}
		}else {
			System.err.println("Taf2IWXXM failed");
			System.err.println("ERR: "+result.getStatus());
			for (ConversionIssue iss:result.getConversionIssues()) {
				System.err.println("iss: "+iss.getMessage());
			}
		}
		return "FAIL";
	}
}
