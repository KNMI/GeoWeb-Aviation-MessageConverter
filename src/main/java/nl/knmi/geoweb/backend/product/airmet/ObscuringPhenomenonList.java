package nl.knmi.geoweb.backend.product.airmet;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

public class ObscuringPhenomenonList {
 //   private List<ObscuringPhenomenon> obscuringPhenomena;

    private static List<ObscuringPhenomenon> allPhenomena;

    @Getter
    public static class ObscuringPhenomenon {

        private String name;
        private String code;

        public ObscuringPhenomenon(String name, String code) {
            this.name = name;
            this.code = code;
        }
        public ObscuringPhenomenon(){}
    }

    public ObscuringPhenomenonList() {
//        this.obscuringPhenomena = new ArrayList<>();
    }

    public static List<ObscuringPhenomenon> getAllObscuringPhenomena() {
        if (allPhenomena==null) {
            allPhenomena = new ArrayList<>();
            allPhenomena.add(new ObscuringPhenomenon("Drizzle", "DZ"));
            allPhenomena.add(new ObscuringPhenomenon("Dust", "DU"));
            allPhenomena.add(new ObscuringPhenomenon("Dust/sand whirls", "DP"));
            allPhenomena.add(new ObscuringPhenomenon("Duststorm", "DS"));
            allPhenomena.add(new ObscuringPhenomenon("Fog", "FG"));
            allPhenomena.add(new ObscuringPhenomenon("Funnel cloud", "FC"));
            allPhenomena.add(new ObscuringPhenomenon("Hail", "GR"));
            allPhenomena.add(new ObscuringPhenomenon("Haze", "HZ"));
            allPhenomena.add(new ObscuringPhenomenon("Ice pellets", "PL"));
            allPhenomena.add(new ObscuringPhenomenon("Mist", "BR"));
        }
        return allPhenomena;
    }

    public static ObscuringPhenomenon of(String s) {
        for (ObscuringPhenomenon ph: getAllObscuringPhenomena()){
            if (s.equals(ph.code)) return ph;
        }
        return null;
    }


}
