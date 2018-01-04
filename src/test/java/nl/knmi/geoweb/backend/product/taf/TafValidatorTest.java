package nl.knmi.geoweb.backend.product.taf;

import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.core.Is.is;

import java.io.IOException;
import java.text.ParseException;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;

import nl.knmi.adaguc.tools.Tools;
import nl.knmi.geoweb.backend.product.taf.TafValidator.ValidationResult;

@RunWith(SpringRunner.class)
@SpringBootTest(classes={TafValidatorTestContext.class})
public class TafValidatorTest {
	
	@Value(value = "${productstorelocation}")
	String productstorelocation;
	
	@Test
	public void testValidateOK () throws Exception {
		TafSchemaStore tafSchemaStore =  new TafSchemaStore(productstorelocation);
		TafValidator tafValidator = new TafValidator(tafSchemaStore);

		String taf = Tools.readResource( "Taf_valid.json");
		
		JSONObject tafAsJSON = new JSONObject(taf);
		ValidationResult report = tafValidator.validate(tafAsJSON.toString());
		assertThat(report.isSucceeded(), is(true));
	}
	
	@Test
	public void testValidateFails () throws IOException, JSONException, ProcessingException, ParseException  {
		TafSchemaStore tafSchemaStore =  new TafSchemaStore(productstorelocation);
		TafValidator tafValidator = new TafValidator(tafSchemaStore);

		String taf = Tools.readResource( "./Taf_invalid.json");
		JSONObject tafAsJSON = new JSONObject(taf);
		ValidationResult report = tafValidator.validate(tafAsJSON.toString());
		assertThat(report.isSucceeded(), is(false));
	}
	
}
