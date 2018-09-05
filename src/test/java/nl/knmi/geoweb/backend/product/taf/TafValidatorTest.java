package nl.knmi.geoweb.backend.product.taf;

import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.core.Is.is;

import java.io.IOException;
import java.text.ParseException;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.locationtech.jts.util.Debug;
import org.mozilla.javascript.debug.DebuggableObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;

import nl.knmi.adaguc.tools.Tools;
import nl.knmi.geoweb.backend.product.taf.TafValidationResult;

@RunWith(SpringRunner.class)
@SpringBootTest(classes={TafValidatorTestContext.class})
public class TafValidatorTest {

	@Value(value = "${productstorelocation}")
	String productstorelocation;

	// @Autowired
	// private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("tafObjectMapper")
	private ObjectMapper tafObjectMapper;
   
	@Test
	public void testValidateOK () throws Exception {
		TafSchemaStore tafSchemaStore =  new TafSchemaStore(productstorelocation);
		TafValidator tafValidator = new TafValidator(tafSchemaStore, tafObjectMapper);

		String taf = Tools.readResource( "Taf_valid.json");

		JSONObject tafAsJSON = new JSONObject(taf);
		TafValidationResult report = tafValidator.validate(tafAsJSON.toString());
		assertThat(report.isSucceeded(), is(true));
	}

	@Test
	public void testValidateFails () throws IOException, JSONException, ProcessingException, ParseException  {
		TafSchemaStore tafSchemaStore =  new TafSchemaStore(productstorelocation);
		TafValidator tafValidator = new TafValidator(tafSchemaStore, tafObjectMapper);

		String taf = Tools.readResource( "./Taf_invalid.json");
		JSONObject tafAsJSON = new JSONObject(taf);
		TafValidationResult report = tafValidator.validate(tafAsJSON.toString());
		assertThat(report.isSucceeded(), is(false));
	}

	/* Tests if prob30 change in wind difference is more than 5 knots */
	@Test
	public void testValidate_test_taf_change_in_wind_enough_difference () throws Exception {
		String tafString = "{\"forecast\":{\"caVOK\":true,\"wind\":{\"direction\":120,\"speed\":40,\"unit\":\"KT\"}},\"metadata\":{\"location\":\"EHAM\",\"validityStart\":\"2018-06-18T12:00:00Z\",\"validityEnd\":\"2018-06-19T18:00:00Z\"},\"changegroups\":[{\"changeStart\":\"2018-06-18T12:00:00Z\",\"changeEnd\":\"2018-06-18T14:00:00Z\",\"changeType\":\"PROB30\",\"forecast\":{\"caVOK\":true,\"wind\":{\"direction\":120,\"speed\":40,\"unit\":\"KT\"}}}]}";
		TafSchemaStore tafSchemaStore =  new TafSchemaStore(productstorelocation);
		TafValidator tafValidator = new TafValidator(tafSchemaStore, tafObjectMapper);
		TafValidationResult report = tafValidator.validate(tafString);
		System.out.println(report.getErrors());
		assertThat(report.getErrors().toString(), is("{\"/changegroups/0/forecast/wind/windEnoughDifference\":"+
				"[\"Change in wind must be at least 30 degrees or 5 knots\"]}"));
		assertThat(report.isSucceeded(), is(false));

	}
	
	/*Tests speedOperator and gustsOperator */
	@Test
	public void testValidate_test_taf_speedOperator_and_gustOperator () throws Exception {
		
		String tafString = "{\"forecast\":{\"caVOK\":true,\"wind\":{\"direction\":100,\"speed\":20,\"gusts\":31,\"gustsOperator\":\"above\",\"speedOperator\":\"above\",\"unit\":\"MPS\"}},\"metadata\":{\"location\":\"EHAM\",\"validityStart\":\"2018-06-18T12:00:00Z\",\"validityEnd\":\"2018-06-19T18:00:00Z\"},\"changegroups\":[]}";
		TafSchemaStore tafSchemaStore =  new TafSchemaStore(productstorelocation);
		TafValidator tafValidator = new TafValidator(tafSchemaStore, tafObjectMapper);
		TafValidationResult report = tafValidator.validate(tafString);
		assertThat(report.isSucceeded(), is(true));

	}
	
