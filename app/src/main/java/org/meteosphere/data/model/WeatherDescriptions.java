package org.meteosphere.data.model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.meteosphere.settings.AppSettings;

// Descriptions of the WMO weather codes, in the languages of the app: the codes Open-Meteo gives and the ones
// ForecaMapper.getWmo() and ForecaHelper of the wpr server turn the Foreca symbols into.
public final class WeatherDescriptions {

    public static final List<String> LANGUAGES = Arrays.asList("ru", "en", "uk", "pl", "de", "fr", "es", "it", "pt", "tr", "zh", "ja", "nl", "zh-Hant");
    private static final int ENGLISH = 1;

    private static final Map<Integer, String[]> DESCRIPTIONS = new HashMap<>();

    private static void add(int wmo, String... descriptions) {
        DESCRIPTIONS.put(wmo, descriptions);
    }

    static {
        // WMO code, then ru, en, uk, pl, de, fr, es, it, pt, tr, zh, ja, nl, zh-Hant
        add(0, "Ясно", "Clear", "Ясно", "Bezchmurnie", "Klar", "Ciel dégagé", "Despejado", "Sereno", "Céu limpo", "Açık", "晴", "晴れ", "Onbewolkt", "晴"); // Clear sky
        add(1, "Малооблачно", "Mostly clear", "Малохмарно", "Małe zachmurzenie", "Überwiegend klar", "Plutôt dégagé", "Mayormente despejado", "Prevalentemente sereno", "Pouco nublado", "Çoğunlukla açık", "晴间少云", "おおむね晴れ", "Licht bewolkt", "晴時多雲"); // Mainly clear
        add(2, "Облачно", "Partly cloudy", "Хмарно", "Umiarkowane zachmurzenie", "Teilweise bewölkt", "Partiellement nuageux", "Parcialmente nublado", "Parzialmente nuvoloso", "Parcialmente nublado", "Parçalı bulutlu", "多云", "晴れ時々くもり", "Half bewolkt", "多雲"); // Partly cloudy
        add(3, "Пасмурно", "Overcast", "Похмуро", "Pochmurno", "Bedeckt", "Couvert", "Cubierto", "Coperto", "Encoberto", "Kapalı", "阴", "くもり", "Zwaar bewolkt", "陰"); // Overcast

        add(45, "Туман", "Fog", "Туман", "Mgła", "Nebel", "Brouillard", "Niebla", "Nebbia", "Neblina", "Sis", "雾", "霧", "Mist", "霧"); // Fog
        add(48, "Туман, изморозь", "Freezing fog", "Туман, паморозь", "Mgła, szadź", "Gefrierender Nebel", "Brouillard givrant", "Niebla helada", "Nebbia gelata", "Neblina congelante", "Dondurucu sis", "冻雾", "着氷性の霧", "Aanvriezende mist", "凍霧"); // Depositing rime fog

        add(51, "Небольшая морось", "Light drizzle", "Невелика мряка", "Słaba mżawka", "Leichter Nieselregen", "Bruine faible", "Llovizna ligera", "Pioviggine debole", "Garoa fraca", "Hafif çisenti", "小毛毛雨", "弱い霧雨", "Lichte motregen", "小毛毛雨"); // Drizzle: Light intensity
        add(53, "Морось", "Drizzle", "Мряка", "Mżawka", "Nieselregen", "Bruine", "Llovizna", "Pioviggine", "Garoa", "Çisenti", "毛毛雨", "霧雨", "Motregen", "毛毛雨"); // Drizzle: Moderate intensity
        add(55, "Сильная морось", "Heavy drizzle", "Сильна мряка", "Silna mżawka", "Starker Nieselregen", "Bruine forte", "Llovizna intensa", "Pioviggine intensa", "Garoa forte", "Yoğun çisenti", "大毛毛雨", "強い霧雨", "Dichte motregen", "大毛毛雨"); // Drizzle: Dense intensity

        add(56, "Небольшая морось, гололёд", "Light freezing drizzle", "Невелика мряка, ожеледиця", "Słaba marznąca mżawka", "Leichter gefrierender Nieselregen", "Bruine verglaçante faible", "Llovizna helada ligera", "Pioviggine gelata debole", "Garoa congelante fraca", "Hafif dondurucu çisenti", "小冻毛毛雨", "弱い着氷性の霧雨", "Lichte aanvriezende motregen", "小凍毛毛雨"); // Freezing Drizzle: Light intensity
        add(57, "Сильная морось, гололёд", "Heavy freezing drizzle", "Сильна мряка, ожеледиця", "Silna marznąca mżawka", "Starker gefrierender Nieselregen", "Bruine verglaçante forte", "Llovizna helada intensa", "Pioviggine gelata intensa", "Garoa congelante forte", "Yoğun dondurucu çisenti", "大冻毛毛雨", "強い着氷性の霧雨", "Dichte aanvriezende motregen", "大凍毛毛雨"); // Freezing Drizzle: Dense intensity

        add(61, "Небольшой дождь", "Light rain", "Невеликий дощ", "Słaby deszcz", "Leichter Regen", "Pluie faible", "Lluvia ligera", "Pioggia debole", "Chuva fraca", "Hafif yağmur", "小雨", "小雨", "Lichte regen", "小雨"); // Rain: Slight intensity
        add(63, "Дождь", "Rain", "Дощ", "Deszcz", "Regen", "Pluie", "Lluvia", "Pioggia", "Chuva", "Yağmur", "中雨", "雨", "Regen", "中雨"); // Rain: Moderate intensity
        add(65, "Сильный дождь", "Heavy rain", "Сильний дощ", "Silny deszcz", "Starker Regen", "Forte pluie", "Lluvia intensa", "Pioggia forte", "Chuva forte", "Şiddetli yağmur", "大雨", "強い雨", "Zware regen", "大雨"); // Rain: Heavy intensity

        add(66, "Небольшой дождь, гололёд", "Light freezing rain", "Невеликий дощ, ожеледиця", "Słaby marznący deszcz", "Leichter gefrierender Regen", "Pluie verglaçante faible", "Lluvia helada ligera", "Pioggia gelata debole", "Chuva congelante fraca", "Hafif dondurucu yağmur", "小冻雨", "弱い着氷性の雨", "Lichte ijzel", "小凍雨"); // Freezing Rain: Light intensity
        add(67, "Сильный дождь, гололёд", "Heavy freezing rain", "Сильний дощ, ожеледиця", "Silny marznący deszcz", "Starker gefrierender Regen", "Pluie verglaçante forte", "Lluvia helada intensa", "Pioggia gelata forte", "Chuva congelante forte", "Şiddetli dondurucu yağmur", "大冻雨", "強い着氷性の雨", "Zware ijzel", "大凍雨"); // Freezing Rain: Heavy intensity

        // Not given by Open-Meteo, ForecaMapper.getWmo() gives them for sleet
        add(68, "Небольшой дождь со снегом", "Light rain and snow", "Невеликий дощ зі снігом", "Słaby deszcz ze śniegiem", "Leichter Schneeregen", "Pluie et neige mêlées faibles", "Aguanieve ligera", "Pioggia mista a neve debole", "Chuva e neve fracas", "Hafif karla karışık yağmur", "小雨夹雪", "弱いみぞれ", "Lichte natte sneeuw", "小雨夾雪"); // Rain and snow: Slight
        add(69, "Дождь со снегом", "Rain and snow", "Дощ зі снігом", "Deszcz ze śniegiem", "Schneeregen", "Pluie et neige mêlées", "Aguanieve", "Pioggia mista a neve", "Chuva e neve", "Karla karışık yağmur", "雨夹雪", "みぞれ", "Natte sneeuw", "雨夾雪"); // Rain and snow: Moderate or heavy

        add(71, "Небольшой снег", "Light snow", "Невеликий сніг", "Słaby śnieg", "Leichter Schneefall", "Neige faible", "Nevada ligera", "Neve debole", "Neve fraca", "Hafif kar", "小雪", "小雪", "Lichte sneeuw", "小雪"); // Snow fall: Slight intensity
        add(73, "Снег", "Snow", "Сніг", "Śnieg", "Schneefall", "Neige", "Nieve", "Neve", "Neve", "Kar", "中雪", "雪", "Sneeuw", "中雪"); // Snow fall: Moderate intensity
        add(75, "Снегопад", "Heavy snow", "Снігопад", "Silny śnieg", "Starker Schneefall", "Fortes chutes de neige", "Nevada intensa", "Neve forte", "Neve forte", "Yoğun kar", "大雪", "大雪", "Zware sneeuw", "大雪"); // Snow fall: Heavy intensity

        add(77, "Снежная крупа", "Snow grains", "Снігова крупа", "Śnieg ziarnisty", "Schneegriesel", "Neige en grains", "Cinarra", "Neve granulosa", "Grãos de neve", "Kar taneleri", "米雪", "霧雪", "Motsneeuw", "米雪"); // Snow grains

        add(80, "Временами дождь", "Light rain showers", "Часом дощ", "Słaby przelotny deszcz", "Leichte Regenschauer", "Averses faibles", "Chubascos ligeros", "Rovesci deboli", "Pancadas de chuva fracas", "Hafif sağanak", "小阵雨", "弱いにわか雨", "Lichte regenbuien", "小陣雨"); // Rain showers: Slight intensity
        add(81, "Кратковременный дождь", "Rain showers", "Короткочасний дощ", "Przelotny deszcz", "Regenschauer", "Averses", "Chubascos", "Rovesci", "Pancadas de chuva", "Sağanak", "阵雨", "にわか雨", "Regenbuien", "陣雨"); // Rain showers: Moderate intensity
        add(82, "Сильный ливень", "Heavy rain showers", "Сильна злива", "Ulewny deszcz", "Starke Regenschauer", "Fortes averses", "Chubascos fuertes", "Forti rovesci", "Pancadas de chuva fortes", "Şiddetli sağanak", "强阵雨", "強いにわか雨", "Zware regenbuien", "強陣雨"); // Rain showers: Heavy intensity

        add(83, "Временами дождь со снегом", "Light rain and snow showers", "Часом дощ зі снігом", "Słaby przelotny deszcz ze śniegiem", "Leichte Schneeregenschauer", "Averses de pluie et neige faibles", "Chubascos ligeros de aguanieve", "Deboli rovesci di pioggia mista a neve", "Pancadas fracas de chuva e neve", "Hafif karla karışık sağanak", "小阵雨夹雪", "弱いにわかみぞれ", "Lichte buien met natte sneeuw", "小陣雨夾雪"); // Rain and snow showers: Slight, not given by Open-Meteo

        add(85, "Временами снег", "Light snow showers", "Часом сніг", "Słaby przelotny śnieg", "Leichte Schneeschauer", "Averses de neige faibles", "Chubascos de nieve ligeros", "Deboli rovesci di neve", "Pancadas de neve fracas", "Hafif kar sağanağı", "小阵雪", "弱いにわか雪", "Lichte sneeuwbuien", "小陣雪"); // Snow showers: Light intensity
        add(86, "Сильный снегопад", "Heavy snow showers", "Сильний снігопад", "Silny przelotny śnieg", "Starke Schneeschauer", "Fortes averses de neige", "Chubascos de nieve fuertes", "Forti rovesci di neve", "Pancadas de neve fortes", "Yoğun kar sağanağı", "强阵雪", "強いにわか雪", "Zware sneeuwbuien", "強陣雪"); // Snow showers: Heavy intensity

        add(95, "Гроза", "Thunderstorm", "Гроза", "Burza", "Gewitter", "Orage", "Tormenta", "Temporale", "Trovoada", "Gök gürültülü sağanak", "雷阵雨", "雷雨", "Onweer", "雷陣雨"); // Thunderstorm: Slight or moderate
        add(96, "Гроза с градом", "Thunderstorm with hail", "Гроза з градом", "Burza z gradem", "Gewitter mit Hagel", "Orage avec grêle", "Tormenta con granizo", "Temporale con grandine", "Trovoada com granizo", "Gök gürültülü sağanak ve dolu", "雷阵雨伴有冰雹", "ひょうを伴う雷雨", "Onweer met hagel", "雷陣雨伴有冰雹"); // Thunderstorm with slight hail
        add(99, "Гроза с сильным градом", "Thunderstorm with heavy hail", "Гроза з сильним градом", "Burza z silnym gradem", "Gewitter mit starkem Hagel", "Orage avec forte grêle", "Tormenta con granizo fuerte", "Temporale con forte grandine", "Trovoada com granizo forte", "Gök gürültülü sağanak ve yoğun dolu", "雷阵雨伴有强冰雹", "強いひょうを伴う雷雨", "Onweer met zware hagel", "雷陣雨伴有強冰雹"); // Thunderstorm with heavy hail
    }

    private WeatherDescriptions() {
    }

    // In the language of the app; empty for unknown codes and -1, the code of none
    public static String get(int wmo) {
        AppSettings settings = AppSettings.getInstance();
        return get(wmo, settings != null && settings.language != null ? settings.language : Locale.getDefault().getLanguage());
    }

    // Empty for unknown codes, English for other languages
    public static String get(int wmo, String lang) {
        String[] descriptions = DESCRIPTIONS.get(wmo);
        if (descriptions == null) {
            return "";
        }
        int index = LANGUAGES.indexOf(lang);
        return descriptions[index >= 0 ? index : ENGLISH];
    }
}
