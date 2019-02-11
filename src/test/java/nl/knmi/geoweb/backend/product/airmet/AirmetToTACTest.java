package nl.knmi.geoweb.backend.product.airmet;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;

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
import nl.knmi.geoweb.backend.product.airmet.Airmet.Phenomenon;
import nl.knmi.geoweb.backend.product.sigmetairmet.SigmetAirmetChange;
import nl.knmi.geoweb.backend.product.sigmetairmet.SigmetAirmetLevel;
import nl.knmi.geoweb.backend.product.sigmetairmet.SigmetAirmetStatus;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = {AirmetStoreTestConfig.class})
public class AirmetToTACTest {
	@Autowired
	@Qualifier("airmetObjectMapper")
	private ObjectMapper airmetObjectMapper;
	
	public final String airmetStoreLocation = "/tmp/junit/geowebbackendstore/";
	
	static String testGeoJson="{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[4.44963571205923,52.75852934878266],[1.4462013467168233,52.00458561642831],[5.342222631879865,50.69927379063084],[7.754619712476178,50.59854892065259],[8.731640530117685,52.3196364467871],[8.695454573908739,53.50720041878871],[6.847813968390116,54.08633053026368],[3.086939481359807,53.90252679590722]]]},\"properties\":{}}]}";

	static String testGeoJson1="{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[0,52],[0,60],[10,60],[10,52],[0,52]]]}, \"properties\":{\"featureFunction\":\"start\", \"selectionType\":\"box\"} }]}";

	static String testGeoJson2="{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[0,52],[0,60],[5,60],[5,52],[0,52]]]}, \"properties\":{\"featureFunction\":\"start\", \"selectionType\":\"box\"} }]}";
	
	static String testGeoJson3="{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[0,52],[0,54],[10,54],[10,52],[0,52]]]}, \"properties\":{\"featureFunction\":\"start\", \"selectionType\":\"box\"} }]}";
	
	static String testGeoJson4="{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[0,52]}, \"properties\":{\"featureFunction\":\"start\", \"selectionType\":\"point\"} }]}";
	
	static String testGeoJson5="{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\", \"properties\":{\"featureFunction\":\"start\", \"selectionType\":\"fir\"} }]}";

	static String testAirmet="{\"geojson\":"
			+"{\"type\":\"FeatureCollection\",\"features\":"+"[{\"type\":\"Feature\",\"properties\":{\"prop0\":\"value0\",\"prop1\":{\"this\":\"that\"}},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[4.44963571205923,52.75852934878266],[1.4462013467168233,52.00458561642831],[5.342222631879865,50.69927379063084],[7.754619712476178,50.59854892065259],[8.731640530117685,52.3196364467871],[8.695454573908739,53.50720041878871],[6.847813968390116,54.08633053026368],[3.086939481359807,53.90252679590722]]]}}]},"
			+"\"phenomenon\":\"ISOL_TCU\","
			+"\"obs_or_forecast\":{\"obs\":true},"
			+"\"level\":{\"lev1\":{\"value\":100.0,\"unit\":\"FL\"}},"
			+"\"movement_type\":\"stationary\","
			+"\"change\":\"NC\","
			+"\"issuedate\":\"2017-03-24T15:56:16Z\","
			+"\"validdate\":\"2017-03-24T15:56:16Z\","
			+"\"firname\":\"AMSTERDAM FIR\","
			+"\"location_indicator_icao\":\"EHAA\","
			+"\"location_indicator_mwo\":\"EHDB\"}";
	
	@Test
	public void contextLoads() throws Exception {
		assertThat(airmetObjectMapper,notNullValue());
	}
	
	public Airmet createAirmet (String s) throws Exception {
		Airmet sm=new Airmet("AMSTERDAM FIR", "EHAA", "EHDB", "abcd");
		sm.setPhenomenon(Phenomenon.getPhenomenon("ISOL_TCU"));
		sm.setValiddate(OffsetDateTime.now(ZoneId.of("Z")).minusHours(1));
		sm.setValiddate_end(OffsetDateTime.now(ZoneId.of("Z")).plusHours(3));
		sm.setChange(SigmetAirmetChange.NC);
		sm.setMovement_type(Airmet.AirmetMovementType.STATIONARY);
		sm.setLevelinfo(new SigmetAirmetLevel(new SigmetAirmetLevel.SigmetAirmetPart(SigmetAirmetLevel.SigmetAirmetLevelUnit.FL, 300),
				SigmetAirmetLevel.SigmetAirmetLevelMode.TOPS_ABV));
		setGeoFromString(sm, s);
		return sm;
	}
	
	public void setGeoFromString(Airmet am, String json) {
		Debug.println("setGeoFromString "+json);
		GeoJsonObject geo;	
		try {
			geo = airmetObjectMapper.readValue(json, GeoJsonObject.class);
			am.setGeojson(geo);
			Debug.println("setGeoFromString ["+json+"] set");
			return;
		} catch (JsonParseException e) {
		} catch (JsonMappingException e) {
		} catch (IOException e) {
		}
		Debug.errprintln("setGeoFromString on ["+json+"] failed");
		am.setGeojson(null);
	}
	
	public void validateAirmet (Airmet am) throws Exception {
		Debug.println("Testing createAndCheckAirmet");
		Debug.println(am.getValiddate().toString());
		assertThat(am.getPhenomenon().toString(), is("ISOL_TCU"));
	}
	
	@Test 
	public void createAndValidateAirmet () throws Exception {
		Airmet am = createAirmet(testGeoJson1);
		validateAirmet(am);
	}

	@Autowired
	AirmetStore testAirmetStore;
	
	public AirmetStore createNewStore () throws IOException {
		Tools.rmdir(airmetStoreLocation);
		Tools.mksubdirs(airmetStoreLocation);
		testAirmetStore.setLocation(airmetStoreLocation);
		Airmet[] airmets=testAirmetStore.getAirmets(false, SigmetAirmetStatus.concept);
		assertThat(airmets.length, is(0));
		return testAirmetStore;
	}
	
	@Test
	public void saveOneAirmet () throws Exception {
		AirmetStore store=createNewStore();
		Airmet sm = createAirmet(testGeoJson1);
		assertThat(store.getOM(),notNullValue());
		
		store.storeAirmet(sm);
		assertThat(store.getAirmets(false, SigmetAirmetStatus.concept).length, is(1));
	}
	
	@Test
	public void loadAndValidateAirmet () throws Exception {
		AirmetStore store=createNewStore();
		Airmet sm = createAirmet(testGeoJson5);
		assertThat(store.getOM(),notNullValue());
		store.storeAirmet(sm);
		
		Airmet[] airmets=store.getAirmets(false, SigmetAirmetStatus.concept);
		assertThat(airmets.length, is(1));
		validateAirmet(airmets[0]);
		Debug.println("AIRMET: "+airmets[0].toString());
		FIRStore firStore=new FIRStore("/tmp/FIRSTORE");
		Debug.println("  TAC:"+airmets[0].toTAC(firStore.lookup("EHAA", true)));
	}
	
}