	/*Tests Changegroups: veranderingsgroep met gust (tov geen gusts) ten onrechte afgekeurd.  */
	@Test
	public void testValidate_test_taf_changeGroup_with_gust_should_validateOK() throws Exception {
		
		String tafString = "{\"forecast\":{\"caVOK\":true,\"wind\":{\"direction\":200,\"speed\":20,\"unit\":\"KT\"}},\"metadata\":{\"location\":\"EHAM\",\"validityStart\":\"2018-06-18T12:00:00Z\",\"validityEnd\":\"2018-06-19T18:00:00Z\"},\"changegroups\":[{\"changeStart\":\"2018-06-18T12:00:00Z\",\"changeEnd\":\"2018-06-18T15:00:00Z\",\"changeType\":\"BECMG\",\"forecast\":{\"wind\":{\"direction\":200,\"speed\":22,\"gusts\":36,\"unit\":\"KT\"}}}]}";
		TafSchemaStore tafSchemaStore =  new TafSchemaStore(productstorelocation);
		TafValidator tafValidator = new TafValidator(tafSchemaStore, tafObjectMapper);
		TafValidationResult report = tafValidator.validate(tafString);
		assertThat(report.isSucceeded(), is(true));

	}
	
	/*Tests validation should simply not crash on this one: One */
	@Test
	public void testValidate_test_taf_validation_should_not_crash_1() throws Exception {
		String tafString = "{\"forecast\":{\"caVOK\":true,\"wind\":{\"direction\":200,\"speed\":20,\"unit\":\"KT\"}},\"metadata\":{\"location\":\"EHAM\",\"validityStart\":\"2018-06-18T12:00:00Z\",\"validityEnd\":\"2018-06-19T18:00:00Z\"},\"changegroups\":[{\"changeStart\":\"2018-06-18T16:00:00Z\",\"changeEnd\":\"2018-06-18T20:00:00Z\",\"changeType\":\"BECMG\",\"forecast\":{\"wind\":{\"direction\":200,\"speed\":22,\"gusts\":30,\"unit\":\"KT\"}}}]}";
		TafSchemaStore tafSchemaStore =  new TafSchemaStore(productstorelocation);
		TafValidator tafValidator = new TafValidator(tafSchemaStore, tafObjectMapper);
		TafValidationResult report = tafValidator.validate(tafString);
		assertThat(report.isSucceeded(), is(false));

	}
	
	/*Tests validation should simply not crash on this one: Two */
	@Test
	public void testValidate_test_taf_validation_should_not_crash_2() throws Exception {
		String tafString = "{\"forecast\":{\"caVOK\":true,\"wind\":{\"direction\":200,\"speed\":20,\"unit\":\"KT\"}},\"metadata\":{\"location\":\"EHAM\",\"validityStart\":\"2018-06-18T12:00:00Z\",\"validityEnd\":\"2018-06-19T18:00:00Z\"},\"changegroups\":[{\"changeStart\":\"2018-06-18T16:00:00Z\",\"changeEnd\":\"2018-06-18T20:00:00Z\",\"changeType\":\"BECMG\",\"forecast\":{\"wind\":{\"direction\":200,\"speed\":22,\"gusts\":37,\"unit\":\"KT\"}}}]}";
		TafSchemaStore tafSchemaStore =  new TafSchemaStore(productstorelocation);
		TafValidator tafValidator = new TafValidator(tafSchemaStore, tafObjectMapper);
		TafValidationResult report = tafValidator.validate(tafString);
		assertThat(report.isSucceeded(), is(true));

	}
	
	/*Clouds NOT ascending in height should give valid pointer*/
	@Test
	public void testValidate_test_taf_clouds_not_ascending_in_height_should_give_valid_pointer() throws Exception {
		String tafString = "{\"forecast\":{\"clouds\":[{\"amount\":\"OVC\",\"height\":20,\"mod\":\"CB\"},{\"amount\":\"OVC\",\"height\":15,\"mod\":\"CB\"}],\"visibility\":{\"unit\":\"M\",\"value\":6000},\"weather\":[{\"qualifier\":\"moderate\",\"descriptor\":\"showers\",\"phenomena\":[\"rain\"]}],\"wind\":{\"direction\":200,\"speed\":20,\"unit\":\"KT\"}},\"metadata\":{\"location\":\"EHAM\",\"validityStart\":\"2018-06-18T12:00:00Z\",\"validityEnd\":\"2018-06-19T18:00:00Z\"},\"changegroups\":[{\"changeStart\":\"2018-06-18T14:00:00Z\",\"changeEnd\":\"2018-06-18T16:00:00Z\",\"changeType\":\"PROB30\",\"forecast\":{\"visibility\":{\"unit\":\"M\",\"value\":7000},\"wind\":{\"direction\":200,\"speed\":25,\"unit\":\"KT\"}}}]}";
		TafSchemaStore tafSchemaStore =  new TafSchemaStore(productstorelocation);
		TafValidator tafValidator = new TafValidator(tafSchemaStore, tafObjectMapper);
		TafValidationResult report = tafValidator.validate(tafString);
		System.out.println(report.getErrors().toString());
		assertThat(report.getErrors().toString(), is("{\"/forecast/clouds/1/cloudsHeightAscending\":[\"Cloud groups must be ascending in height\"]}"));
		assertThat(report.isSucceeded(), is(false));

	}
	
