package nl.knmi.geoweb.iwxxm_2_1.converter;

import fi.fmi.avi.converter.ConversionHints;
import fi.fmi.avi.converter.ConversionResult;
import fi.fmi.avi.model.taf.TAF;
import fi.fmi.avi.model.taf.TAFSurfaceWind;
import nl.knmi.geoweb.backend.product.taf.Taf;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

public class GeoWebTafInConverter extends AbstractGeoWebTafInConverter<TAF> {
    @Override
    public ConversionResult<Taf> convertMessage(TAF input, ConversionHints hints) {
        ConversionResult<Taf> retval = new ConversionResult<>();
        Taf taf=new Taf();
        Taf.Metadata metadata=new Taf.Metadata();
        metadata.setUuid(UUID.randomUUID().toString());
        metadata.setIssueTime(OffsetDateTime.ofInstant(input.getIssueTime().getCompleteTime().get().toInstant(), ZoneId.of("Z")));
        metadata.setValidityStart(OffsetDateTime.ofInstant(input.getValidityTime().get().getStartTime().get().getCompleteTime().get().toInstant(),ZoneId.of("Z")));
        metadata.setValidityEnd(OffsetDateTime.ofInstant(input.getValidityTime().get().getEndTime().get().getCompleteTime().get().toInstant(),ZoneId.of("Z")));
        metadata.setLocation(input.getAerodrome().getDesignator());
        metadata.setStatus(Taf.TAFReportPublishedConcept.inactive);

        switch (input.getStatus()) {
            case NORMAL:
                metadata.setType(Taf.TAFReportType.normal);
                break;
            case AMENDMENT:
                metadata.setType(Taf.TAFReportType.amendment);
                break;
            case CORRECTION:
                metadata.setType(Taf.TAFReportType.correction);
                break;
            case CANCELLATION:
                metadata.setType(Taf.TAFReportType.canceled);
                break;
            case MISSING:
                metadata.setType(Taf.TAFReportType.missing);
                break;
        }
        taf.setMetadata(metadata);

        Taf.Forecast forecast=new Taf.Forecast();
        forecast.setCaVOK(input.getBaseForecast().get().isCeilingAndVisibilityOk());
        Taf.Forecast.TAFWind wind=new Taf.Forecast.TAFWind();
        TAFSurfaceWind inWind=input.getBaseForecast().get().getSurfaceWind().get();
        wind.setSpeed(inWind.getMeanWindSpeed().getValue().intValue());
        wind.setDirection(inWind.isVariableDirection()?"VRB":inWind.getMeanWindDirection().get().getValue());
        if (inWind.getWindGust().isPresent()) {
            wind.setGusts(inWind.getWindGust().get().getValue().intValue());
        }
        forecast.setWind(wind);

        taf.setForecast(forecast);

        retval.setStatus(ConversionResult.Status.SUCCESS);
        retval.setConvertedMessage(taf);
        return null;
    }
}
