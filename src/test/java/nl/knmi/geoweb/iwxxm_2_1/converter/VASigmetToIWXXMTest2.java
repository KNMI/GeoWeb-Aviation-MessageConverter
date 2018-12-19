package nl.knmi.geoweb.iwxxm_2_1.converter;

import java.io.IOException;

import org.geojson.GeoJsonObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.knmi.adaguc.tools.Debug;
import nl.knmi.adaguc.tools.Tools;
import nl.knmi.geoweb.backend.aviation.FIRStore;
import nl.knmi.geoweb.backend.product.sigmet.Sigmet;
import nl.knmi.geoweb.backend.product.sigmet.converter.SigmetConverter;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = {SigmetToIWXXMTestConfig.class})
public class VASigmetToIWXXMTest2 {
	@Autowired
	@Qualifier("sigmetObjectMapper")
	private ObjectMapper sigmetObjectMapper;
	
	@Autowired
	private SigmetConverter sigmetConverter;
	
	public final String sigmetStoreLocation = "/tmp/junit/geowebbackendstore/";
	
	static String[] testSigmets= new String[] {  getStringFromFile(
			"nl/knmi/geoweb/iwxxm_2_1/converter/SIGMET_EHDB_2018-11-29T1230_20181129111546.json") };

	public static String getStringFromFile(String fn) {
        	return Tools.readResource(fn);
    }

	public void setGeoFromString2(Sigmet sm, String json) {
		Debug.println("setGeoFromString2 "+json);
		GeoJsonObject geo;	
		try {
			geo = sigmetObjectMapper.readValue(json, GeoJsonObject.class);
			sm.setGeojson(geo);
			Debug.println("setGeoFromString ["+json+"] set");
			return;
		} catch (JsonParseException e) {
		} catch (JsonMappingException e) {
		} catch (IOException e) {
		}
		Debug.errprintln("setGeoFromString on ["+json+"] failed");
		sm.setGeojson(null);
	}
	
	@Autowired
	FIRStore firStore;
	
	public void TestConversion(String s) {
		Sigmet sm = null;
		try {
			sm=sigmetObjectMapper.readValue(s, Sigmet.class);
		} catch (Exception e) {
			e.printStackTrace(System.err);
		}

		String res=sigmetConverter.ToIWXXM_2_1(sm);
		System.err.println(res);
		System.err.println("TAC: "+sm.toTAC(firStore.lookup(sm.getFirname(), false)));
	}
	
	@Test
	public void TestConversions(){
		for (String sm: testSigmets) {
			Debug.println("Testing sigmet: "+sm);
			TestConversion(sm);
			Debug.println("\n");
		}
	}

}