	/*Clouds ascending in height should validate*/
	@Test
	public void testValidate_test_taf_clouds_ascending_in_height_should_validate() throws Exception {
		String tafString = "{\"forecast\":{\"clouds\":[{\"amount\":\"OVC\",\"height\":20,\"mod\":\"CB\"},{\"amount\":\"OVC\",\"height\":25,\"mod\":\"CB\"}],\"visibility\":{\"unit\":\"M\",\"value\":6000},\"weather\":[{\"qualifier\":\"moderate\",\"descriptor\":\"showers\",\"phenomena\":[\"rain\"]}],\"wind\":{\"direction\":200,\"speed\":20,\"unit\":\"KT\"}},\"metadata\":{\"location\":\"EHAM\",\"validityStart\":\"2018-06-18T12:00:00Z\",\"validityEnd\":\"2018-06-19T18:00:00Z\"},\"changegroups\":[{\"changeStart\":\"2018-06-18T14:00:00Z\",\"changeEnd\":\"2018-06-18T16:00:00Z\",\"changeType\":\"PROB30\",\"forecast\":{\"visibility\":{\"unit\":\"M\",\"value\":7000},\"wind\":{\"direction\":200,\"speed\":25,\"unit\":\"KT\"}}}]}";
		TafSchemaStore tafSchemaStore =  new TafSchemaStore(productstorelocation);
		TafValidator tafValidator = new TafValidator(tafSchemaStore, tafObjectMapper);
		TafValidationResult report = tafValidator.validate(tafString);
		assertThat(report.isSucceeded(), is(true));
	}
	
	/* FZFG (Freezing Fog) alleen bij <= 1000 toestaan */
	@Test
	public void testValidate_test_taf_FZFG_only_below_1000m_visibility() throws Exception {
		TafSchemaStore tafSchemaStore =  new TafSchemaStore(productstorelocation);
		TafValidator tafValidator = new TafValidator(tafSchemaStore, tafObjectMapper);
		
		/* TESTING 1000 SHOULD VALIDATE */
		String tafString = "{\"forecast\":{\"clouds\":[{\"amount\":\"OVC\",\"height\":20,\"mod\":\"CB\"}],\"visibility\":{\"unit\":\"M\",\"value\":900},\"weather\":[{\"qualifier\":\"moderate\",\"descriptor\":\"freezing\",\"phenomena\":[\"fog\"]},{\"qualifier\":\"moderate\",\"descriptor\":\"showers\",\"phenomena\":[\"rain\"]}],\"wind\":{\"direction\":200,\"speed\":20,\"unit\":\"KT\"}},\"metadata\":{\"location\":\"EHAM\",\"validityStart\":\"2018-06-18T12:00:00Z\",\"validityEnd\":\"2018-06-19T18:00:00Z\"},\"changegroups\":[]}";
		TafValidationResult report = tafValidator.validate(tafString);
		assertThat(report.isSucceeded(), is(true));

		/* TESTING 2000 SHOULD NOT VALIDATE */
		tafString = "{\"forecast\":{\"clouds\":[{\"amount\":\"OVC\",\"height\":20,\"mod\":\"CB\"}],\"visibility\":{\"unit\":\"M\",\"value\":2000},\"weather\":[{\"qualifier\":\"moderate\",\"descriptor\":\"freezing\",\"phenomena\":[\"fog\"]},{\"qualifier\":\"moderate\",\"descriptor\":\"showers\",\"phenomena\":[\"rain\"]}],\"wind\":{\"direction\":200,\"speed\":20,\"unit\":\"KT\"}},\"metadata\":{\"location\":\"EHAM\",\"validityStart\":\"2018-06-18T12:00:00Z\",\"validityEnd\":\"2018-06-19T18:00:00Z\"},\"changegroups\":[]}";
		report = tafValidator.validate(tafString);
		assertThat(report.isSucceeded(), is(false));

	}
	
