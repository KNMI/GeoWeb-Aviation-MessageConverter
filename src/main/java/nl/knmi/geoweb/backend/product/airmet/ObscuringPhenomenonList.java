package nl.knmi.geoweb.backend.product.airmet;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

public class ObscuringPhenomenonList {
    private List<ObscuringPhenomenon> obscuringPhenomena;

    @Getter
    public class ObscuringPhenomenon {

        private String name;
        private String code;

        public ObscuringPhenomenon(String name, String code) {
            this.name = name;
            this.code = code;
        }
    }

    public ObscuringPhenomenonList() {
        this.obscuringPhenomena = new ArrayList<>();
        this.obscuringPhenomena.add(new ObscuringPhenomenon("Drizzle", "DZ"));
        this.obscuringPhenomena.add(new ObscuringPhenomenon("Dust", "DU"));
        this.obscuringPhenomena.add(new ObscuringPhenomenon("Dust/sand whirls", "DP"));
        this.obscuringPhenomena.add(new ObscuringPhenomenon("Duststorm", "DS"));
        this.obscuringPhenomena.add(new ObscuringPhenomenon("Fog", "FG"));
        this.obscuringPhenomena.add(new ObscuringPhenomenon("Funnel cloud", "FC"));
        this.obscuringPhenomena.add(new ObscuringPhenomenon("Hail", "GR"));
        this.obscuringPhenomena.add(new ObscuringPhenomenon("Haze", "HZ"));
        this.obscuringPhenomena.add(new ObscuringPhenomenon("Ice pellets", "PL"));
        this.obscuringPhenomena.add(new ObscuringPhenomenon("Mist", "BR"));
    }

    public List<ObscuringPhenomenon> getObscuringPhenomena() {
        return obscuringPhenomena;
    }
}
