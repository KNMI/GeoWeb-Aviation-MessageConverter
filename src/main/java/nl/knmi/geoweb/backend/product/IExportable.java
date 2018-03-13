package nl.knmi.geoweb.backend.product;

import java.io.File;

import nl.knmi.geoweb.backend.product.taf.converter.TafConverter;

public interface IExportable {
	public void export (File path, TafConverter converter);
}