	/* Test metadata properties */
	@Test
	public void testValidate_test_metadataProperties() throws Exception {
		TafSchemaStore tafSchemaStore =  new TafSchemaStore(productstorelocation);
		TafValidator tafValidator = new TafValidator(tafSchemaStore, tafObjectMapper);
		
		/* TESTING 1000 SHOULD VALIDATE, lowercase concept */
		String tafString = "{\"forecast\":{\"caVOK\":true,\"wind\":{\"direction\":100,\"speed\":20,\"unit\":\"KT\"}},\"metadata\":{\"location\":\"EHTW\",\"status\":\"concept\",\"type\":\"normal\",\"validityStart\":\"2018-06-20T06:00:00Z\",\"validityEnd\":\"2018-06-21T12:00:00Z\"},\"changegroups\":[]}";
		TafValidationResult report = tafValidator.validate(tafString);
		assertThat(report.isSucceeded(), is(true));

		/* TESTING 2000 SHOULD NOT VALIDATE, uppercase CONCEPt */
		tafString = "{\"forecast\":{\"caVOK\":true,\"wind\":{\"direction\":100,\"speed\":20,\"unit\":\"KT\"}},\"metadata\":{\"location\":\"EHTW\",\"status\":\"CONCEPT\",\"type\":\"normal\",\"validityStart\":\"2018-06-20T06:00:00Z\",\"validityEnd\":\"2018-06-21T12:00:00Z\"},\"changegroups\":[]}";
		report = tafValidator.validate(tafString);
		assertThat(report.isSucceeded(), is(false));
	}
	
	/* Test MIFG Moderate Shallow Fog */
	@Test
	public void testValidate_test_taf_MIFG_moderate_shallow_fog() throws Exception {
		String tafString = "{\"forecast\":{\"clouds\":[{\"amount\":\"FEW\",\"height\":20}],\"visibility\":{\"unit\":\"M\",\"value\":6000},\"weather\":[{\"qualifier\":\"moderate\",\"descriptor\":\"shallow\",\"phenomena\":[\"fog\"]}],\"wind\":{\"direction\":120,\"speed\":40,\"unit\":\"KT\"}},\"metadata\":{\"location\":\"EHAM\",\"type\":\"normal\",\"validityStart\":\"2018-08-15T06:00:00Z\",\"validityEnd\":\"2018-08-16T12:00:00Z\"},\"changegroups\":[]}";
		TafSchemaStore tafSchemaStore =  new TafSchemaStore(productstorelocation);
		TafValidator tafValidator = new TafValidator(tafSchemaStore, tafObjectMapper);
		TafValidationResult report = tafValidator.validate(tafString);
		assertThat(report.isSucceeded(), is(true));
	}
	
	
	/* Tests if FG / FOG with 1100 meters gives proper feedback message */
	@Test
	public void testValidate_test_taf_FOG_1100_meters_proper_feedback () throws Exception {
		String tafString = "{\"forecast\":{\"clouds\":\"NSC\",\"visibility\":{\"unit\":\"M\",\"value\":1100},\"weather\":[{\"qualifier\":\"moderate\",\"phenomena\":[\"fog\"]}],\"wind\":{\"direction\":120,\"speed\":40,\"unit\":\"KT\"}},\"metadata\":{\"location\":\"EHAM\",\"type\":\"normal\",\"validityStart\":\"2018-08-15T12:00:00Z\",\"validityEnd\":\"2018-08-16T18:00:00Z\"},\"changegroups\":[]}";
		TafSchemaStore tafSchemaStore =  new TafSchemaStore(productstorelocation);
		TafValidator tafValidator = new TafValidator(tafSchemaStore, tafObjectMapper);
		TafValidationResult report = tafValidator.validate(tafString);
		System.out.println(report.getErrors().toString());
		assertThat(report.getErrors().toString(), is("{\"/forecast/visibilityAndFogWithoutDescriptorWithinLimit\":[\"Fog requires a visibility of less than 1000 meters\"]}"));
		assertThat(report.isSucceeded(), is(false));

	}
	

	/* Tests if heavy fog (+FG) gives proper feedback message */
	@Test
	public void testValidate_test_taf_FOG_heavy_proper_feedback () throws Exception {
		String tafString = "{\"forecast\":{\"clouds\":\"NSC\",\"visibility\":{\"unit\":\"M\",\"value\":1000},\"weather\":[{\"qualifier\":\"heavy\",\"phenomena\":[\"widespread dust\"]}],\"wind\":{\"direction\":120,\"speed\":40,\"unit\":\"KT\"}},\"metadata\":{\"location\":\"EHAM\",\"type\":\"normal\",\"validityStart\":\"2018-08-15T12:00:00Z\",\"validityEnd\":\"2018-08-16T18:00:00Z\"},\"changegroups\":[]}";
		TafSchemaStore tafSchemaStore =  new TafSchemaStore(productstorelocation);
		TafValidator tafValidator = new TafValidator(tafSchemaStore, tafObjectMapper);
		TafValidationResult report = tafValidator.validate(tafString);
		System.out.println(report.getErrors().toString());
		assertThat(report.getErrors().toString(), is("{\"/forecast/weather/0/qualifier\":[\"FG, BR, DU, HZ, SA, FU, VA, SQ, PO and TS can only be moderate\"]}"));
		assertThat(report.isSucceeded(), is(false));

	}
}
