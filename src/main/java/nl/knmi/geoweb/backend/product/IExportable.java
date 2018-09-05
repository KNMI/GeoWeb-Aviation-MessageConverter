package nl.knmi.geoweb.backend.product;

import java.io.File;

import com.fasterxml.jackson.databind.ObjectMapper;

import nl.knmi.geoweb.backend.product.taf.converter.TafConverter;

public interface IExportable<GeoWebProduct> {
	public String export (File path, ProductConverter<GeoWebProduct> converter, ObjectMapper om);
}
