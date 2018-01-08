package nl.knmi.geoweb.backend.product.taf;

import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.Getter;
import lombok.Setter;

// Return the validation result and (if present) the human readable errors
// if succeeded is true, the value of errors is undefined
// if succeeded is false, errors is an object where the key is the pointer in the (enriched) json and the value is an array of errors (strings)
public class TafValidationResult {
	@Getter
	@Setter
	private boolean succeeded = false;
	
	@Getter
	@Setter
	private ObjectNode errors = null;
	
	public TafValidationResult(boolean succeeded, ObjectNode errors) {
		this.succeeded = succeeded;
		this.errors = errors;
	}
}