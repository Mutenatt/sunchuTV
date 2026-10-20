package tv.sunchu.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Clima desde Open-Meteo (sin API key) para las coordenadas configuradas. */
final class Weather {

    static final class Hour {
        String time;
        double temp;
        int code;
        boolean day;
    }

    double temp;
    double feels;
    int code;
    boolean day;
    double max;
    double min;
    String sunrise;
    String sunset;
    final List<Hour> next = new ArrayList<>();

    /** Último dato obtenido; lo usa también el protector de pantalla. */
    static volatile Weather last;

    static Weather fetch() throws Exception {
        String url = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + BuildConfig.WEATHER_LAT
                + "&longitude=" + BuildConfig.WEATHER_LON
                + "&current=temperature_2m,apparent_temperature,weather_code,is_day"
                + "&hourly=temperature_2m,weather_code"
                + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset"
                + "&timezone=America%2FArgentina%2FCordoba&forecast_days=2";
        JSONObject o = new JSONObject(Net.get(url, null));

        Weather w = new Weather();
        JSONObject cur = o.getJSONObject("current");
        w.temp = cur.getDouble("temperature_2m");
        w.feels = cur.optDouble("apparent_temperature", w.temp);
        w.code = cur.optInt("weather_code", 0);
        w.day = cur.optInt("is_day", 1) == 1;
        String now = cur.getString("time"); // "2026-10-20T03:45"

        JSONObject daily = o.getJSONObject("daily");
        w.max = daily.getJSONArray("temperature_2m_max").getDouble(0);
        w.min = daily.getJSONArray("temperature_2m_min").getDouble(0);
        w.sunrise = hhmm(daily.getJSONArray("sunrise").getString(0));
        w.sunset = hhmm(daily.getJSONArray("sunset").getString(0));

        JSONObject hourly = o.getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time");
        JSONArray temps = hourly.getJSONArray("temperature_2m");
        JSONArray codes = hourly.getJSONArray("weather_code");
        String hourKey = now.substring(0, 13); // "2026-10-20T03"
        int start = 0;
        for (int i = 0; i < times.length(); i++) {
            if (times.getString(i).compareTo(hourKey) >= 0) {
                start = i;
                break;
            }
        }
        for (int i = start + 1; i < Math.min(start + 7, times.length()); i++) {
            Hour h = new Hour();
            h.time = hhmm(times.getString(i));
            h.temp = temps.getDouble(i);
            h.code = codes.getInt(i);
            h.day = h.time.compareTo(w.sunrise) >= 0 && h.time.compareTo(w.sunset) < 0;
            w.next.add(h);
        }
        return w;
    }

    private static String hhmm(String iso) {
        return iso.length() >= 16 ? iso.substring(11, 16) : iso;
    }

    static String round(double t) {
        return Math.round(t) + "°";
    }

    /** Códigos WMO que usa Open-Meteo. */
    static String describe(int code) {
        if (code == 0) return "Despejado";
        if (code == 1) return "Mayormente despejado";
        if (code == 2) return "Parcialmente nublado";
        if (code == 3) return "Nublado";
        if (code == 45 || code == 48) return "Niebla";
        if (code >= 51 && code <= 57) return "Llovizna";
        if (code >= 61 && code <= 67) return "Lluvia";
        if (code >= 71 && code <= 77) return "Nieve";
        if (code >= 80 && code <= 82) return "Chaparrones";
        if (code >= 95) return "Tormenta";
        return "Sin datos";
    }

    static String emoji(int code, boolean day) {
        if (code == 0) return day ? "☀️" : "🌙";
        if (code == 1 || code == 2) return day ? "⛅" : "☁️";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "🌫️";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "🌧️";
        if (code >= 71 && code <= 77) return "❄️";
        if (code >= 95) return "⛈️";
        return "🌡️";
    }
}
