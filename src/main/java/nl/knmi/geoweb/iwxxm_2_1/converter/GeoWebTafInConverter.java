package nl.knmi.geoweb.iwxxm_2_1.converter;

import fi.fmi.avi.converter.ConversionHints;
import fi.fmi.avi.converter.ConversionResult;
import fi.fmi.avi.model.AviationCodeListUser;
import fi.fmi.avi.model.CloudForecast;
import fi.fmi.avi.model.CloudLayer;
import fi.fmi.avi.model.taf.TAF;
import fi.fmi.avi.model.taf.TAFBaseForecast;
import fi.fmi.avi.model.taf.TAFChangeForecast;
import fi.fmi.avi.model.taf.TAFSurfaceWind;
import nl.knmi.geoweb.backend.product.taf.Taf;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class GeoWebTafInConverter extends AbstractGeoWebTafInConverter<TAF> {
    @Override
    public ConversionResult<Taf> convertMessage(TAF input, ConversionHints hints) {
        ConversionResult<Taf> retval = new ConversionResult<>();
        Taf taf = new Taf();
        Taf.Metadata metadata = new Taf.Metadata();
        metadata.setUuid(UUID.randomUUID().toString());
        metadata.setIssueTime(OffsetDateTime.ofInstant(input.getIssueTime().getCompleteTime().get().toInstant(), ZoneId.of("Z")));
        metadata.setValidityStart(OffsetDateTime.ofInstant(input.getValidityTime().get().getStartTime().get().getCompleteTime().get().toInstant(), ZoneId.of("Z")));
        metadata.setValidityEnd(OffsetDateTime.ofInstant(input.getValidityTime().get().getEndTime().get().getCompleteTime().get().toInstant(), ZoneId.of("Z")));
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

        Taf.Forecast forecast = new Taf.Forecast();
        if (input.getBaseForecast().isPresent()) {
            forecast.setCaVOK(input.getBaseForecast().get().isCeilingAndVisibilityOk());
            updateForecastWind(forecast, input.getBaseForecast().get(), retval);
            updateForecastTemperature(forecast, input.getBaseForecast().get(), retval);
            updateForecastCloud(forecast, input.getBaseForecast().get(), retval);
            updateForecastWeather(forecast, input.getBaseForecast().get(), retval);
        }
        taf.setForecast(forecast);

        List<Taf.ChangeForecast> changeForecasts = new ArrayList<>();
        for (TAFChangeForecast changeForecast : input.getChangeForecasts().get()) {
            Taf.ChangeForecast ch = new Taf.ChangeForecast();
            Taf.Forecast chFc = new Taf.Forecast();
            switch (changeForecast.getChangeIndicator()) {
                case FROM:
                    ch.setChangeType("FM");
                    break;
                case BECOMING:
                    ch.setChangeType("BECMG");
                    break;
                default:
            }
            chFc.setCaVOK(changeForecast.isCeilingAndVisibilityOk());
            Taf.Forecast.TAFWind chwind = new Taf.Forecast.TAFWind();
            TAFSurfaceWind chInWind = input.getBaseForecast().get().getSurfaceWind().get();
            chwind.setSpeed(chInWind.getMeanWindSpeed().getValue().intValue());
            if (chInWind.getMeanWindSpeed().getUom().equals("[kn_i]")) {
                chwind.setUnit("KT");
            } else if (chInWind.getMeanWindSpeed().getUom().equals("m/s")) {
                chwind.setUnit("MPS");
            } else {
                chwind.setUnit("KT");
            }
            chwind.setDirection(chInWind.isVariableDirection() ? "VRB" : chInWind.getMeanWindDirection().get().getValue());
            if (chInWind.getWindGust().isPresent()) {
                chwind.setGusts(chInWind.getWindGust().get().getValue().intValue());
            }
            chFc.setWind(chwind);
            changeForecasts.add(ch);

        }
        taf.setChangegroups(changeForecasts);

        retval.setStatus(ConversionResult.Status.SUCCESS);
        retval.setConvertedMessage(taf);
        return retval;
    }

    private String getUomFromUnit(String unit) {
        if (unit.equals("[kn_i]")) {
            return "KT";
        } else if (unit.equals("m/s")) {
            return "MPS";
        } else {
            return "KT";
        }
    }

    private void updateForecastWind(Taf.Forecast fc, TAFBaseForecast tafBaseForecast, ConversionResult<Taf> result) {
        Taf.Forecast.TAFWind wind = new Taf.Forecast.TAFWind();
        TAFSurfaceWind inWind = tafBaseForecast.getSurfaceWind().get();
        wind.setSpeed(inWind.getMeanWindSpeed().getValue().intValue());
        wind.setUnit(getUomFromUnit(inWind.getMeanWindSpeed().getUom()));
        wind.setDirection(inWind.isVariableDirection() ? "VRB" : inWind.getMeanWindDirection().get().getValue());
        if (inWind.getWindGust().isPresent()) {
            wind.setGusts(inWind.getWindGust().get().getValue().intValue());
        }
        fc.setWind(wind);


    }

    private void updateForecastCloud(Taf.Forecast fc, TAFBaseForecast tafBaseForecast, ConversionResult<Taf> result) {
        if (tafBaseForecast.getCloud().isPresent()) {
            List<Taf.Forecast.TAFCloudType> cloudTypes = new ArrayList<>();

            CloudForecast cf = tafBaseForecast.getCloud().get();
            if (cf.isNoSignificantCloud()) {
              Taf.Forecast.TAFCloudType ct = new Taf.Forecast.TAFCloudType();
              ct.setIsNSC(true);

              fc.setClouds(Arrays.asList(ct));
            } else {
                if (cf.getVerticalVisibility().isPresent()) {
                    fc.setVertical_visibility(cf.getVerticalVisibility().get().getValue().intValue());
                } else {
                    List<Taf.Forecast.TAFCloudType> clouds=new ArrayList<>();
                    for (CloudLayer cloudLayer : cf.getLayers().get()) {
                        Taf.Forecast.TAFCloudType ct = new Taf.Forecast.TAFCloudType();
                        if (cloudLayer.getAmount().isPresent()) {
                            switch (cloudLayer.getAmount().get()) {
                                case FEW:
                                    ct.setAmount("FEW");
                                    break;
                                case BKN:
                                    ct.setAmount("BKN");
                                    break;
                                case SCT:
                                    ct.setAmount("SCT");
                                    break;
                                case OVC:
                                    ct.setAmount("OVC");
                                    break;
                                default:
                                    break;
                            }
                        }
                        if (cloudLayer.getBase().isPresent()) {
                            ct.setHeight(cloudLayer.getBase().get().getValue().intValue());
                        }
                        if (cloudLayer.getCloudType().isPresent()) {
                            ct.setMod(cloudLayer.getCloudType().get().toString());
                        }
                        clouds.add(ct);
                    }
                    fc.setClouds(clouds);
                }
            }
        }

        Taf.Forecast.TAFWind wind = new Taf.Forecast.TAFWind();
        TAFSurfaceWind inWind = tafBaseForecast.getSurfaceWind().get();
        wind.setSpeed(inWind.getMeanWindSpeed().getValue().intValue());
        wind.setUnit(getUomFromUnit(inWind.getMeanWindSpeed().getUom()));
        wind.setDirection(inWind.isVariableDirection() ? "VRB" : inWind.getMeanWindDirection().get().getValue());
        if (inWind.getWindGust().isPresent()) {
            wind.setGusts(inWind.getWindGust().get().getValue().intValue());
        }
        fc.setWind(wind);
    }

    private void updateForecastWeather(Taf.Forecast fc, TAFBaseForecast tafBaseForecast, ConversionResult<Taf> result) {
    }

    private void updateForecastTemperature(Taf.Forecast fc, TAFBaseForecast tafBaseForecast, ConversionResult<Taf> result) {
    }


}
